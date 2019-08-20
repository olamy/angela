package com.terracottatech.qa.angela.common.topology;

import java.util.Objects;

/**
 * Version holder
 * example :
 * 4.0.0
 * 4.0.0-SNAPSHOT
 * 4.3.0.1.15
 * 4.3.0-SNAPSHOT
 * 9.12
 *
 * @author Tim Eck
 */
public class Version implements Comparable<Version> {

  private int major;
  private int minor;
  private int revision;
  private int build_major;
  private int build_minor;
  private boolean snapshot;

  public static Version version(String version) {
    return new Version(version);
  }

  public Version(String version) {
    if (version == null) {
      throw new IllegalArgumentException("Version parameter is null");
    }
    String versionToSplit = version;
    if (version.endsWith("-SNAPSHOT")) {
      this.snapshot = true;
      versionToSplit = versionToSplit.split("-")[0];
    } else {
      this.snapshot = false;
    }

    String[] split = versionToSplit.split("\\.");
    if (split.length == 3 || split.length == 5) {
      this.major = parseMajorVersion(split[0]);
      this.minor = parseMinorVersion(split[1]);
      this.revision = parseRevisionVersion(split[2]);
      this.build_major = parse(split[3]);
      this.build_minor = parse(split[4]);
    } else {
      throw new IllegalArgumentException("Cannot parse string: " + version + " into a valid Version object");
    }
  }

  private int parseMajorVersion(String input) {
    int majorVersion = parse(input);
    if (majorVersion != 10 && majorVersion != 4) {
      throw new IllegalArgumentException("Expected major version to be either 10 or 4, but found: " + input);
    }
    return majorVersion;
  }

  private int parseMinorVersion(String input) {
    int minorVersion = parse(input);
    if (minorVersion < 0) {
      throw new IllegalArgumentException("Expected minor version to be a positive number, but found: " + input);
    }
    return minorVersion;
  }

  private int parseRevisionVersion(String input) {
    int revision = parse(input);
    if (revision < 0) {
      throw new IllegalArgumentException("Expected revision to be a positive number, but found: " + input);
    }
    return revision;
  }

  private int parse(String input) {
    int parsed = -1;
    try {
      parsed = Integer.parseInt(input);
    } catch (NumberFormatException e) {
      // Ignore, handled in the next step
    }
    return parsed;
  }

  public int getMajor() {
    return major;
  }

  public int getMinor() {
    return minor;
  }

  public int getRevision() {
    return revision;
  }

  public int getBuild_major() {
    return build_major;
  }

  public int getBuild_minor() {
    return build_minor;
  }

  public boolean isSnapshot() {
    return snapshot;
  }

  @Override
  public String toString() {
    return getVersion(true);
  }

  public String getVersion(boolean showSnapshot) {
    StringBuilder sb = new StringBuilder();
    if (major != -1) {
      sb.append(getMajor());
      if (minor != -1) {
        sb.append(".").append(getMinor());
        if (revision != -1) {
          sb.append(".").append(getRevision());
          if (build_major != -1) {
            sb.append(".").append(getBuild_major());
            if (build_minor != -1) {
              sb.append(".").append(getBuild_minor());
            }
          }
        }
      }
      if (isSnapshot() && showSnapshot) {
        sb.append("-SNAPSHOT");
      }
    }
    return sb.toString();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    Version version = (Version) o;
    return major == version.major &&
        minor == version.minor &&
        revision == version.revision &&
        build_major == version.build_major &&
        build_minor == version.build_minor &&
        snapshot == version.snapshot;
  }

  @Override
  public int hashCode() {
    return Objects.hash(major, minor, revision, build_major, build_minor, snapshot);
  }

  @Override
  public int compareTo(Version ver) {
    if (major > ver.major) {
      return 1;
    } else if (major < ver.major) {
      return -1;
    }

    if (minor > ver.minor) {
      return 1;
    } else if (minor < ver.minor) {
      return -1;
    }

    if (revision > ver.revision) {
      return 1;
    } else if (revision < ver.revision) {
      return -1;
    }

    if (build_major > ver.build_major) {
      return 1;
    } else if (build_major < ver.build_major) {
      return -1;
    }

    if (build_minor > ver.build_minor) {
      return 1;
    } else if (build_minor < ver.build_minor) {
      return -1;
    }

    if (snapshot) {
      return ver.snapshot ? 0 : -1;
    } else {
      return ver.snapshot ? 1 : 0;
    }
  }
}
