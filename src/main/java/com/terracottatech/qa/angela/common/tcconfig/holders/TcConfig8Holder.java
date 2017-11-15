package com.terracottatech.qa.angela.common.tcconfig.holders;

import org.dom4j.Document;
import org.dom4j.Node;

import java.io.InputStream;
import java.util.List;

/**
 * Terracotta config for Terracotta 4.0.x
 * <p>
 * 8   -> 4.0.x
 *
 * @author Aurelien Broszniowski
 */
public class TcConfig8Holder extends TcConfigHolder {

  public TcConfig8Holder() {
  }

  public TcConfig8Holder(final InputStream tcConfigInputStream) {
    super(tcConfigInputStream);
  }

  @Override
  protected List<Node> getServersList(final Document tcConfigXml) {
    return tcConfigXml.selectNodes("//*[name()='servers']//*[name()='server']");
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
