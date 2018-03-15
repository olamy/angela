package com.terracottatech.qa.angela.common.util;

import org.hamcrest.core.IsNull;
import org.junit.Test;

import java.util.EnumSet;
import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;

public class JavaLocationResolverTest {

  @Test(expected = NullPointerException.class)
  public void testNullResourceThrows() {
    new JavaLocationResolver(null, JavaLocationResolver.DEFAULT_ALLOWED_VENDORS);
  }

  @Test
  public void testResolveAll() {
    JavaLocationResolver javaLocationResolver = new JavaLocationResolver(getClass().getResource("/toolchains/toolchains.xml"), null);
    List<JDK> jdks = javaLocationResolver.resolveJavaLocation();
    assertThat(jdks.size(), is(6));
    assertThat(jdks.get(0).getVendor(), is(JavaLocationResolver.Vendor.SUN.getName()));
    assertThat(jdks.get(0).getVersion(), is("1.6"));
    assertThat(jdks.get(1).getVendor(), is(IsNull.nullValue()));
    assertThat(jdks.get(1).getVersion(), is("1.7"));
    assertThat(jdks.get(2).getVendor(), is(JavaLocationResolver.Vendor.OPENJDK.getName()));
    assertThat(jdks.get(2).getVersion(), is("1.8"));
    assertThat(jdks.get(3).getVendor(), is(JavaLocationResolver.Vendor.IBM.getName()));
    assertThat(jdks.get(3).getVersion(), is("1.8"));
    assertThat(jdks.get(4).getVendor(), is(JavaLocationResolver.Vendor.SUN.getName()));
    assertThat(jdks.get(4).getVersion(), is("1.8"));
    assertThat(jdks.get(5).getVendor(), is(JavaLocationResolver.Vendor.ORACLE.getName()));
    assertThat(jdks.get(5).getVersion(), is("1.8"));
  }

  @Test
  public void testResolveOne() {
    JavaLocationResolver javaLocationResolver = new JavaLocationResolver(getClass().getResource("/toolchains/toolchains.xml"), JavaLocationResolver.DEFAULT_ALLOWED_VENDORS);
    List<JDK> jdks = javaLocationResolver.resolveJavaLocation("1.6", EnumSet.of(JavaLocationResolver.Vendor.SUN));
    assertThat(jdks.size(), is(1));
    assertThat(jdks.get(0).getVendor(), is(JavaLocationResolver.Vendor.SUN.getName()));
    assertThat(jdks.get(0).getVersion(), is("1.6"));
  }

  @Test
  public void testResolveManyOfaVersion() {
    JavaLocationResolver javaLocationResolver = new JavaLocationResolver(getClass().getResource("/toolchains/toolchains.xml"), JavaLocationResolver.DEFAULT_ALLOWED_VENDORS);
    List<JDK> jdks = javaLocationResolver.resolveJavaLocation("1.8", EnumSet.of(JavaLocationResolver.Vendor.ORACLE, JavaLocationResolver.Vendor.OPENJDK));
    assertThat(jdks.size(), is(2));
    assertThat(jdks.get(0).getVendor(), is(JavaLocationResolver.Vendor.OPENJDK.getName()));
    assertThat(jdks.get(0).getVersion(), is("1.8"));
    assertThat(jdks.get(1).getVendor(), is(JavaLocationResolver.Vendor.ORACLE.getName()));
    assertThat(jdks.get(1).getVersion(), is("1.8"));
  }

  @Test
  public void testResolveAllOfaVersion() {
    JavaLocationResolver javaLocationResolver = new JavaLocationResolver(getClass().getResource("/toolchains/toolchains.xml"), JavaLocationResolver.DEFAULT_ALLOWED_VENDORS);
    List<JDK> jdks = javaLocationResolver.resolveJavaLocation("1.8");
    assertThat(jdks.size(), is(3));
    assertThat(jdks.get(0).getVendor(), is(JavaLocationResolver.Vendor.OPENJDK.getName()));
    assertThat(jdks.get(0).getVersion(), is("1.8"));
    assertThat(jdks.get(1).getVendor(), is(JavaLocationResolver.Vendor.SUN.getName()));
    assertThat(jdks.get(1).getVersion(), is("1.8"));
    assertThat(jdks.get(2).getVendor(), is(JavaLocationResolver.Vendor.ORACLE.getName()));
    assertThat(jdks.get(2).getVersion(), is("1.8"));
  }
}
