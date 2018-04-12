package edu.wpi.first.jni;

import java.util.Map;

import org.gradle.api.tasks.compile.JavaCompile;

public interface JniNativeLibraryInternal {
    void setJniHeaderLocations(Map<JavaCompile, String> location);
}
