package edu.wpi.first.jni;

import java.util.ArrayList;
import java.util.List;

import org.gradle.api.tasks.compile.JavaCompile;
import org.gradle.nativeplatform.internal.DefaultNativeLibrarySpec;

public class DefaultJniNativeLibrary extends DefaultNativeLibrarySpec implements JniNativeLibrarySpec, JniNativeLibraryInternal {
    private List<JavaCompile> javaCompile = new ArrayList<>();
    private List<JniCrossCompileOptions> crossCompileOptions = new ArrayList<>();
    private List<String> jniHeaderLocation = new ArrayList<>();

    public DefaultJniNativeLibrary() {
        super();
    }

    public List<JavaCompile> getJavaCompileTasks() {
        return javaCompile;
    }
    public void setJavaCompileTasks(List<JavaCompile> compile) {
        javaCompile = compile;
    }

    public List<JniCrossCompileOptions> getJniCrossCompileOptions() {
        return crossCompileOptions;
    }
    public void setJniCrossCompileOptions(List<JniCrossCompileOptions> options) {
        crossCompileOptions = options;
    }

    public List<String> getJniHeaderLocations() {
        return jniHeaderLocation;
    }

    public void setJniHeaderLocations(List<String> location) {
        jniHeaderLocation = location;
    }
 }
