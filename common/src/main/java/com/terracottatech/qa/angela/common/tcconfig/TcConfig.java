package com.terracottatech.qa.angela.common.tcconfig;

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

  private TcConfig(Version version, URL tcConfigPath) {
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
    }
    catch (IOException e) {
      throw new RuntimeException("Cannot read tc-config file : " + tcConfigPath, e);
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
