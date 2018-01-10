package com.terracottatech.qa.angela.common.client;

import org.apache.ignite.Ignite;

import com.terracottatech.qa.angela.common.topology.InstanceId;

import java.net.URI;

public class Context {
  private final String nodeName;
  private final Ignite ignite;
  private final InstanceId instanceId;
  private final URI clusterUri;

  public Context(String nodeName, Ignite ignite, InstanceId instanceId, URI clusterUri) {
    this.nodeName = nodeName;
    this.ignite = ignite;
    this.instanceId = instanceId;
    this.clusterUri = clusterUri;
  }

  public Barrier barrier(String name, int count) {
    return new Barrier(ignite, count,instanceId + "@" + name);
  }

  public URI clusterURI() {
    return clusterUri;
  }
}
