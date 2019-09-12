package com.terracottatech.qa.angela.common.tcconfig;

import com.terracottatech.qa.angela.common.net.GroupMember;
import com.terracottatech.qa.angela.common.tcconfig.holders.TcConfig10Holder;
import com.terracottatech.qa.angela.common.tcconfig.holders.TcConfig8Holder;
import com.terracottatech.qa.angela.common.tcconfig.holders.TcConfig9Holder;
import com.terracottatech.qa.angela.common.tcconfig.holders.TcConfigHolder;
import com.terracottatech.qa.angela.common.topology.Version;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.List;
import java.util.Map;

/**
 * base tc config holder
 * <p/>
 *
 * @author Tim Eck
 */
public class TcConfig {

  private final TcConfigHolder tcConfigHolder;
  private String tcConfigName;

  public static TcConfig tcConfig(Version version, URL tcConfigPath) {
    return new TcConfig(version, tcConfigPath);
  }

  public static TcConfig copy(TcConfig tcConfig) {
    return new TcConfig(tcConfig);
  }

  TcConfig(TcConfig tcConfig) {
    this.tcConfigName = tcConfig.tcConfigName;
    if (tcConfig.tcConfigHolder instanceof TcConfig8Holder) {
      this.tcConfigHolder = new TcConfig8Holder((TcConfig8Holder)tcConfig.tcConfigHolder);
    } else if (tcConfig.tcConfigHolder instanceof TcConfig9Holder) {
      this.tcConfigHolder = new TcConfig9Holder((TcConfig9Holder)tcConfig.tcConfigHolder);
    } else if (tcConfig.tcConfigHolder instanceof TcConfig10Holder) {
      this.tcConfigHolder = new TcConfig10Holder((TcConfig10Holder)tcConfig.tcConfigHolder);
    } else {
      throw new RuntimeException("Unexpected");
    }
  }

  TcConfig(Version version, URL tcConfigPath) {
    this.tcConfigName = new File(tcConfigPath.getPath()).getName();
    this.tcConfigHolder = initTcConfigHolder(version, tcConfigPath);
  }

  private TcConfigHolder initTcConfigHolder(Version version, URL tcConfigPath) {
    try {
      try (InputStream is = tcConfigPath.openStream()) {
        if (version.getMajor() == 4) {
          if (version.getMinor() == 0) {
            return new TcConfig8Holder(is);
          } else {
            return new TcConfig9Holder(is);
          }
        } else {
          return new TcConfig10Holder(is);
        }
      }
    } catch (IOException e) {
      throw new RuntimeException("Cannot read tc-config file : " + tcConfigPath, e);
    }
  }

  public List<TerracottaServer> getServers() {
    return tcConfigHolder.getServers();
  }

  public String getTcConfigName() {
    return tcConfigName;
  }

  public void setTcConfigName(String tcConfigName) {
    this.tcConfigName = tcConfigName;
  }

  public void createOrUpdateTcProperty(String name, String value) {
    tcConfigHolder.createOrUpdateTcProperty(name, value);
  }

  public String toXml() {
    return tcConfigHolder.getTcConfigContent();
  }

  public void writeTcConfigFile(File kitDir) {
    this.tcConfigHolder.writeTcConfigFile(kitDir, tcConfigName);
  }

  public void writeTcConfigFile(File kitDir, String name) {
    this.tcConfigHolder.writeTcConfigFile(kitDir, name);
  }

  public String getPath() {
    return this.tcConfigHolder.getTcConfigPath();
  }

  public List<String> getLogsLocation() {
    return this.tcConfigHolder.getLogsLocation();
  }

  public void updateLogsLocation(File kitDir, int stripeId) {
    tcConfigHolder.updateLogsLocation(kitDir, stripeId);
  }

  public void updateSecurityRootDirectoryLocation(String securityRootDirectory) {
    tcConfigHolder.updateSecurityRootDirectoryLocation(securityRootDirectory);
  }

  public void updateAuditDirectoryLocation(File kitDir, int stripeId) {
    tcConfigHolder.updateAuditDirectoryLocation(kitDir, stripeId);
  }

  public void updateDataDirectory(String rootId, String newlocation) {
    tcConfigHolder.updateDataDirectory(rootId, newlocation);
  }

  public Map<String, String> getDataDirectories() {
    return tcConfigHolder.getDataDirectories();
  }

  public List<String> getPluginServices() {
    return tcConfigHolder.getPluginServices();
  }

  public void updateServerHost(int serverIndex, String newServerName) {
    tcConfigHolder.updateServerHost(serverIndex, newServerName);
  }

  public void updateServerName(int serverIndex, String newServerName) {
    tcConfigHolder.updateServerName(serverIndex, newServerName);
  }

  public void updateServerPort(int serverIndex, String portName, int port) {
    tcConfigHolder.updateServerPort(serverIndex, portName, port);
  }

  public void addServer(int stripeIndex, String hostname) {
    tcConfigHolder.addServer(stripeIndex, hostname);
  }

  public List<GroupMember> retrieveGroupMembers(String serverName, boolean updateProxy) {
    return tcConfigHolder.retrieveGroupMembers(serverName, updateProxy);
  }

  public Map<ServerSymbolicName, Integer> retrieveTsaPorts(boolean updateForProxy) {
    return tcConfigHolder.retrieveTsaPorts(updateForProxy);
  }

  public void substituteToken(String token, String value) {
    tcConfigHolder.substituteToken(token, value);
  }

  public void addOffheap(String resourceName, String size, String unit) {
    tcConfigHolder.addOffheap(resourceName, size, unit);
  }

  public void addDataDirectoryList(List<TsaStripeConfig.TsaDataDirectory> tsaDataDirectoryList) {
    tcConfigHolder.addDataDirectory(tsaDataDirectoryList);
  }

  public void addPersistencePlugin(String persistenceDataName) {
    tcConfigHolder.addPersistencePlugin(persistenceDataName);
  }
}
