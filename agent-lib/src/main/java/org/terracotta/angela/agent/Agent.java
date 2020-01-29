/*
 * The contents of this file are subject to the Terracotta Public License Version
 * 2.0 (the "License"); You may not use this file except in compliance with the
 * License. You may obtain a copy of the License at
 *
 * http://terracotta.org/legal/terracotta-public-license.
 *
 * Software distributed under the License is distributed on an "AS IS" basis,
 * WITHOUT WARRANTY OF ANY KIND, either express or implied. See the License for
 * the specific language governing rights and limitations under the License.
 *
 * The Covered Software is Angela.
 *
 * The Initial Developer of the Covered Software is
 * Terracotta, Inc., a Software AG company
 */

package org.terracotta.angela.agent;

import org.apache.ignite.Ignite;
import org.apache.ignite.IgniteException;
import org.apache.ignite.Ignition;
import org.apache.ignite.configuration.BasicAddressResolver;
import org.apache.ignite.configuration.IgniteConfiguration;
import org.apache.ignite.logger.NullLogger;
import org.apache.ignite.logger.slf4j.Slf4jLogger;
import org.apache.ignite.spi.communication.tcp.TcpCommunicationSpi;
import org.apache.ignite.spi.discovery.tcp.TcpDiscoverySpi;
import org.apache.ignite.spi.discovery.tcp.ipfinder.vm.TcpDiscoveryVmIpFinder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.terracotta.angela.common.AngelaProperties;
import org.terracotta.angela.common.util.AngelaVersion;
import org.terracotta.angela.common.util.HostPort;
import org.terracotta.angela.common.util.IgniteCommonHelper;
import org.terracotta.angela.common.util.IpUtils;

import java.io.Closeable;
import java.net.UnknownHostException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.terracotta.angela.common.AngelaProperties.DIRECT_JOIN;
import static org.terracotta.angela.common.AngelaProperties.IGNITE_LOGGING;
import static org.terracotta.angela.common.AngelaProperties.NODE_NAME;
import static org.terracotta.angela.common.AngelaProperties.PORT_RANGE;
import static org.terracotta.angela.common.AngelaProperties.getEitherOf;
import static org.terracotta.angela.common.util.DirectoryUtils.createAndValidateDir;
import static org.terracotta.angela.common.util.HostAndIpValidator.isValidIPv6;

/**
 * @author Ludovic Orban
 */
public class Agent {
  public static final String AGENT_IS_READY_MARKER_LOG = "Agent is ready";
  private final static Logger logger;

  public static final Path ROOT_DIR;
  public static final Path WORK_DIR;
  public static final Path IGNITE_DIR;
  public static volatile AgentController controller;

  static {
    // the angela-agent jar may end up on the classpath, so its logback config cannot have the default filename
    // this must happen before any Logger instance gets created
    System.setProperty("logback.configurationFile", "angela-logback.xml");
    logger = LoggerFactory.getLogger(Agent.class);

    ROOT_DIR = Paths.get(getEitherOf(AngelaProperties.ROOT_DIR, AngelaProperties.KITS_DIR));
    if (!ROOT_DIR.isAbsolute()) {
      throw new IllegalArgumentException("Expected ROOT_DIR to be an absolute path, got: " + ROOT_DIR);
    }
    WORK_DIR = ROOT_DIR.resolve("work");
    IGNITE_DIR = ROOT_DIR.resolve("ignite");
  }

  public static void main(String[] args) {
    Node node = startNode();
    Runtime.getRuntime().addShutdownHook(new Thread(node::close));
  }

  static Node startNode() {
    String nodeName = NODE_NAME.getValue();
    String directJoin = DIRECT_JOIN.getValue();
    String portRange = PORT_RANGE.getValue();

    List<String> nodesToJoin = new ArrayList<>();
    if (directJoin != null) {
      for (String node : directJoin.split(",")) {
        if (!node.trim().isEmpty()) {
          nodesToJoin.add(node);
        }
      }
    }
    Node node = new Node(nodeName, nodesToJoin, Integer.parseInt(portRange));

    // Do not use logger here as the marker is being grep'ed at and we do not want to depend upon the logger config
    System.out.println(AGENT_IS_READY_MARKER_LOG);
    System.out.flush();

    // keep this debug log as TestAgent depends on it
    logger.debug("Agent started");

    return node;
  }

  public static class Node implements Closeable {

    private volatile Ignite ignite;

    public Node(String nodeName, List<String> nodesToJoin) {
      this(nodeName, nodesToJoin, Integer.parseInt(PORT_RANGE.getDefaultValue()));
    }

    public Node(String nodeName, List<String> nodesToJoin, int portRange) {
      try {
        init(nodeName, nodesToJoin, portRange);
      } catch (Exception e) {
        try {
          close();
        } catch (Exception subEx) {
          e.addSuppressed(subEx);
        }
        throw e;
      }
    }

    private void init(String nodeName, List<String> nodesToJoin, int portRange) {
      logger.info("Root directory is : " + ROOT_DIR);
      createAndValidateDir(ROOT_DIR);
      createAndValidateDir(WORK_DIR);
      createAndValidateDir(IGNITE_DIR);

      IgniteConfiguration cfg = new IgniteConfiguration();
      cfg.setIgniteHome(IGNITE_DIR.resolve(System.getProperty("user.name")).toString());
      Map<String, String> userAttributes = new HashMap<>();
      userAttributes.put("angela.version", AngelaVersion.getAngelaVersion());
      userAttributes.put("nodename", nodeName);
      cfg.setUserAttributes(userAttributes);
      if (!nodesToJoin.isEmpty()) {
        cfg.setIgniteInstanceName(nodeName);
      } else {
        cfg.setIgniteInstanceName("localhost");
      }
      boolean enableLogging = Boolean.getBoolean(IGNITE_LOGGING.getValue());
      cfg.setGridLogger(enableLogging ? new Slf4jLogger() : new NullLogger());
      cfg.setPeerClassLoadingEnabled(true);
      cfg.setMetricsLogFrequency(0);

      if (nodesToJoin.isEmpty()) {
        logger.info("'{}' creating new isolated cluster", nodeName);
      } else {
        logger.info("'{}' joining isolated cluster on {}", nodeName, nodesToJoin);
      }

      List<String> nodesToJoinHostnames = new ArrayList<>();
      Map<String, String> hostnameToIpMapping = new HashMap<>();

      nodesToJoin.forEach(hostIpStr -> {
        String[] hostIp = hostIpStr.split("/");
        nodesToJoinHostnames.add(hostIp[0]);
        if (hostIp.length > 1) {
          int lastColon = hostIp[0].lastIndexOf(":");
          if (lastColon == -1 || isValidIPv6(hostIp[0])) {
            hostnameToIpMapping.put(hostIp[0], hostIp[1]);
          } else {
            hostnameToIpMapping.put(hostIp[0].substring(0, lastColon), hostIp[1]);
          }
        }
      });

      TcpDiscoverySpi spi = new TcpDiscoverySpi();
      spi.setIpFinder(new TcpDiscoveryVmIpFinder(true).setAddresses(nodesToJoinHostnames));
      spi.setLocalPort(40000);
      spi.setJoinTimeout(10000);
      spi.setLocalPortRange(portRange);
      cfg.setCommunicationSpi(new TcpCommunicationSpi().setLocalPortRange(portRange));
      cfg.setDiscoverySpi(spi);

      // Mapping internal ip addresses to public addresses to that ignite node be able to discover each other.
      if (!hostnameToIpMapping.isEmpty()) {
        logger.info("Adding address resolver for : " + hostnameToIpMapping);
        try {
          cfg.setAddressResolver(new BasicAddressResolver(hostnameToIpMapping));
        } catch (UnknownHostException e) {
          throw new IllegalArgumentException(e);
        }

        // only configure the localhost when joining other nodes with public IPs.
        cfg.setLocalHost(IpUtils.getHostName());
      }

      try {
        ignite = Ignition.start(cfg);
        IgniteCommonHelper.checkForDuplicateAgent(ignite, nodeName);
      } catch (IgniteException e) {
        throw new RuntimeException("Error starting agent " + nodeName, e);
      }

      controller = new AgentController(ignite, nodesToJoin.isEmpty() ? Collections.singleton(new HostPort(nodeName, 40000).getHostPort()) : nodesToJoin);
      logger.info("Registered node '" + nodeName + "'");
    }

    @Override
    public void close() {
      controller = null;
      if (ignite != null) {
        ignite.close();
        ignite = null;
      }
    }

  }
}
