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
import java.io.Serializable;
import java.net.URL;
import java.util.List;
import java.util.Map;
import java.util.Properties;

/**
 * base tc config holder
 * <p/>
 *
 * @author Tim Eck
 */
public class TcConfig implements Serializable {

  private final TcConfigHolder tcConfigHolder;
  private final String tcConfigName;

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
          } else if (version.getMinor() >= 1) {
            return new TcConfig9Holder(is);
          } else {
            throw new IllegalArgumentException("Cannot parse tc-config for version : " + version.toString());
          }
        } else if (version.getMajor() == 5) {
          return new TcConfig10Holder(is);
        } else if (version.getMajor() == 10) {
          return new TcConfig10Holder(is);
        } else {
          throw new IllegalArgumentException("Cannot parse tc-config for version : " + version.toString());
        }
      }
    } catch (IOException e) {
      throw new RuntimeException("Cannot read tc-config file : " + tcConfigPath, e);
    }
  }

  public Map<ServerSymbolicName, TerracottaServer> getServers() {
    return tcConfigHolder.getServers();
  }

  public TerracottaServer getTerracottaServer(int index) {
    return this.tcConfigHolder.getServers().values().toArray(new TerracottaServer[0])[index];
  }

  public String getTcConfigName() {
    return tcConfigName;
  }

  public void createOrUpdateTcProperty(String name, String value) {
    tcConfigHolder.createOrUpdateTcProperty(name, value);
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

  public void updateLogsLocation(final File kitDir, final int tcConfigIndex) {
    tcConfigHolder.updateLogsLocation(kitDir, tcConfigIndex);
  }

  public void updateSecurityRootDirectoryLocation(final String securityRootDirectory) {
    tcConfigHolder.updateSecurityRootDirectoryLocation(securityRootDirectory);
  }

  public void updateAuditDirectoryLocation(final File kitDir, final int tcConfigIndex) {
    tcConfigHolder.updateAuditDirectoryLocation(kitDir, tcConfigIndex);
  }

  public void updateDataDirectory(final String rootId, final String newlocation) {
    tcConfigHolder.updateDataDirectory(rootId, newlocation);
  }

  public void updateServerHost(int serverIndex, String newServerName) {
    tcConfigHolder.updateServerHost(serverIndex, newServerName);
  }

  public List<GroupMember> retrieveGroupMembers(final String serverName, final boolean updateProxy){
    return tcConfigHolder.retrieveGroupMembers(serverName, updateProxy);
  }

  public Map<ServerSymbolicName, Integer> retrieveTsaPorts(final boolean updateForProxy) {
    return tcConfigHolder.retrieveTsaPorts(updateForProxy);
  }

  public void substituteToken(final String token, final String value){
    tcConfigHolder.substituteToken(token, value);
  }
  
}
