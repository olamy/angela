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

import org.apache.ignite.lang.IgniteRunnable;

/**
 * @author Ludovic Orban
 */
public class IgniteSample {

  public static void main(String[] args) throws Exception {
    System.setProperty("IGNITE_PERFORMANCE_SUGGESTIONS_DISABLED", "true");

    Node node1 = new Node("node1");
    Node node2 = new Node("node2");
    node1.init();
    node2.init();

    node1.executeOnAll((IgniteRunnable) () -> System.out.println("hi everywhere!"));
    node1.executeOnHost("node2", (IgniteRunnable) () -> System.out.println("hi on node2!"));
    node2.executeOnHost("node1", (IgniteRunnable) () -> System.out.println("hi on node1!"));

    node1.shutdown();
    node2.shutdown();
  }

}
