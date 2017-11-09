package com.terracottatech.qa.angela.topology;

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
public class Version implements Comparable<Version>  {

  private int major;
  private int minor;
  private int revision;
  private int build_major;
  private int build_minor;
  private boolean snapshot;

  public Version() {
  }

  public Version(final String version) {
    String versionToSplit = version;
    if (version.endsWith("-SNAPSHOT")) {
      this.snapshot = true;
      versionToSplit = versionToSplit.split("-")[0];
    } else {
      this.snapshot = false;
    }

    String[] split = versionToSplit.split("\\.");

    if (split.length == 3 || split.length == 5) {
      this.major = Integer.parseInt(split[0]);
      this.minor = Integer.parseInt(split[1]);
      this.revision = Integer.parseInt(split[2]);

      if (split.length == 5) {
        this.build_major = Integer.parseInt(split[3]);
        this.build_minor = Integer.parseInt(split[4]);
      } else {
        this.build_major = -1;
        this.build_minor = -1;
      }

    } else {
      throw new IllegalArgumentException("cannot parse version: " + version);
    }
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

  public String getRealVersion(boolean showSnapshot, boolean showLicense) {
    StringBuilder sb = new StringBuilder();
    if (major != -1) {
      sb.append(major);
      if (minor != -1) {
        sb.append(".").append(minor);
        if (revision != -1) {
          sb.append(".").append(revision);
          if (build_major != -1) {
            sb.append(".").append(build_major);
            if (build_minor != -1) {
              sb.append(".").append(build_minor);
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
  public boolean equals(final Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    final Version version = (Version)o;

    if (major != version.major) return false;
    if (minor != version.minor) return false;
    if (revision != version.revision) return false;
    if (build_major != version.build_major) return false;
    if (build_minor != version.build_minor) return false;
    return snapshot == version.snapshot;
  }

  @Override
  public int hashCode() {
    int result = major;
    result = 31 * result + minor;
    result = 31 * result + revision;
    result = 31 * result + build_major;
    result = 31 * result + build_minor;
    result = 31 * result + (snapshot ? 1 : 0);
    return result;
  }

  @Override
  public int compareTo(Version ver) {
    if (major > ver.major) {
      return 1;
    } else if (major < ver.major) { return -1; }

    if (minor > ver.minor) {
      return 1;
    } else if (minor < ver.minor) { return -1; }

    if (revision > ver.revision) {
      return 1;
    } else if (revision < ver.revision) { return -1; }

    if (build_major > ver.build_major) {
      return 1;
    } else if (build_major < ver.build_major) { return -1; }

    if (build_minor > ver.build_minor) {
      return 1;
    } else if (build_minor < ver.build_minor) { return -1; }

    if (snapshot) {
      return ver.snapshot ? 0 : -1;
    } else {
      return ver.snapshot ? 1 : 0;
    }
  }

  public static Version version(final String version) {
    return new Version(version);
  }
}
