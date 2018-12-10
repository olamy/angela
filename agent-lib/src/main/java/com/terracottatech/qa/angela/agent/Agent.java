/*
 * Copyright Terracotta, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.terracottatech.qa.angela.agent;

import com.terracottatech.qa.angela.common.util.AngelaVersion;
import com.terracottatech.qa.angela.common.util.IgniteCommonHelper;
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

import java.io.Closeable;
import java.io.File;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author Ludovic Orban
 */
public class Agent {

  private final static Logger LOGGER = LoggerFactory.getLogger(Agent.class);

  public static final int DFLT_ANGELA_PORT_RANGE = 1000;
  public static final String ROOT_DIR;
  public static final String ROOT_DIR_SYSPROP_NAME = "kitsDir";
  public static final String AGENT_IS_READY_MARKER_LOG = "Agent is ready";
  public static final String IGNITE_LOGGING_SYSPROP_NAME = "tc.qa.angela.logging";
  public static final File WORK_DIR;

  static {
    final String dir = System.getProperty(ROOT_DIR_SYSPROP_NAME);
    final String DEFAULT_ROOT_DIR = new File("/data/angela").getAbsolutePath();
    if (dir == null || dir.isEmpty()) {
      ROOT_DIR = DEFAULT_ROOT_DIR;
    } else if (dir.startsWith(".")) {
      throw new IllegalArgumentException("Can not use relative path for the ROOT_DIR. Please use a fixed one.");
    } else {
      ROOT_DIR = dir;
    }
    WORK_DIR = new File(ROOT_DIR, "work");
  }


  public static volatile AgentController CONTROLLER;

  public static void main(String[] args) throws Exception {
    // the angela-agent jar may end up on the classpath, so its logback config cannot have the default filename
    System.setProperty("logback.configurationFile", "angela-logback.xml");
    String nodeName = System.getProperty("tc.qa.nodeName", InetAddress.getLocalHost().getHostName());
    String directjoin = System.getProperty("tc.qa.directjoin");
    String portRange = System.getProperty("tc.qa.portrange", "" + DFLT_ANGELA_PORT_RANGE);

    List<String> nodesToJoin = new ArrayList<>();
    if (directjoin != null) {
      for (String node : directjoin.split(",")) {
        if (!node.trim().isEmpty()) {
          nodesToJoin.add(node);
        }
      }
    }
    final Node node = new Node(nodeName, nodesToJoin, Integer.parseInt(portRange));

    Runtime.getRuntime().addShutdownHook(new Thread(node::close));

    // Do not use logger here as the marker is being grep'ed at and we do not want to depend upon the logger config
    System.out.println(AGENT_IS_READY_MARKER_LOG);
    System.out.flush();
  }

  public static class Node implements Closeable {

    private volatile Ignite ignite;

    public Node(String nodeName, List<String> nodesToJoin) {
      this(nodeName, nodesToJoin, DFLT_ANGELA_PORT_RANGE);
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
      File rootDirFile = new File(ROOT_DIR);
      LOGGER.info("Root directory is : " + rootDirFile);
      if (!rootDirFile.exists()) {
        if (!rootDirFile.mkdirs()) {
          throw new RuntimeException("Auto creation of root directory: " + rootDirFile + " failed. " +
                                     "Make sure that the provided directory is writable or create one manually.");
        }
      }
      if (!rootDirFile.isDirectory()) {
        throw new RuntimeException("Root directory is not a folder : " + rootDirFile);
      }
      if (!rootDirFile.canWrite()) {
        throw new RuntimeException("Root directory is not writable : " + rootDirFile);
      }

      IgniteConfiguration cfg = new IgniteConfiguration();
      cfg.setIgniteHome(new File(rootDirFile, "ignite").getPath());
      Map<String, String> userAttributes = new HashMap<>();
      userAttributes.put("angela.version", AngelaVersion.getAngelaVersion());
      userAttributes.put("nodename", nodeName);
      cfg.setUserAttributes(userAttributes);
      cfg.setIgniteInstanceName(nodeName);
      boolean enableLogging = Boolean.getBoolean(IGNITE_LOGGING_SYSPROP_NAME);
      cfg.setGridLogger(enableLogging ? new Slf4jLogger() : new NullLogger());
      cfg.setPeerClassLoadingEnabled(true);
      cfg.setMetricsLogFrequency(0);

      if (nodesToJoin.isEmpty()) {
        LOGGER.info("'{}' creating new isolated cluster", nodeName);
      } else {
        LOGGER.info("'{}' joining isolated cluster on {}", nodeName, nodesToJoin);
      }

      List<String> nodesToJoinHostnames = new ArrayList<>();
      Map<String, String> hostnameToIpMapping = new HashMap<>();

      nodesToJoin.forEach(hostIpStr -> {
        String[] hostIp = hostIpStr.split("/");
        nodesToJoinHostnames.add(hostIp[0]);
        if (hostIp.length > 1) {
          hostnameToIpMapping.put(hostIp[0].split(":")[0], hostIp[1]);
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
        LOGGER.info("Adding address resolver for : " + hostnameToIpMapping);
        try {
          cfg.setAddressResolver(new BasicAddressResolver(hostnameToIpMapping));
        } catch (UnknownHostException e) {
          throw new IllegalArgumentException(e);
        }

        // only configure the localhost when joining other nodes with public IPs.
        try {
          cfg.setLocalHost(InetAddress.getLocalHost().getHostAddress());
        } catch (UnknownHostException e) {
          throw new IllegalStateException(e);
        }
      }

      try {
        ignite = Ignition.start(cfg);
        IgniteCommonHelper.checkForDuplicateAgent(ignite, nodeName);
      } catch (IgniteException e) {
        throw new RuntimeException("Error starting agent " + nodeName, e);
      }

      CONTROLLER = new AgentController(ignite, nodesToJoin.isEmpty() ? Collections.singleton(nodeName + ":40000") : nodesToJoin);
      LOGGER.info("Registered node '" + nodeName + "'");
    }

    @Override
    public void close() {
      CONTROLLER = null;
      if (ignite != null) {
        ignite.close();
        ignite = null;
      }
    }

  }
}
