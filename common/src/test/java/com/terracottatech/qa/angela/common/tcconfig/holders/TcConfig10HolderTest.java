package com.terracottatech.qa.angela.common.tcconfig.holders;

import org.junit.Test;

import java.io.InputStream;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.*;

/**
 * @author vmad
 */
public class TcConfig10HolderTest {

  @Test
  public void testUpdateSecurityRootDirectoryLocation() {
    final InputStream tcConfigStream = TcConfig10HolderTest.class.getResourceAsStream("/terracotta/10/tc-config10.xml");
    TcConfig10Holder tcConfig10Holder = new TcConfig10Holder(tcConfigStream);
    assertThat(tcConfig10Holder.getSecurityRootDirectory(), is("initial_path"));

    String NEW_PATH_VALUE = "new_path";
    tcConfig10Holder.updateSecurityRootDirectoryLocation(NEW_PATH_VALUE);
    assertThat(tcConfig10Holder.getSecurityRootDirectory(), is(NEW_PATH_VALUE));
  }
}