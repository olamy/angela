package com.terracottatech.qa.angela.common.util;

import com.terracottatech.qa.angela.common.topology.InstanceId;
import org.apache.ignite.Ignite;
import org.apache.ignite.IgniteException;
import org.apache.ignite.cluster.ClusterGroup;
import org.apache.ignite.configuration.CollectionConfiguration;
import org.apache.ignite.lang.IgniteCallable;
import org.apache.ignite.lang.IgniteFuture;

import java.util.Collection;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

public class IgniteCommonHelper {

  public static BlockingQueue<Object> fileTransferQueue(Ignite ignite, InstanceId instanceId) {
    return ignite.queue(instanceId + "@file-transfer-queue", 100, new CollectionConfiguration());
  }

  public static void checkForDuplicateAgent(Ignite ignite, String nodeName) {
    ClusterGroup location = ignite.cluster().forAttribute("nodename", nodeName);
    IgniteFuture<Collection<Object>> future = ignite.compute(location).broadcastAsync((IgniteCallable<Object>) () -> 0);
    Collection<Object> results = future.get(60, TimeUnit.SECONDS);
    if (results.size() != 1) {
      throw new IllegalStateException("Node with name [" + nodeName + "] already exists on the network, refusing to duplicate it.");
    }
  }

}
