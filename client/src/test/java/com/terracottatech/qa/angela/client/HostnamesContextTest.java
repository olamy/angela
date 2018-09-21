package com.terracottatech.qa.angela.client;

import com.terracottatech.qa.angela.common.topology.LicenseType;
import com.terracottatech.qa.angela.common.topology.PackageType;
import com.terracottatech.qa.angela.common.topology.Topology;
import com.terracottatech.qa.angela.test.Versions;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static com.terracottatech.qa.angela.common.distribution.Distribution.distribution;
import static com.terracottatech.qa.angela.common.tcconfig.TcConfig.tcConfig;
import static com.terracottatech.qa.angela.common.topology.Version.version;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;


public class HostnamesContextTest {

  private HostnamesContext hostnamesContext;

  @Before
  public void setUp() {
    System.setProperty("tc.qa.angela.hostnames", "server1.eur.ad.sag,server2.eur.ad.sag");
    hostnamesContext = new HostnamesContext();
  }

  @After
  public void tearDown() {
    System.clearProperty("tc.qa.angela.hostnames");
    hostnamesContext = null;
  }

  @Test
  public void testHostInjection10x() {
    Topology topology = new Topology(distribution(version(Versions.TERRACOTTA_VERSION), PackageType.KIT, LicenseType.TC_DB),
        tcConfig(version(Versions.TERRACOTTA_VERSION), getClass().getResource("/terracotta/10/tc-config.xml")));
    hostnamesContext.injectHostnames(topology);
    assertEquals(hostnamesContext.getInjectedHostName("localhost"), "server1.eur.ad.sag");
  }

  @Test
  public void testHostInjection10xMultipleInvocation() {
    Topology topology = new Topology(distribution(version(Versions.TERRACOTTA_VERSION), PackageType.KIT, LicenseType.TC_DB),
        tcConfig(version(Versions.TERRACOTTA_VERSION), getClass().getResource("/terracotta/10/tc-config.xml")));
    hostnamesContext.injectHostnames(topology);
    hostnamesContext.injectHostnames(topology);
    assertEquals(hostnamesContext.getInjectedHostName("localhost"), "server1.eur.ad.sag");
  }

  @Test
  public void testHostInjection10xDifferentHosts() {
    Topology topology = new Topology(distribution(version(Versions.TERRACOTTA_VERSION), PackageType.KIT, LicenseType.TC_DB),
        tcConfig(version(Versions.TERRACOTTA_VERSION), getClass().getResource("/terracotta/10/tc-config-host.xml")));
    hostnamesContext.injectHostnames(topology);
    assertEquals(hostnamesContext.getInjectedHostName("test1.eur.ad.sag"), "server1.eur.ad.sag");
    assertEquals(hostnamesContext.getInjectedHostName("test2.eur.ad.sag"), "server2.eur.ad.sag");
  }

  @Test
  public void testHostInjection10xBig() {
    Topology topology = new Topology(distribution(version(Versions.TERRACOTTA_VERSION), PackageType.KIT, LicenseType.TC_DB),
        tcConfig(version(Versions.TERRACOTTA_VERSION), getClass().getResource("/terracotta/10/tc-config-big.xml")));

    try {
      hostnamesContext.injectHostnames(topology);
    } catch (IllegalArgumentException e) {
      assertEquals(e.getMessage(), "'tc.qa.angela.hostnames' system property is not having sufficient hostnames.");
      return;
    }

    fail();
  }

  @Test
  public void testHostInjection4x() {
    Topology topology = new Topology(distribution(version(Versions.TERRACOTTA_VERSION_4X), PackageType.KIT, LicenseType.MAX),
        tcConfig(version(Versions.TERRACOTTA_VERSION_4X), getClass().getResource("/terracotta/4/tc-config.xml")));
    hostnamesContext.injectHostnames(topology);
    assertEquals(hostnamesContext.getInjectedHostName("localhost"), "server1.eur.ad.sag");
  }

  @Test
  public void testHostInjection4xMultipleInvocation() {
    Topology topology = new Topology(distribution(version(Versions.TERRACOTTA_VERSION_4X), PackageType.KIT, LicenseType.MAX),
        tcConfig(version(Versions.TERRACOTTA_VERSION_4X), getClass().getResource("/terracotta/4/tc-config.xml")));
    hostnamesContext.injectHostnames(topology);
    hostnamesContext.injectHostnames(topology);
    assertEquals(hostnamesContext.getInjectedHostName("localhost"), "server1.eur.ad.sag");
  }

  @Test
  public void testHostInjection4xDifferentHosts() {
    Topology topology = new Topology(distribution(version(Versions.TERRACOTTA_VERSION_4X), PackageType.KIT, LicenseType.MAX),
        tcConfig(version(Versions.TERRACOTTA_VERSION_4X), getClass().getResource("/terracotta/4/tc-config-host.xml")));
    hostnamesContext.injectHostnames(topology);
    assertEquals(hostnamesContext.getInjectedHostName("test1.eur.ad.sag"), "server1.eur.ad.sag");
    assertEquals(hostnamesContext.getInjectedHostName("test2.eur.ad.sag"), "server2.eur.ad.sag");
  }

  @Test
  public void testHostInjection4xBig() {
    Topology topology = new Topology(distribution(version(Versions.TERRACOTTA_VERSION_4X), PackageType.KIT, LicenseType.MAX),
        tcConfig(version(Versions.TERRACOTTA_VERSION_4X), getClass().getResource("/terracotta/4/tc-config-big.xml")));

    try {
      hostnamesContext.injectHostnames(topology);
    } catch (IllegalArgumentException e) {
      assertEquals(e.getMessage(), "'tc.qa.angela.hostnames' system property is not having sufficient hostnames.");
      return;
    }

    fail();
  }
}
