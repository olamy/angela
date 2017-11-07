package com.terracottatech.qa.angela;

import org.apache.ignite.Ignite;
import org.apache.ignite.Ignition;
import org.apache.ignite.configuration.IgniteConfiguration;
import org.apache.ignite.spi.discovery.tcp.TcpDiscoverySpi;
import org.apache.ignite.spi.discovery.tcp.ipfinder.vm.TcpDiscoveryVmIpFinder;
import org.junit.Test;

import java.util.Arrays;

/**
 * @author Aurelien Broszniowski
 */

public class InstallTest {


  @Test
  public void testInit() throws Exception {
    TsaControl tsaControl1 = new TsaControl();
    tsaControl1.init("1");

    TsaControl tsaControl2 = new TsaControl();
    tsaControl2.init("2");
    TsaControl tsaControl3 = new TsaControl();
    tsaControl3.init("1");

    tsaControl1.close();
    tsaControl2.close();
    tsaControl3.close();
  }
}
