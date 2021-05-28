package edu.wpi.first.jni;

import org.gradle.api.tasks.compile.JavaCompile;
import org.gradle.api.file.DirectoryProperty;

import java.util.Map;

public interface JniNativeSpecInternal {
  void setJniHeaderLocations(Map<JavaCompile, DirectoryProperty> location);
}
