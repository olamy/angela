package com.terracottatech.qa.angela.common.tcconfig;

import com.terracottatech.qa.angela.common.topology.Version;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * @author Aurelien Broszniowski
 */

public class TsaConfig {

  private final Version version;
  private final List<TsaStripeConfig> stripeConfigs;

  TsaConfig(Version version, TsaStripeConfig stripeConfig, TsaStripeConfig... stripeConfigs) {
    if (version.getMajor() < 10) {
      throw new UnsupportedOperationException("Dynamic TcConfig generation for BigMemory is not supported");
    }
    this.version = version;
    this.stripeConfigs = new ArrayList<>();
    this.stripeConfigs.add(stripeConfig);
    Collections.addAll(this.stripeConfigs, stripeConfigs);
  }

  public static TsaConfig tsaConfig(Version version, TsaStripeConfig stripeConfig, TsaStripeConfig... stripeConfigs) {
    return new TsaConfig(version, stripeConfig, stripeConfigs);
  }

  public List<TcConfig> buildTcConfigs() {
    List<TcConfig> tcConfigs = new ArrayList<>();

    for (int i = 0; i < stripeConfigs.size(); i++) {
      final TsaStripeConfig stripeConfig = stripeConfigs.get(i);
      TcConfig tcConfig = new TcConfig(version, TsaConfig.class.getResource("tsa-config-tc-config-template-10.xml"));
      for (String hostname : stripeConfig.getHostnames()) {
        tcConfig.addServer((i + 1), hostname);
      }

      final TsaStripeConfig.TsaOffheapConfig tsaOffheapConfig = stripeConfig.getTsaOffheapConfig();
      if (tsaOffheapConfig != null) {
        tcConfig.addOffheap(tsaOffheapConfig.getResourceName(), tsaOffheapConfig.getSize(),
            tsaOffheapConfig.getUnit());
      }

      final TsaStripeConfig.TsaDataDirectory tsaDataDirectory = stripeConfig.getTsaDataDirectory();
      if (tsaDataDirectory != null) {
        tcConfig.addDataDirectory(tsaDataDirectory.getDataName(), tsaDataDirectory.getLocation(), tsaDataDirectory.isUseForPlatform());
      }
      tcConfigs.add(tcConfig);
    }

    return tcConfigs;
  }
}
