package com.terracottatech.qa.angela.common.client;

import com.terracottatech.qa.angela.common.topology.InstanceId;
import org.apache.ignite.Ignite;

public class Context {
  private final String nodeName;
  private final Ignite ignite;
  private final InstanceId instanceId;

  public Context(String nodeName, Ignite ignite, InstanceId instanceId) {
    this.nodeName = nodeName;
    this.ignite = ignite;
    this.instanceId = instanceId;
  }

  public Barrier barrier(String name, int count) {
    return new Barrier(ignite, count, name);
  }

}
