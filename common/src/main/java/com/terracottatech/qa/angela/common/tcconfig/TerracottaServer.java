package com.terracottatech.qa.angela.common.tcconfig;

/**
 * Logical definition of a Terracotta server instance
 *
 * @author Aurelien Broszniowski
 */
public class TerracottaServer {

  private final ServerSymbolicName serverSymbolicName;
  private final String hostName;
  private volatile int tsaPort;
  private volatile int tsaGroupPort;
  private volatile int managementPort;
  private volatile int jmxPort;
  private volatile String repository;
  private volatile String logs;
  private volatile String metaData;

  private TerracottaServer(String serverSymbolicName, String hostName) {
    this.serverSymbolicName = new ServerSymbolicName(serverSymbolicName);
    this.hostName = hostName;
  }

  public static TerracottaServer tcServer(String symbolicName, String hostName) {
    TerracottaServer terracottaServer = new TerracottaServer(symbolicName, hostName);
    return terracottaServer;
  }

  public TerracottaServer tsaPort(int tsaPort) {
    this.tsaPort = tsaPort;
    return this;
  }

  public TerracottaServer groupPort(int groupPort) {
    this.tsaGroupPort = groupPort;
    return this;
  }

  public TerracottaServer managementPort(int managementPort) {
    this.managementPort = managementPort;
    return this;
  }

  public TerracottaServer jmxPort(int jmxPort) {
    this.jmxPort = jmxPort;
    return this;
  }

  public TerracottaServer repository(String repository) {
    this.repository = repository;
    return this;
  }

  public TerracottaServer logs(String logs) {
    this.logs = logs;
    return this;
  }

  public TerracottaServer metaData(String metaData) {
    this.metaData = metaData;
    return this;
  }

  public ServerSymbolicName getServerSymbolicName() {
    return serverSymbolicName;
  }

  public String getHostname() {
    return hostName;
  }

  public int getTsaPort() {
    return tsaPort;
  }

  public int getTsaGroupPort() {
    return tsaGroupPort;
  }

  public int getManagementPort() {
    return managementPort;
  }

  public int getJmxPort() {
    return jmxPort;
  }

  public String getRepository() {
    return repository;
  }

  public String getMetaData() {
    return metaData;
  }

  public String getLogs() {
    return logs;
  }

  @Override
  public String toString() {
    return "TerracottaServer{" +
        "serverSymbolicName=" + serverSymbolicName +
        ", hostname='" + hostName + '\'' +
        '}';
  }
}
