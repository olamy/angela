package com.terracottatech.qa.angela.client;

import com.terracottatech.qa.angela.agent.Agent;
import com.terracottatech.qa.angela.common.util.AngelaVersion;
import org.apache.ignite.Ignite;
import org.apache.ignite.IgniteException;
import org.apache.ignite.cluster.ClusterGroup;
import org.apache.ignite.lang.IgniteCallable;
import org.apache.ignite.lang.IgniteFuture;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class IgniteHelper {

  public static void checkAgentHealth(Ignite ignite, String nodeName) {
    ClusterGroup location = ignite.cluster().forAttribute("nodename", nodeName);
    IgniteFuture<Collection<Map<String, ?>>> future = ignite.compute(location).broadcastAsync((IgniteCallable<Map<String, ?>>) () -> Agent.CONTROLLER.getNodeAttributes());
    try {
      Collection<Map<String, ?>> attributeMaps = future.get(10, TimeUnit.SECONDS);
      if (attributeMaps.size() != 1) {
        throw new IllegalStateException("Detected " + attributeMaps.size() + " agents with node name [" + nodeName + "] while expected exactly one");
      }
      Map<String, ?> attributeMap = attributeMaps.iterator().next();
      if (!nodeName.equals(attributeMap.get("nodename"))) {
        throw new IllegalStateException("Agent " + nodeName + " mistakenly identifies itself as " + attributeMap);
      }
      if (!AngelaVersion.getAngelaVersion().equals(attributeMap.get("angela.version"))) {
        throw new IllegalStateException("Agent " + nodeName + " is running version [" + attributeMap.get("angela.version") + "]" +
            " but the expected version is [" + AngelaVersion.getAngelaVersion() + "]");
      }
    } catch (IgniteException e) {
      throw new IllegalStateException("Node with name '" + nodeName + "' not found in the cluster", e);
    }
  }

}
