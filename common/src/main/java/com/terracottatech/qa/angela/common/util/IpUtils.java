package com.terracottatech.qa.angela.common.util;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;

import static com.terracottatech.qa.angela.common.util.HostAndIpValidator.isValidIPv6;

public class IpUtils {
  public static String getHostName() {
    try {
      return InetAddress.getLocalHost().getHostName();
    } catch (UnknownHostException e) {
      throw new IllegalStateException(e);
    }
  }

  public static String getHostAddress(String host) {
    try {
      return InetAddress.getByName(host).getHostAddress();
    } catch (UnknownHostException e) {
      throw new IllegalStateException(e);
    }
  }

  public static String encloseInBracketsIfIpv6(String hostname) {
    if (hostname != null && isValidIPv6(hostname, false)) {
      return "[" + hostname + "]";
    }
    return hostname;
  }

  public static InetSocketAddress encloseInBracketsIfIpv6(InetSocketAddress address) {
    if (address != null && isValidIPv6(address.getHostName(), false)) {
      return InetSocketAddress.createUnresolved("[" + address.getHostName() + "]", address.getPort());
    }
    return address;
  }
}
