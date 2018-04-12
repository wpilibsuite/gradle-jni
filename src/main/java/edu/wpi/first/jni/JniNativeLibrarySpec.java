package edu.wpi.first.jni;

import java.util.List;
import java.util.Map;

import org.gradle.api.tasks.compile.JavaCompile;
import org.gradle.nativeplatform.NativeLibrarySpec;

public interface JniNativeLibrarySpec extends NativeLibrarySpec, JniNativeLibraryInternal {
    List<JavaCompile> getJavaCompileTasks();
    void setJavaCompileTasks(List<JavaCompile> compile);

    void setJniCrossCompileOptions(List<JniCrossCompileOptions> options);
    List<JniCrossCompileOptions> getJniCrossCompileOptions();

    boolean getEnableCheckTask();
    void setEnableCheckTask(boolean val);

    Map<JavaCompile, String> getJniHeaderLocations();
}
