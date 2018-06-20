package com.terracottatech.qa.angela.common.util;

import org.hamcrest.core.IsNull;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;

public class JavaLocationResolverTest {

  @Test(expected = NullPointerException.class)
  public void testNullResourceThrows() {
    new JavaLocationResolver(null);
  }

  @Test
  public void testResolveAll() {
    JavaLocationResolver javaLocationResolver = new JavaLocationResolver(getClass().getResource("/toolchains/toolchains.xml"));
    List<JDK> jdks = javaLocationResolver.resolveJavaLocation(null, null);
    assertThat(jdks.size(), is(6));
    assertThat(jdks.get(0).getVendor(), is("sun"));
    assertThat(jdks.get(0).getVersion(), is("1.6"));
    assertThat(jdks.get(1).getVendor(), is(IsNull.nullValue()));
    assertThat(jdks.get(1).getVersion(), is("1.7"));
    assertThat(jdks.get(2).getVendor(), is("openjdk"));
    assertThat(jdks.get(2).getVersion(), is("1.8"));
    assertThat(jdks.get(3).getVendor(), is("ibm"));
    assertThat(jdks.get(3).getVersion(), is("1.8"));
    assertThat(jdks.get(4).getVendor(), is("sun"));
    assertThat(jdks.get(4).getVersion(), is("1.8"));
    assertThat(jdks.get(5).getVendor(), is("Oracle Corporation"));
    assertThat(jdks.get(5).getVersion(), is("1.8"));
  }

  @Test
  public void testResolveOne() {
    JavaLocationResolver javaLocationResolver = new JavaLocationResolver(getClass().getResource("/toolchains/toolchains.xml"));
    List<JDK> jdks = javaLocationResolver.resolveJavaLocation("1.6", Collections.singleton("sun"));
    assertThat(jdks.size(), is(1));
    assertThat(jdks.get(0).getVendor(), is("sun"));
    assertThat(jdks.get(0).getVersion(), is("1.6"));
  }

  @Test
  public void testResolveManyOfaVersion() {
    JavaLocationResolver javaLocationResolver = new JavaLocationResolver(getClass().getResource("/toolchains/toolchains.xml"));
    List<JDK> jdks = javaLocationResolver.resolveJavaLocation("1.8", new HashSet<>(Arrays.asList("Oracle Corporation", "openjdk")));
    assertThat(jdks.size(), is(2));
    assertThat(jdks.get(0).getVendor(), is("openjdk"));
    assertThat(jdks.get(0).getVersion(), is("1.8"));
    assertThat(jdks.get(1).getVendor(), is("Oracle Corporation"));
    assertThat(jdks.get(1).getVersion(), is("1.8"));
  }

  @Test
  public void testResolveAllOfaVersion() {
    JavaLocationResolver javaLocationResolver = new JavaLocationResolver(getClass().getResource("/toolchains/toolchains.xml"));
    List<JDK> jdks = javaLocationResolver.resolveJavaLocation("1.8", new HashSet<>(Arrays.asList("Oracle Corporation", "sun", "openjdk")));
    assertThat(jdks.size(), is(3));
    assertThat(jdks.get(0).getVendor(), is("openjdk"));
    assertThat(jdks.get(0).getVersion(), is("1.8"));
    assertThat(jdks.get(1).getVendor(), is("sun"));
    assertThat(jdks.get(1).getVersion(), is("1.8"));
    assertThat(jdks.get(2).getVendor(), is("Oracle Corporation"));
    assertThat(jdks.get(2).getVersion(), is("1.8"));
  }
}
