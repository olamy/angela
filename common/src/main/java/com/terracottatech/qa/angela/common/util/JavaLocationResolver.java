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

  public List<JDK> resolveJavaLocation() {
    return resolveJavaLocation(null, null);
  }

  public List<JDK> resolveJavaLocation(String version) {
    return resolveJavaLocation(version, null);
  }

  public List<JDK> resolveJavaLocation(String version, Vendor vendor) {
    List<JDK> list = jdks.stream()
        .filter(JDK::isValid)
        .filter(jdk -> version == null || version.equals(jdk.getVersion()))
        .filter(jdk -> vendor == null || vendor.getName().equalsIgnoreCase(jdk.getVendor()))
        .collect(Collectors.toList());
    if (list.isEmpty()) {
      String message = "Missing JDK with version [" + version + "]";
      if (vendor != null) {
        message += " and vendor [" + vendor.getName() + "]";
      }
      message += " config in toolchains.xml. Available JDKs: " + jdks;
      throw new RuntimeException(message);
    }
    return list;
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
        boolean valid = new File(home).isDirectory();

        String version = textContentOf(providesElement, "version");
        String vendor = textContentOf(providesElement, "vendor");

        jdks.add(new JDK(home, version, vendor, valid));
      }

      return jdks;
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  private static String textContentOf(Element element, String subElementName) {
    NodeList nodeList = element.getElementsByTagName(subElementName);
    if (nodeList.getLength() > 0) {
      return nodeList.item(0).getTextContent();
    }
    return null;
  }

}
