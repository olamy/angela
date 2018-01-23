package com.terracottatech.qa.angela.client.filesystem;

import com.terracottatech.qa.angela.agent.Agent;
import org.apache.ignite.Ignite;
import org.apache.ignite.cluster.ClusterGroup;
import org.apache.ignite.lang.IgniteClosure;

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
    ClusterGroup location = ignite.cluster().forAttribute("nodename", nodeName);
    byte[] bytes = ignite.compute(location).applyAsync((IgniteClosure<String, byte[]>) aName -> Agent.CONTROLLER.downloadFile(aName), getAbsoluteName()).get();
    try (FileOutputStream fos = new FileOutputStream(path)) {
      fos.write(bytes);
    }
  }

}
