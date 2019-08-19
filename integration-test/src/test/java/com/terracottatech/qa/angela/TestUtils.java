package com.terracottatech.qa.angela;

import com.terracottatech.qa.angela.common.tcconfig.License;

import java.net.URL;

public class TestUtils {
  public static final URL LICENSE_RESOURCE = TestUtils.class.getResource("/terracotta/10/Terracotta101.xml");
  public static final License LICENSE = new License(LICENSE_RESOURCE);
}