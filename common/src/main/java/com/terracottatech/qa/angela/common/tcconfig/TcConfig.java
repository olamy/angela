package com.terracottatech.qa.angela.common.tcconfig;

import com.terracottatech.qa.angela.common.tcconfig.holders.TcConfig10Holder;
import com.terracottatech.qa.angela.common.tcconfig.holders.TcConfig8Holder;
import com.terracottatech.qa.angela.common.tcconfig.holders.TcConfig9Holder;
import com.terracottatech.qa.angela.common.tcconfig.holders.TcConfigHolder;
import com.terracottatech.qa.angela.common.topology.Version;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.Serializable;
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

  private final String tcConfigPath;
  private final TcConfigHolder tcConfigHolder;
  private final String tcConfigName;

  public static TcConfig tcConfig(Version version, String tcConfigPath) {
    return new TcConfig(version, tcConfigPath);
  }

  public TcConfig(final Version version, final String tcConfigPath) {
    this.tcConfigPath = tcConfigPath;
    this.tcConfigName = new File(tcConfigPath).getName();
    this.tcConfigHolder = initTcConfigHolder(version);
  }

  private TcConfigHolder initTcConfigHolder(final Version version) {
    try {
      if (version.getMajor() == 4) {
        if (version.getMinor() == 0) {
          return new TcConfig8Holder(new FileInputStream(
              getClass().getResource(this.tcConfigPath).getPath()));
        } else if (version.getMinor() >= 1) {
          return new TcConfig9Holder(new FileInputStream(
              getClass().getResource(this.tcConfigPath).getPath()));
        } else {
          throw new IllegalArgumentException("Cannot parse tc-config for version : " + version.toString());
        }
      } else if (version.getMajor() == 5) {
        return new TcConfig10Holder(new FileInputStream(
            getClass().getResource(this.tcConfigPath).getPath()));
      } else if (version.getMajor() == 10) {
        return new TcConfig10Holder(new FileInputStream(
            getClass().getResource(this.tcConfigPath).getPath()));
      } else {
        throw new IllegalArgumentException("Cannot parse tc-config for version : " + version.toString());
      }
    } catch (FileNotFoundException e) {
      throw new RuntimeException("Cannot read tc-config file : " + this.tcConfigPath, e);
    }
  }

  public Map<ServerSymbolicName, TerracottaServer> getServers() {
    return tcConfigHolder.getServers();
  }

  public Object getTcProperty(final String key) {
    return tcConfigHolder.getTcProperty(key);
  }

  public TerracottaServer getTerracottaServer(int index) {
    return this.tcConfigHolder.getServers().values().toArray(new TerracottaServer[0])[index];
  }


  public void updateTcProperties(final Properties tcProperties) {
    tcConfigHolder.setTcProperties(tcProperties);
  }

  public void writeTcConfigFile(File kitDir) {
    this.tcConfigHolder.writeTcConfigFile(kitDir, tcConfigName);
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

  public void updateDataDirectory(final String rootId, final String newlocation) {
    tcConfigHolder.updateDataDirectory(rootId, newlocation);
  }
}
