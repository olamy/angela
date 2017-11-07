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
package com.terracottatech.qa.angela;

import java.net.InetAddress;

/**
 * @author Ludovic Orban
 */
public class Agent {

  private static volatile Node node;

  public static void main(String[] args) throws Exception {
    String hostName = InetAddress.getLocalHost().getHostName();
    node = new Node(hostName);
    node.init();

    Runtime.getRuntime().addShutdownHook(new Thread(node::shutdown));

    System.out.println("Registered node '" + hostName + "'");
  }

}
