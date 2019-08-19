package com.terracottatech.qa.angela.agent.kit;

import org.junit.Test;

import com.terracottatech.qa.angela.common.topology.PackageType;
import com.terracottatech.qa.angela.common.distribution.Distribution;
import com.terracottatech.qa.angela.common.topology.Version;

import java.util.HashMap;
import java.util.Map;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * @author Aurelien Broszniowski
 */

public class KitManagerTest {

  @Test
  public void testSagVersions() {
    Map<String, String> sagInstallers = new HashMap<String, String>() {{
      put("4.3.0", "SoftwareAGInstaller98_LATEST.jar");
      put("4.3.1", "SoftwareAGInstaller99_LATEST.jar");
      put("4.3.2", "SoftwareAGInstaller910_LATEST.jar");
      put("4.3.3", "SoftwareAGInstaller912_LATEST.jar");
      put("4.3.4", "SoftwareAGInstaller101_LATEST.jar");
      put("4.3.5", "SoftwareAGInstaller102_LATEST.jar");
      put("4.3.6", "SoftwareAGInstaller103_LATEST.jar");
      put("4.3.7", "SoftwareAGInstaller104_LATEST.jar");

      put("10.1.0", "SoftwareAGInstaller101_LATEST.jar");
      put("10.2.0", "SoftwareAGInstaller102_LATEST.jar");
      put("10.3.0", "SoftwareAGInstaller103_LATEST.jar");
      put("10.3.1", "SoftwareAGInstaller104_LATEST.jar");
      put("10.5.0", "SoftwareAGInstaller105_LATEST.jar");
    }};


    for (String testedVersion : sagInstallers.keySet()) {
      final String[] testedVersionNumbers = testedVersion.split("\\.");

      Distribution distribution = mock(Distribution.class);
      Version version = mock(Version.class);
      when(distribution.getVersion()).thenReturn(version);
      when(distribution.getPackageType()).thenReturn(PackageType.SAG_INSTALLER);
      when(version.getVersion(false)).thenReturn(testedVersion);
      when(version.getMajor()).thenReturn(Integer.parseInt(testedVersionNumbers[0]));
      when(version.getMinor()).thenReturn(Integer.parseInt(testedVersionNumbers[1]));
      when(version.getRevision()).thenReturn(Integer.parseInt(testedVersionNumbers[2]));

      LocalKitManager localKitManager = new LocalKitManager(distribution);
      String sagInstallerName = localKitManager.getSAGInstallerName(version);
      assertThat(sagInstallerName, is(equalTo(sagInstallers.get(testedVersion))));
    }
  }
}
