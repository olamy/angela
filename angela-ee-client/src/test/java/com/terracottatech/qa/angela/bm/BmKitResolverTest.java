package com.terracottatech.qa.angela.bm;

import org.junit.Test;

import com.terracottatech.qa.angela.common.topology.Version;

import java.io.IOException;
import java.util.List;

/**
 * @author Aurelien Broszniowski
 */

public class BmKitResolverTest {

  @Test
  public void testGetAllGAFixVersions() throws IOException {
    Version version = new Version("4.3.4.6.7");
    final List<String> allGAFixVersions = new BmKitResolver().getAllGAFixVersions(version);
    for (String allGAFixVersion : allGAFixVersions) {
      System.out.println(allGAFixVersion);
    }
  }
}
