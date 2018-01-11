package com.terracottatech.qa.angela.common.client;

import org.apache.ignite.Ignite;

import com.terracottatech.qa.angela.common.topology.InstanceId;

import java.net.URI;

public class Context {
  private final String nodeName;
  private final Ignite ignite;
  private final InstanceId instanceId;
  private final URI tsaUri;

  public Context(String nodeName, Ignite ignite, InstanceId instanceId, URI tsaUri) {
    this.nodeName = nodeName;
    this.ignite = ignite;
    this.instanceId = instanceId;
    this.tsaUri = tsaUri;
  }

  public Barrier barrier(String name, int count) {
    return new Barrier(ignite, count,instanceId + "@" + name);
  }

  public URI tsaURI() {
    return tsaUri;
  }
}
