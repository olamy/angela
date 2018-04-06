package com.terracottatech.qa.angela.client;

import com.terracottatech.qa.angela.agent.Agent;
import org.apache.ignite.Ignite;
import org.apache.ignite.IgniteException;
import org.apache.ignite.cluster.ClusterGroup;
import org.apache.ignite.lang.IgniteCallable;
import org.apache.ignite.lang.IgniteFuture;

import java.util.Collection;
import java.util.concurrent.TimeUnit;

public class IgniteHelper {

  public static void checkAgentHealth(Ignite ignite, String nodeName) {
    ClusterGroup location = ignite.cluster().forAttribute("nodename", nodeName);
    IgniteFuture<Collection<String>> future = ignite.compute(location).broadcastAsync((IgniteCallable<String>) () -> Agent.CONTROLLER.getNodeName());
    try {
      Collection<String> strings = future.get(10, TimeUnit.SECONDS);
      if (strings.size() != 1) {
        throw new IllegalStateException("Detected " + strings.size() + " agents with node name [" + nodeName + "] while expected exactly one");
      }
      String string = strings.iterator().next();
      if (!nodeName.equals(string)) {
        throw new IllegalStateException("Agent " + nodeName + " mistakenly identifies itself as " + string);
      }
    } catch (IgniteException e) {
      throw new IllegalStateException("Node with name '" + nodeName + "' not found in the cluster", e);
    }
  }

}
