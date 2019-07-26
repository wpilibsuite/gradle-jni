package edu.wpi.first.jni;

import org.gradle.api.tasks.compile.JavaCompile;
import org.gradle.nativeplatform.internal.DefaultNativeLibrarySpec;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DefaultJniNativeExecutable extends DefaultNativeLibrarySpec
    implements JniNativeExecutableSpec {
  private List<JavaCompile> javaCompile = new ArrayList<>();
  private List<JniCrossCompileOptions> crossCompileOptions = new ArrayList<>();
  private Map<JavaCompile, String> jniHeaderLocation = new HashMap<>();
  private boolean enableCheckTask = false;

  public DefaultJniNativeExecutable() {
    super();
  }

  @Override
  public List<JavaCompile> getJavaCompileTasks() {
    return javaCompile;
  }

  @Override
  public void setJavaCompileTasks(List<JavaCompile> compile) {
    javaCompile = compile;
  }

  @Override
  public List<JniCrossCompileOptions> getJniCrossCompileOptions() {
    return crossCompileOptions;
  }

  @Override
  public void setJniCrossCompileOptions(List<JniCrossCompileOptions> options) {
    crossCompileOptions = options;
  }

  @Override
  public Map<JavaCompile, String> getJniHeaderLocations() {
    return jniHeaderLocation;
  }

  @Override
  public void setJniHeaderLocations(Map<JavaCompile, String> location) {
    jniHeaderLocation = location;
  }

  @Override
  public boolean getEnableCheckTask() {
    return enableCheckTask;
  }

  @Override
  public void setEnableCheckTask(boolean val) {
    enableCheckTask = val;
  }
}
