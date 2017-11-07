package com.terracottatech.qa.angela.tcconfig.holders;

import org.dom4j.Document;
import org.dom4j.Element;
import org.dom4j.Node;
import org.dom4j.io.SAXReader;

import java.io.InputStream;
import java.io.StringReader;
import java.util.List;

/**
 * Terracotta config for Terracotta 5.0
 * <p>
 * 10 -> 5.0
 *
 * @author Aurelien Broszniowski
 */
public class TcConfig10Holder extends TcConfigHolder {

  public TcConfig10Holder() {
  }

  public TcConfig10Holder(final InputStream tcConfigInputStream) {
    super(tcConfigInputStream);
  }

  @Override
  protected List<Node> getServersList(final Document tcConfigXml) {
    return tcConfigXml.selectNodes("//*[name()='servers']//*[name()='server']");
  }

  @Override
  public void updateDataDirectory(final String rootId, final String newlocation) {
    StringReader stringInputStream = new StringReader(this.tcConfigContent);
    try {
      SAXReader reader = new SAXReader();
      try {
        Document tcConfigXml = reader.read(stringInputStream);

        List<Node> serversList = getServersList(tcConfigXml);
        for (Node server : serversList) {
          Node datarootNode = server.selectSingleNode("//*[name()='data:directory']");

          if (datarootNode != null) {
            String id = ((Element)datarootNode).attributeValue("id");
            if (rootId.equalsIgnoreCase(id)) {
              datarootNode.setText(newlocation);
            }
          }
        }
        this.tcConfigContent = tcConfigXml.asXML();
      } finally {
        stringInputStream.close();
      }
    } catch (Exception e) {
      throw new RuntimeException("Cannot read tc-config xml input stream : " + stringInputStream.toString(), e);
    }
  }

  @Override
  public void updateHostname(final String serverName, final String hostname) {
    StringReader stringInputStream = new StringReader(this.tcConfigContent);
    try {
      SAXReader reader = new SAXReader();
      try {
        Document tcConfigXml = reader.read(stringInputStream);

        List<Node> serversList = getServersList(tcConfigXml);
        for (Node server : serversList) {
          String name = ((Element)server).attributeValue("name");
          if (name.equalsIgnoreCase(serverName)) {
            ((Element)server).addAttribute("host", hostname);
          }
        }
        this.tcConfigContent = tcConfigXml.asXML();
      } finally {
        stringInputStream.close();
      }
    } catch (Exception e) {
      throw new RuntimeException("Cannot read tc-config xml input stream : " + stringInputStream.toString(), e);
    }
  }

}
