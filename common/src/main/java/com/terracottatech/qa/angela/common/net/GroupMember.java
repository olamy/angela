package com.terracottatech.qa.angela.common.net;

/**
 *
 */
public class GroupMember {
  private final String serverName;
  private final String host;
  private final int groupPort;
  private final int proxyPort;


  public GroupMember(String serverName, String host, int groupPort) {
    this(serverName, host, groupPort, -1);
  }

  public GroupMember(String serverName, String host, int groupPort, int proxyPort) {
    this.serverName = serverName;
    this.host = host;
    this.groupPort = groupPort;
    this.proxyPort = proxyPort;
  }

  public String getServerName() {
    return serverName;
  }

  public String getHost() {
    return host;
  }

  public int getGroupPort() {
    return groupPort;
  }

  public int getProxyPort() {
    return proxyPort;
  }


  public boolean isProxiedMember() {
    return proxyPort > 0;
  }

  @Override
  public String toString() {
    return "GroupMember{" +
           "serverName='" + serverName + '\'' +
           ", host='" + host + '\'' +
           ", groupPort=" + groupPort +
           ", proxyPort=" + proxyPort +
           '}';
  }
}
