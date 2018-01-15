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

import org.apache.ignite.Ignite;
import org.apache.ignite.Ignition;
import org.apache.ignite.configuration.IgniteConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.net.InetAddress;
import java.util.Collections;

/**
 * @author Ludovic Orban
 */
public class Agent {

  private final static Logger LOGGER = LoggerFactory.getLogger(Agent.class);

  public static final String WORK_DIR;

  static {
    final String dir = System.getProperty("kitsDir");
    final String DEFAULT_WORK_DIR = "/data/tsamanager";
    if (dir == null || dir.isEmpty()) {
      WORK_DIR = DEFAULT_WORK_DIR;
    } else if (dir.startsWith(".")) {
      throw new IllegalArgumentException("Can not use relative path for the WORK_DIR. Please use a fixed one.");
    } else {
      WORK_DIR = dir;
    }
  }


  public static volatile AgentController CONTROLLER;

  public static void main(String[] args) throws Exception {
    String nodeName = System.getProperty("tc.qa.nodeName", InetAddress.getLocalHost().getHostName());
    final Node node = new Node(nodeName);
    node.init();

    Runtime.getRuntime().addShutdownHook(new Thread(node::shutdown));

    LOGGER.info("Agent is ready");
  }

  public static class Node {

    private final String nodeName;
    private volatile Ignite ignite;

    public Node(String nodeName) {
      this.nodeName = nodeName;
    }

    public void init() {
      File workDirFile = new File(WORK_DIR);
      LOGGER.info("Working directory is : " + workDirFile);
      if (!workDirFile.isDirectory()) {
        throw new RuntimeException("Working directory is not a folder : " + workDirFile);
      }
      if (!workDirFile.canWrite()) {
        throw new RuntimeException("Working directory is not writable : " + workDirFile);
      }

      IgniteConfiguration cfg = new IgniteConfiguration();

      cfg.setUserAttributes(Collections.singletonMap("nodename", nodeName));
      cfg.setIgniteInstanceName(nodeName);
      cfg.setPeerClassLoadingEnabled(true);

      ignite = Ignition.start(cfg);
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
