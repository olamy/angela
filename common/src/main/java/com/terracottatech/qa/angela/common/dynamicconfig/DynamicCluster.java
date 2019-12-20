package com.terracottatech.qa.angela.common.dynamicconfig;

import java.util.Arrays;
import java.util.List;

public class DynamicCluster {
  private final String clusterName;
  private final List<Stripe> stripes;

  private DynamicCluster(String clusterName, List<Stripe> stripes) {
    this.clusterName = clusterName;
    this.stripes = stripes;
  }

  public static DynamicCluster dynamicCluster(String clusterName, Stripe... stripes) {
    DynamicCluster dynamicCluster = new DynamicCluster(clusterName, Arrays.asList(stripes));
    return dynamicCluster;
  }

  public String getClusterName() {
    return this.clusterName;
  }

  public List<Stripe> getStripes() {
    return this.stripes;
  }

}
