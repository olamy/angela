/*
 * The contents of this file are subject to the Terracotta Public License Version
 * 2.0 (the "License"); You may not use this file except in compliance with the
 * License. You may obtain a copy of the License at
 *
 * http://terracotta.org/legal/terracotta-public-license.
 *
 * Software distributed under the License is distributed on an "AS IS" basis,
 * WITHOUT WARRANTY OF ANY KIND, either express or implied. See the License for
 * the specific language governing rights and limitations under the License.
 *
 * The Covered Software is Angela.
 *
 * The Initial Developer of the Covered Software is
 * Terracotta, Inc., a Software AG company
 */

package org.terracotta.angela.common;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.terracotta.angela.common.AngelaProperties.JAVA_BUILD_VENDOR;
import static org.terracotta.angela.common.AngelaProperties.JDK_SECURITY_ALLOW_NON_CA_ANCHOR;

/**
 * Instances of this class are immutable.
 */
public class TerracottaCommandLineEnvironment {
  /*
    Implementor note: Please keep this class immutable!
   */

  /**
   * @deprecated Use {@link TerracottaCommandLineEnvironment#DEFAULT} instead.
   */
  @Deprecated
  public static final Set<String> DEFAULT_ALLOWED_JDK_VENDORS = Collections.unmodifiableSet(new HashSet<>(Collections.singletonList(JAVA_BUILD_VENDOR.getValue())));

  /**
   * @deprecated Use {@link TerracottaCommandLineEnvironment#DEFAULT} instead.
   */
  @Deprecated
  public static final String DEFAULT_JDK_VERSION = "1.8";

 public static final TerracottaCommandLineEnvironment DEFAULT = new TerracottaCommandLineEnvironment(DEFAULT_JDK_VERSION, DEFAULT_ALLOWED_JDK_VENDORS,
      Arrays.asList(JDK_SECURITY_ALLOW_NON_CA_ANCHOR.getJvmFormattedProperty()));
  private final String javaVersion;
  private final Set<String> javaVendors;
  private final List<String> javaOpts;

  /**
   * Create a new instance that contains whatever is necessary to build a JVM command line, minus classpath and main class.
   * @param javaVersion the java version specified in toolchains.xml, can be null if any version will fit.
   * @param javaVendors a set of acceptable java vendors specified in toolchains.xml, can be null if any vendor will fit.
   * @param javaOpts some command line arguments to give to the JVM, like -Xmx2G, -XX:Whatevever or -Dsomething=abc.
   *                 Can be null if no JVM argument is needed.
   */
  public TerracottaCommandLineEnvironment(String javaVersion, Set<String> javaVendors, List<String> javaOpts) {
    this.javaVersion = javaVersion;
    this.javaVendors = javaVendors == null ? null : Collections.unmodifiableSet(new HashSet<>(javaVendors));
    this.javaOpts = javaOpts == null ? null : Collections.unmodifiableList(new ArrayList<>(javaOpts));
  }

  public String getJavaVersion() {
    return javaVersion;
  }

  public Set<String> getJavaVendors() {
    return javaVendors;
  }

  public List<String> getJavaOpts() {
    return javaOpts;
  }

  public TerracottaCommandLineEnvironment withJavaVersion(String javaVersion) {
    return new TerracottaCommandLineEnvironment(javaVersion, javaVendors, javaOpts);
  }

  public TerracottaCommandLineEnvironment withJavaVendors(Set<String> javaVendors) {
    return new TerracottaCommandLineEnvironment(javaVersion, javaVendors, javaOpts);
  }

  public TerracottaCommandLineEnvironment withJavaVendor(String javaVendor) {
    return new TerracottaCommandLineEnvironment(javaVersion, Collections.singleton(javaVendor), javaOpts);
  }

  public TerracottaCommandLineEnvironment withJavaOpts(List<String> javaOpts) {
    return new TerracottaCommandLineEnvironment(javaVersion, javaVendors, javaOpts);
  }

  @Override
  public String toString() {
    return "TerracottaCommandLineEnvironment{" +
           "javaVersion='" + javaVersion + '\'' +
           ", javaVendors=" + javaVendors +
           ", javaOpts=" + javaOpts +
           '}';
  }
}
