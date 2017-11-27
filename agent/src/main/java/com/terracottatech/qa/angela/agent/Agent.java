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

import com.terracottatech.qa.angela.common.kit.KitManager;
import org.apache.ignite.Ignite;
import org.apache.ignite.Ignition;
import org.apache.ignite.configuration.IgniteConfiguration;

import java.net.InetAddress;
import java.util.Collections;

/**
 * @author Ludovic Orban
 */
public class Agent {

  public static volatile AgentControl CONTROL;

  public static void main(String[] args) throws Exception {
    String nodeName = System.getProperty("tc.qa.nodeName", InetAddress.getLocalHost().getHostName());
    final Node node = new Node(nodeName);
    node.init();

    Runtime.getRuntime().addShutdownHook(new Thread(node::shutdown));

    System.out.println("Working directory is " + KitManager.KITS_DIR);
    System.out.println("Registered node '" + nodeName + "'");
  }

  static class Node {

    private final String nodeName;
    private volatile Ignite ignite;

    public Node(String nodeName) {
      this.nodeName = nodeName;
    }

    public void init() {
      IgniteConfiguration cfg = new IgniteConfiguration();

      cfg.setUserAttributes(Collections.singletonMap("nodename", nodeName));
      cfg.setIgniteInstanceName(nodeName);
      cfg.setPeerClassLoadingEnabled(true);

      ignite = Ignition.start(cfg);

      CONTROL = new AgentControl(ignite);
    }

    public void shutdown() {
      ignite.close();
      ignite = null;
      CONTROL = null;
    }

  }
}
