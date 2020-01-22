package com.terracottatech.qa.angela.client.filesystem;

import com.terracottatech.qa.angela.agent.Agent;
import com.terracottatech.qa.angela.client.util.IgniteClientHelper;
import org.apache.ignite.Ignite;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

public class RemoteFile {
  protected final Ignite ignite;
  protected final String nodeName;
  protected final String parentName;
  protected final String name;

  public RemoteFile(Ignite ignite, String nodeName, String parentName, String name) {
    this.ignite = ignite;
    this.nodeName = nodeName;
    this.parentName = parentName;
    this.name = name;
  }

  public String getName() {
    return name;
  }

  public String getAbsoluteName() {
    if (parentName == null) {
      return name;
    }
    return parentName + "/" + name;
  }

  public boolean isFolder() {
    return this instanceof RemoteFolder;
  }

  public void downloadTo(File path) throws IOException {
    byte[] bytes = downloadContents();
    try (FileOutputStream fos = new FileOutputStream(path)) {
      fos.write(bytes);
    }
  }

  private byte[] downloadContents() {
    String filename = getAbsoluteName();
    return IgniteClientHelper.executeRemotely(ignite, nodeName, () -> Agent.controller.downloadFile(filename));
  }

  public TransportableFile toTransportableFile() {
    return new TransportableFile(getName(), downloadContents());
  }

  @Override
  public String toString() {
    return "[" + nodeName + "]:" + name;
  }
}
