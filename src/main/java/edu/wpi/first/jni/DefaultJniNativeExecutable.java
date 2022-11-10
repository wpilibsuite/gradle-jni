package edu.wpi.first.jni;

import org.gradle.api.tasks.compile.JavaCompile;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.nativeplatform.internal.DefaultNativeLibrarySpec;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DefaultJniNativeExecutable extends DefaultNativeLibrarySpec
    implements JniNativeExecutableSpec {
  private List<JavaCompile> javaCompile = new ArrayList<>();
  private List<JniCrossCompileOptions> crossCompileOptions = new ArrayList<>();
  private Map<JavaCompile, DirectoryProperty> jniHeaderLocation = new HashMap<>();
  private boolean enableCheckTask = false;
  private List<String> checkSkipSymbols = new ArrayList<>();

  @Override
  public void setCheckSkipSymbols(List<String> checkSkipSymbols) {
    this.checkSkipSymbols = checkSkipSymbols;
  }

  @Override
  public List<String> getCheckSkipSymbols() {
    return checkSkipSymbols;
  }

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
  public Map<JavaCompile, DirectoryProperty> getJniHeaderLocations() {
    return jniHeaderLocation;
  }

  @Override
  public void setJniHeaderLocations(Map<JavaCompile, DirectoryProperty> location) {
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
