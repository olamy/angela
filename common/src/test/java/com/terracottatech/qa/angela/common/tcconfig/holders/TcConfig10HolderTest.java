package com.terracottatech.qa.angela.common.tcconfig.holders;

import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.InputStream;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.*;

/**
 * @author vmad
 */
public class TcConfig10HolderTest {

  @Test
  public void testUpdateSecurityRootDirectoryLocation() throws Exception {
    try (InputStream tcConfigStream = TcConfig10HolderTest.class.getResourceAsStream("/terracotta/10/tc-config10.xml")) {
      TcConfig10Holder tcConfig10Holder = new TcConfig10Holder(tcConfigStream);
      assertThat(tcConfig10Holder.getSecurityRootDirectory(), is("initial_path"));

      String NEW_PATH_VALUE = "new_path";
      tcConfig10Holder.updateSecurityRootDirectoryLocation(NEW_PATH_VALUE);
      assertThat(tcConfig10Holder.getSecurityRootDirectory(), is(NEW_PATH_VALUE));
    }
  }

  @Test
  public void testCreateTcPropertyWhenThereIsNoProperty() {
    String cfg =
        "<tc-config xmlns=\"http://www.terracotta.org/config\">\n" +
        "  <plugins>\n" +
        "    <service xmlns:sec=\"http://www.terracottatech.com/config/security\">\n" +
        "      <sec:security>\n" +
        "        <sec:security-root-directory>initial_path</sec:security-root-directory>\n" +
        "        <sec:ssl-tls/>\n" +
        "      </sec:security>\n" +
        "    </service>\n" +
        "  </plugins>\n" +
        "</tc-config>";
    TcConfig10Holder tcConfig10Holder = new TcConfig10Holder(new ByteArrayInputStream(cfg.getBytes()));
    tcConfig10Holder.createOrUpdateTcProperty("a", "new");
    tcConfig10Holder.createOrUpdateTcProperty("b", "new2");
    assertThat(tcConfig10Holder.tcConfigContent, is(
            "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"no\"?>" +
            "<tc-config xmlns=\"http://www.terracotta.org/config\">\n" +
            "  <plugins>\n" +
            "    <service xmlns:sec=\"http://www.terracottatech.com/config/security\">\n" +
            "      <sec:security>\n" +
            "        <sec:security-root-directory>initial_path</sec:security-root-directory>\n" +
            "        <sec:ssl-tls/>\n" +
            "      </sec:security>\n" +
            "    </service>\n" +
            "  </plugins>\n" +
            "  <tc-properties>\n" +
            "    <property name=\"a\" value=\"new\"/>\n" +
            "    <property name=\"b\" value=\"new2\"/>\n" +
            "  </tc-properties>\n" +
            "</tc-config>"
    ));
  }

  @Test
  public void testCreateTcPropertyWhenThereIsAtLeastOneProperty() {
    String cfg =
        "<tc-config xmlns=\"http://www.terracotta.org/config\">\n" +
        "  <plugins>\n" +
        "    <service xmlns:sec=\"http://www.terracottatech.com/config/security\">\n" +
        "      <sec:security>\n" +
        "        <sec:security-root-directory>initial_path</sec:security-root-directory>\n" +
        "        <sec:ssl-tls/>\n" +
        "      </sec:security>\n" +
        "    </service>\n" +
        "  </plugins>\n" +
        "  <tc-properties>\n" +
        "    <property name=\"z\" value=\"unrelated\"/>\n" +
        "  </tc-properties>\n" +
        "</tc-config>";
    TcConfig10Holder tcConfig10Holder = new TcConfig10Holder(new ByteArrayInputStream(cfg.getBytes()));
    tcConfig10Holder.createOrUpdateTcProperty("a", "new");
    tcConfig10Holder.createOrUpdateTcProperty("b", "new2");
    assertThat(tcConfig10Holder.tcConfigContent, is(
            "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"no\"?>" +
            "<tc-config xmlns=\"http://www.terracotta.org/config\">\n" +
            "  <plugins>\n" +
            "    <service xmlns:sec=\"http://www.terracottatech.com/config/security\">\n" +
            "      <sec:security>\n" +
            "        <sec:security-root-directory>initial_path</sec:security-root-directory>\n" +
            "        <sec:ssl-tls/>\n" +
            "      </sec:security>\n" +
            "    </service>\n" +
            "  </plugins>\n" +
            "  <tc-properties>\n" +
            "    <property name=\"z\" value=\"unrelated\"/>\n" +
            "    <property name=\"a\" value=\"new\"/>\n" +
            "    <property name=\"b\" value=\"new2\"/>\n" +
            "  </tc-properties>\n" +
            "</tc-config>"
    ));
  }

  @Test
  public void testUpdateTcProperty() {
    String cfg =
        "<tc-config xmlns=\"http://www.terracotta.org/config\">\n" +
        "  <plugins>\n" +
        "    <service xmlns:sec=\"http://www.terracottatech.com/config/security\">\n" +
        "      <sec:security>\n" +
        "        <sec:security-root-directory>initial_path</sec:security-root-directory>\n" +
        "        <sec:ssl-tls/>\n" +
        "      </sec:security>\n" +
        "    </service>\n" +
        "  </plugins>\n" +
        "  <tc-properties>\n" +
        "    <property name=\"a\" value=\"old\"/>\n" +
        "  </tc-properties>\n" +
        "</tc-config>";
    TcConfig10Holder tcConfig10Holder = new TcConfig10Holder(new ByteArrayInputStream(cfg.getBytes()));
    tcConfig10Holder.createOrUpdateTcProperty("a", "new");
    tcConfig10Holder.createOrUpdateTcProperty("b", "new2");
    assertThat(tcConfig10Holder.tcConfigContent, is(
        "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"no\"?>" +
            "<tc-config xmlns=\"http://www.terracotta.org/config\">\n" +
            "  <plugins>\n" +
            "    <service xmlns:sec=\"http://www.terracottatech.com/config/security\">\n" +
            "      <sec:security>\n" +
            "        <sec:security-root-directory>initial_path</sec:security-root-directory>\n" +
            "        <sec:ssl-tls/>\n" +
            "      </sec:security>\n" +
            "    </service>\n" +
            "  </plugins>\n" +
            "  <tc-properties>\n" +
            "    <property name=\"a\" value=\"new\"/>\n" +
            "    <property name=\"b\" value=\"new2\"/>\n" +
            "  </tc-properties>\n" +
            "</tc-config>"
    ));
  }
}