package com.terracottatech.qa.angela.client.filesystem;

import com.terracottatech.qa.angela.agent.Agent;
import com.terracottatech.qa.angela.client.IgniteHelper;
import org.apache.commons.io.IOUtils;
import org.apache.ignite.Ignite;
import org.apache.ignite.IgniteException;
import org.apache.ignite.cluster.ClusterGroup;
import org.apache.ignite.lang.IgniteClosure;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static java.util.stream.Collectors.toList;

public class RemoteFolder extends RemoteFile {
  public RemoteFolder(Ignite ignite, String nodeName, String parentName, String name) {
    super(ignite, nodeName, parentName, name);
  }

  public List<RemoteFile> list() {
    IgniteHelper.checkAgentHealth(ignite, nodeName);
    ClusterGroup location = ignite.cluster().forAttribute("nodename", nodeName);
    List<String> remoteFiles = ignite.compute(location).applyAsync((IgniteClosure<String, List<String>>) aName -> Agent.CONTROLLER.listFiles(aName), getAbsoluteName()).get();
    List<String> remoteFolders = ignite.compute(location).applyAsync((IgniteClosure<String, List<String>>) aName -> Agent.CONTROLLER.listFolders(aName), getAbsoluteName()).get();

    List<RemoteFile> result = new ArrayList<>();
    result.addAll(remoteFiles.stream().map(s -> new RemoteFile(ignite, nodeName, getAbsoluteName(), s)).collect(toList()));
    result.addAll(remoteFolders.stream().map(s -> new RemoteFolder(ignite, nodeName, getAbsoluteName(), s)).collect(toList()));
    return result;
  }

  @Override
  public void downloadTo(File path) throws IOException {
    IgniteHelper.checkAgentHealth(ignite, nodeName);
    ClusterGroup location = ignite.cluster().forAttribute("nodename", nodeName);
    byte[] bytes;
    try {
      bytes = ignite.compute(location).applyAsync((IgniteClosure<String, byte[]>) aName -> Agent.CONTROLLER.downloadFolder(aName), getAbsoluteName()).get();
    } catch (IgniteException e) {
      throw new IOException(e.getMessage(), e);
    }

    path.mkdirs();
    if (!path.isDirectory()) {
      throw new IllegalArgumentException("Destination path '" + path + "' is not a folder or could not be created");
    }

    try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(bytes))) {
      while (true) {
        ZipEntry nextEntry = zis.getNextEntry();
        if (nextEntry == null) {
          break;
        }
        String name = nextEntry.getName();
        File file = new File(path, name);
        file.getParentFile().mkdirs();
        try (FileOutputStream fos = new FileOutputStream(file)) {
          IOUtils.copy(zis, fos);
        }
      }
    }
  }

}
