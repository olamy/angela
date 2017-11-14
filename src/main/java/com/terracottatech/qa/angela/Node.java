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

import org.apache.ignite.Ignite;
import org.apache.ignite.IgniteCompute;
import org.apache.ignite.IgniteQueue;
import org.apache.ignite.Ignition;
import org.apache.ignite.cluster.ClusterGroup;
import org.apache.ignite.configuration.CollectionConfiguration;
import org.apache.ignite.configuration.IgniteConfiguration;
import org.apache.ignite.lang.IgniteRunnable;

import java.util.Collections;

/**
 * @author Ludovic Orban
 */
public class Node {

  private final String instanceName;
  private volatile Ignite ignite;

  public Node(String instanceName) {
    this.instanceName = instanceName;
  }

  public void executeOnHost(String nodename, IgniteRunnable runnable) {
    ClusterGroup clusterGroup = ignite.cluster().forAttribute("nodename", nodename);
    IgniteCompute compute = ignite.compute(clusterGroup);
    compute.broadcast(runnable);
  }

  public void executeOnAll(IgniteRunnable runnable) {
    IgniteCompute compute = ignite.compute();
    compute.broadcast(runnable);
  }

  public void init() {
    IgniteConfiguration cfg = new IgniteConfiguration();

    cfg.setUserAttributes(Collections.singletonMap("nodename", instanceName));
    cfg.setIgniteInstanceName(instanceName);
    cfg.setPeerClassLoadingEnabled(true);

    ignite = Ignition.start(cfg);
  }

  public <T> IgniteQueue<T> getQueue(String queueName) {
    return ignite.queue(queueName, 10000, new CollectionConfiguration());
  }

  public void shutdown() {
    ignite.close();
    ignite = null;
  }

}
