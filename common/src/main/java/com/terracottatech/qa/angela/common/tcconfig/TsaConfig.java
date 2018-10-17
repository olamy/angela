package com.terracottatech.qa.angela.common.tcconfig;

import com.terracottatech.qa.angela.common.topology.Version;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Aurelien Broszniowski
 */

public class TsaConfig {

  private final Version version;
  private List<TcConfig> tcConfigs = new ArrayList<>();
  private int stripeCount = 1;


  TsaConfig(final Version version) {
    this.version = version;
  }

  public static TsaConfig tsaConfig(Version version) {
    return new TsaConfig(version);
  }

  public TsaConfig stripes( String hostname, int hostCount) {
    String[] hostnames = new String[hostCount];
    for (int i = 0; i < hostCount; i++) {
      hostnames[i] = hostname;
    }
    return stripes( hostnames);
  }

  public TsaConfig stripes( String... hostnames) {
    if (version.getMajor() < 10) {
      throw new UnsupportedOperationException("Dynamic TcConfig generation for BigMemory is not supported");
    }
    TcConfig tcConfig = new TcConfig(version, TsaConfig.class.getResource("/terracotta/10/tc-config.xml"));

    for (int i = 0; i < stripeCount; i++) {
      for (final String hostname : hostnames) {
        tcConfig.addServer(i + 1, hostname);
      }
    }
    this.tcConfigs.add(tcConfig);
    return this;
  }

  public TsaConfig times(int stripeCount) {
    this.stripeCount = stripeCount;
    return this;
  }

  public TcConfig[] getTcConfigs() {
    return tcConfigs.toArray(new TcConfig[0]);
  }
}
