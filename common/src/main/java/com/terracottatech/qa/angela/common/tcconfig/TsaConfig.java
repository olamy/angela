package com.terracottatech.qa.angela.common.tcconfig;

import com.terracottatech.qa.angela.common.topology.Version;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * @author Aurelien Broszniowski
 */

public class TsaConfig {

  private final List<TcConfig> tcConfigs;

  TsaConfig(List<TcConfig> tcConfigs) {
    this.tcConfigs = tcConfigs;
  }

  public static TsaConfig tsaConfig(Version version, TsaStripeConfig stripeConfig, TsaStripeConfig... stripeConfigs) {
    if (version.getMajor() < 10) {
      throw new UnsupportedOperationException("Dynamic TcConfig generation for BigMemory is not supported");
    }
    List<TsaStripeConfig> cfgs = new ArrayList<>();
    cfgs.add(stripeConfig);
    cfgs.addAll(Arrays.asList(stripeConfigs));
    return new TsaConfig(buildTcConfigs(version, cfgs));
  }

  public static TsaConfig tsaConfig(TcConfig tcConfig, TcConfig... tcConfigs) {
    List<TcConfig> cfgs = new ArrayList<>();
    cfgs.add(tcConfig);
    cfgs.addAll(Arrays.asList(tcConfigs));
    return new TsaConfig(cfgs);
  }

  public static TsaConfig tsaConfig(List<TcConfig> tcConfigs) {
    return new TsaConfig(new ArrayList<>(tcConfigs));
  }

  public List<TcConfig> getTcConfigs() {
    return Collections.unmodifiableList(tcConfigs);
  }

  private static List<TcConfig> buildTcConfigs(Version version, List<TsaStripeConfig> stripeConfigs) {
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

  @Override
  public String toString() {
    return "TsaConfig{" +
        "tcConfigs=" + tcConfigs +
        '}';
  }
}
