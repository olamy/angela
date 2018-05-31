package com.terracottatech.qa.angela.common.util;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.File;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Find current java location
 *
 * @author Aurelien Broszniowski
 */

public class JavaLocationResolver {

  public static final EnumSet<Vendor> DEFAULT_ALLOWED_VENDORS = EnumSet.of(Vendor.ORACLE, Vendor.SUN, Vendor.OPENJDK);

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
  private final EnumSet<Vendor> allowedVendors;

  public JavaLocationResolver(URL url, EnumSet<Vendor> allowedVendors) {
    this.allowedVendors = allowedVendors;
    Objects.requireNonNull(url, "Toolchains URL must not be null");
    jdks = findJDKs(url);
  }

  public JavaLocationResolver() {
    this(DEFAULT_ALLOWED_VENDORS);
  }

  public JavaLocationResolver(EnumSet<Vendor> allowedVendors) {
    this.allowedVendors = allowedVendors;
    jdks = findJDKs();
  }

   List<JDK> resolveJavaLocation() {
    return resolveJavaLocation(null, allowedVendors);
  }

  public List<JDK> resolveJavaLocation(String version) {
    return resolveJavaLocation(version, allowedVendors);
  }

   List<JDK> resolveJavaLocation(String version, EnumSet<Vendor> vendors) {
    List<JDK> list = jdks.stream()
        .filter(JDK::isValid)
        .filter(jdk -> version == null || version.equals(jdk.getVersion()))
        .filter(jdk -> {
          if (vendors == null) {
            return true;
          }
          for (Vendor vendor : vendors) {
            if (vendor.getName().equalsIgnoreCase(jdk.getVendor())) {
              return true;
            }
          }
          return false;
        })
        .collect(Collectors.toList());
    if (list.isEmpty()) {
      String message = "Missing JDK with version [" + version + "]";
      if (vendors != null) {
        message += " and one vendor in [" + vendors + "]";
      }
      message += " config in toolchains.xml. Available JDKs: " + jdks;
      throw new RuntimeException(message);
    }
    return list;
  }

  private static List<JDK> findJDKs() {
    try {
      List<JDK> jdks = findJDKs(new File(System.getProperty("user.home") + File.separator + ".m2" + File.separator + "toolchains.xml").toURI().toURL());
      if (jdks.isEmpty()) {
        jdks = findJDKs(new URL("file:///data/jenkins-slave" + File.separator + ".m2" + File.separator + "toolchains.xml"));
      }
      return jdks;
    } catch (MalformedURLException e) {
      throw new RuntimeException(e);
    }
  }

  private static List<JDK> findJDKs(URL toolchainsLocation) {
    try (InputStream is = toolchainsLocation.openStream()) {
      List<JDK> jdks = new ArrayList<>();

      DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
      DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
      Document doc = dBuilder.parse(is);
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
