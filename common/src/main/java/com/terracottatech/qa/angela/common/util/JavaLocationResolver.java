package com.terracottatech.qa.angela.common.util;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Find current java location
 *
 * @author Aurelien Broszniowski
 */

public class JavaLocationResolver {

  public enum Vendor {
    SUN("sun"),
    ORACLE("Oracle Corporation"),
    OPENJDK("openjdk"),
    IBM("ibm"),
    ;

    private final String name;

    Vendor(String name) {
      this.name = name;
    }

    public String getName() {
      return name;
    }
  }

  private final List<JDK> jdks;

  public JavaLocationResolver() {
    jdks = findJDKs();
  }

  public List<JDK> resolveJavaLocation(String version) {
    return resolveJavaLocation(version, null);
  }

  public List<JDK> resolveJavaLocation(String version, Vendor vendor) {
    return jdks.stream()
        .filter(jdk -> jdk.getVersion().equals(version))
        .filter(jdk -> vendor == null || jdk.getVendor().equals(vendor.getName()))
        .collect(Collectors.toList());
  }

  private static List<JDK> findJDKs() {
    List<JDK> jdks = findJDKs(System.getProperty("user.home") + File.separator + ".m2" + File.separator + "toolchains.xml");
    if (jdks.isEmpty()) {
      jdks = findJDKs("/data/jenkins-slave" + File.separator + ".m2" + File.separator + "toolchains.xml");
    }
    return jdks;
  }

  private static List<JDK> findJDKs(String toolchainsLocation) {
    try {
      List<JDK> jdks = new ArrayList<>();

      DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
      DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
      Document doc = dBuilder.parse(new File(toolchainsLocation));
      doc.getDocumentElement().normalize();


      NodeList toolchainList = doc.getElementsByTagName("toolchain");
      for (int i = 0; i < toolchainList.getLength(); i++) {
        Element toolchainElement = (Element) toolchainList.item(i);

        Element providesElement = (Element) toolchainElement.getElementsByTagName("provides").item(0);
        Element configurationElement = (Element) toolchainElement.getElementsByTagName("configuration").item(0);

        String home = configurationElement.getElementsByTagName("jdkHome").item(0).getTextContent();
        if (!new File(home).isDirectory()) {
          continue;
        }

        String version = providesElement.getElementsByTagName("version").item(0).getTextContent();
        String vendor = providesElement.getElementsByTagName("vendor").item(0).getTextContent();

        jdks.add(new JDK(home, version, vendor));
      }

      return jdks;
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }
}
