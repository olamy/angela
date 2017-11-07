package com.terracottatech.qa.angela.tcconfig.holders;

import org.dom4j.Document;
import org.dom4j.Node;

import java.io.InputStream;
import java.util.List;

/**
 * Terracotta config for Terracotta 4.1+
 * <p>
 * 9.0 -> 4.1.x
 * 9.1 -> 4.2.x
 * 9.2 -> 4.3.x
 *
 * @author Aurelien Broszniowski
 */
public class TcConfig9Holder extends TcConfigHolder {

  public TcConfig9Holder() {
  }

  public TcConfig9Holder(final InputStream tcConfigInputStream) {
    super(tcConfigInputStream);
  }

  @Override
  protected List<Node> getServersList(final Document tcConfigXml) {
    return tcConfigXml.selectNodes("//*[name()='servers']//*[name()='server']");
//    return tcConfigXml.selectNodes("//servers/server");
  }

  @Override
  public void updateDataDirectory(final String rootId, final String newlocation) {
    throw new UnsupportedOperationException("Unimplemented");
  }

  @Override
  public void updateHostname(final String serverName, final String hostname) {
    throw new UnsupportedOperationException("Unimplemented");
  }
}
