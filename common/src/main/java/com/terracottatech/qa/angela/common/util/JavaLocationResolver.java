package com.terracottatech.qa.angela.common.util;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

/**
 * Find current java location
 *
 * @author Aurelien Broszniowski
 */

public class JavaLocationResolver {

  public List<String> resolveJava8Location() {
    List<String> j8Homes = getJavaHome("1.8", System.getProperty("user.home") + File.separator + ".m2" + File.separator + "toolchains.xml");
    if (j8Homes.size() == 0) {
      j8Homes = getJavaHome("1.8", "/data/jenkins-slave" + File.separator + ".m2" + File.separator + "toolchains.xml");
    }
    if (j8Homes.size() == 0) {
      j8Homes.add(System.getProperty("java.home"));
    }
    return j8Homes;
  }

  private List<String> getJavaHome(String version, String toolchainsLocation) {
    List<String > locations = new ArrayList<String>();
    try {
      File fXmlFile = new File(toolchainsLocation);
      DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
      DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
      Document doc = dBuilder.parse(fXmlFile);
      doc.getDocumentElement().normalize();


      NodeList nList = doc.getElementsByTagName("toolchain");
      for (int temp = 0; temp < nList.getLength(); temp++) {
        Node nNode = nList.item(temp);

        if (nNode.getNodeType() == Node.ELEMENT_NODE) {
          Element eElement = (Element)nNode;
          NodeList providesElement = eElement.getElementsByTagName("provides");
          for (int i=0; i< providesElement.getLength(); i++) {
            NodeList childNodes = providesElement.item(i).getChildNodes();
            for (int j=0; j< childNodes.getLength(); j++) {
              if ("version".equalsIgnoreCase(childNodes.item(j).getNodeName()) &&
                  version.equalsIgnoreCase(childNodes.item(j).getTextContent())) {
                String jdkHome = eElement.getElementsByTagName("jdkHome").item(0).getTextContent();
                File jdkHomeFile = new File(jdkHome);
                if (jdkHomeFile.exists()) {
                  locations.add(jdkHome);
                }
              }
            }
          }
        }
      }
    } catch (Exception e) {
      return locations;
    }
    return locations;
  }
}
