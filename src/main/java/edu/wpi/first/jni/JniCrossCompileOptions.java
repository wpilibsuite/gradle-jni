package edu.wpi.first.jni;

import java.util.List;

public class JniCrossCompileOptions {
  public final String operatingSystem;
  public final String architecture;
  public final List<String> jniHeaderLocations;
  public final String name;

  public JniCrossCompileOptions(String os, String arch, List<String> locs) {
    operatingSystem = os;
    architecture = arch;
    jniHeaderLocations = locs;
    name = null;
  }

  public JniCrossCompileOptions(String name, List<String> locs) {
    operatingSystem = null;
    architecture = null;
    jniHeaderLocations = locs;
    this.name = name;
  }
}
