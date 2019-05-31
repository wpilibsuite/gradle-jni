package edu.wpi.first.jni;

import org.gradle.api.tasks.compile.JavaCompile;

import java.util.Map;

public interface JniNativeSpecInternal {
  void setJniHeaderLocations(Map<JavaCompile, String> location);
}
