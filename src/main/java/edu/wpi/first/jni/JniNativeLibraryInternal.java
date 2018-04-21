package edu.wpi.first.jni;

import org.gradle.api.tasks.compile.JavaCompile;

import java.util.Map;

public interface JniNativeLibraryInternal {
    void setJniHeaderLocations(Map<JavaCompile, String> location);
}
