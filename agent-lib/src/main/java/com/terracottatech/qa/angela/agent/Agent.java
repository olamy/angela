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
import org.apache.ignite.Ignite;
import org.apache.ignite.IgniteException;
import org.apache.ignite.Ignition;
import org.apache.ignite.cluster.ClusterGroup;
import org.apache.ignite.configuration.IgniteConfiguration;
import org.apache.ignite.lang.IgniteCallable;
import org.apache.ignite.lang.IgniteFuture;
import org.apache.ignite.logger.NullLogger;
import org.apache.ignite.logger.slf4j.Slf4jLogger;
import org.apache.ignite.spi.discovery.tcp.TcpDiscoverySpi;
import org.apache.ignite.spi.discovery.tcp.ipfinder.vm.TcpDiscoveryVmIpFinder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.net.InetAddress;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * @author Ludovic Orban
 */
public class Agent {

  private final static Logger LOGGER = LoggerFactory.getLogger(Agent.class);

  public static final String ROOT_DIR;
  public static final String ROOT_DIR_SYSPROP_NAME = "kitsDir";
  public static final String AGENT_IS_READY_MARKER_LOG = "Agent is ready";
  public static final String IGNITE_LOGGING_SYSPROP_NAME = "tc.qa.angela.logging";

  static {
    final String dir = System.getProperty(ROOT_DIR_SYSPROP_NAME);
    final String DEFAULT_WORK_DIR = new File("/data/tsamanager").getAbsolutePath();
    if (dir == null || dir.isEmpty()) {
      ROOT_DIR = DEFAULT_WORK_DIR;
    } else if (dir.startsWith(".")) {
      throw new IllegalArgumentException("Can not use relative path for the ROOT_DIR. Please use a fixed one.");
    } else {
      ROOT_DIR = dir;
    }
  }


  public static volatile AgentController CONTROLLER;

  public static void main(String[] args) throws Exception {
    // the angela-agent jar may end up on the classpath, so its logback config cannot have the default filename
    System.setProperty("logback.configurationFile", "/angela-logback.xml");
    String nodeName = System.getProperty("tc.qa.nodeName", InetAddress.getLocalHost().getHostName());
    String directjoin = System.getProperty("tc.qa.directjoin");

    final Node node;
    if (directjoin != null) {
      String[] split = directjoin.split(",");
      node = new Node(nodeName, Arrays.asList(split));
    } else {
      node = new Node(nodeName);
    }

    node.init();

    Runtime.getRuntime().addShutdownHook(new Thread(node::shutdown));

    // Do not use logger here as the marker is being grep'ed at and we do not want to depend upon the logger config
    System.out.println(AGENT_IS_READY_MARKER_LOG);
  }

  public static class Node {

    private final String nodeName;
    private final List<String> nodesToJoin;
    private volatile Ignite ignite;

    public Node(String nodeName) {
      this(nodeName, null);
    }

    public Node(String nodeName, List<String> nodesToJoin) {
      this.nodeName = nodeName;
      this.nodesToJoin = nodesToJoin;
    }

    public void init() {
      File workDirFile = new File(ROOT_DIR);
      LOGGER.info("Root directory is : " + workDirFile);
      if (!workDirFile.exists()) {
        workDirFile.mkdirs();
      }
      if (!workDirFile.isDirectory()) {
        throw new RuntimeException("Root directory is not a folder : " + workDirFile);
      }
      if (!workDirFile.canWrite()) {
        throw new RuntimeException("Root directory is not writable : " + workDirFile);
      }

      IgniteConfiguration cfg = new IgniteConfiguration();
      cfg.setIgniteHome(new File(workDirFile, "ignite").getPath());
      Map<String, String> userAttributes = new HashMap<>();
      userAttributes.put("angela.version", AngelaVersion.getAngelaVersion());
      userAttributes.put("nodename", nodeName);
      cfg.setUserAttributes(userAttributes);
      cfg.setIgniteInstanceName(nodeName);
      boolean enableLogging = Boolean.getBoolean(IGNITE_LOGGING_SYSPROP_NAME);
      cfg.setGridLogger(enableLogging ? new Slf4jLogger() : new NullLogger());
      cfg.setPeerClassLoadingEnabled(true);
      cfg.setMetricsLogFrequency(0);

      if (nodesToJoin != null) {
        LOGGER.info("Joining isolated cluster : " + nodesToJoin);
        TcpDiscoverySpi spi = new TcpDiscoverySpi();
        spi.setLocalPort(40000);
        TcpDiscoveryVmIpFinder ipFinder = new TcpDiscoveryVmIpFinder();
        ipFinder.setShared(true);
        ipFinder.setAddresses(nodesToJoin);
        spi.setIpFinder(ipFinder);
        spi.setJoinTimeout(10000);
        cfg.setDiscoverySpi(spi);
      } else if (nodeName.equals("localhost")) {
        LOGGER.info("Isolating cluster to localhost");
        TcpDiscoverySpi spi = new TcpDiscoverySpi();
        spi.setIpFinder(new TcpDiscoveryVmIpFinder(true).setAddresses(Collections.singleton("localhost")));
        spi.setLocalPort(40000);
        spi.setJoinTimeout(10000);
        cfg.setDiscoverySpi(spi);
      } else {
        LOGGER.info("Joining multicast cluster");
      }

      try {
        ignite = Ignition.start(cfg);
      } catch (IgniteException e) {
        throw new RuntimeException("Error starting agent " + nodeName, e);
      }

      ClusterGroup location = ignite.cluster().forAttribute("nodename", nodeName);
      IgniteFuture<Collection<Object>> future = ignite.compute(location).broadcastAsync((IgniteCallable<Object>) () -> 0);
      try {
        Collection<Object> results = future.get(10, TimeUnit.SECONDS);
        if (results.size() != 1) {
          ignite.close();
          throw new IllegalStateException("Node with name [" + nodeName + "] already exists on the network, refusing to duplicate it.");
        }
      } catch (IgniteException e) {
        // expected
      }

      CONTROLLER = new AgentController(ignite);
      LOGGER.info("Registered node '" + nodeName + "'");
    }

    public void shutdown() {
      CONTROLLER = null;
      if (ignite != null) {
        ignite.close();
        ignite = null;
      }
    }

  }
}
