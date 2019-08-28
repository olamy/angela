package com.terracottatech.qa.angela;

import java.net.URL;

public class TestUtils {
  public static final URL TC_CONFIG_OS = TestUtils.class.getResource("/terracotta/10/tc-config-a-os.xml");

  public static final URL TC_CONFIG_10X_A = TestUtils.class.getResource("/terracotta/10/tc-config-a.xml");
  public static final URL TC_CONFIG_10X_AP = TestUtils.class.getResource("/terracotta/10/tc-config-ap.xml");
  public static final URL TC_CONFIG_10X_MULTISTRIPE1 = TestUtils.class.getResource("/terracotta/10/tc-config-multistripes1.xml");
  public static final URL TC_CONFIG_10X_MULTISTRIPE2 = TestUtils.class.getResource("/terracotta/10/tc-config-multistripes2.xml");

  public static final URL TC_CONFIG_4X_A = TestUtils.class.getResource("/terracotta/4/tc-config-a.xml");
  public static final URL TC_CONFIG_4X_AP = TestUtils.class.getResource("/terracotta/4/tc-config-ap.xml");

}