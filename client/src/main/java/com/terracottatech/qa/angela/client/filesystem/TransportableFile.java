package com.terracottatech.qa.angela.client.filesystem;

public class TransportableFile {

  private final String name;
  private final byte[] content;

  TransportableFile(String name, byte[] content) {
    this.name = name;
    this.content = content;
  }

  public String getName() {
    return name;
  }

  public byte[] getContent() {
    return content;
  }
}
