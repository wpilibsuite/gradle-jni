package edu.wpi.first.jni;

import org.gradle.api.tasks.compile.JavaCompile;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.platform.base.VariantComponentSpec;

import java.util.List;
import java.util.Map;

public interface JniNativeBaseSpec extends VariantComponentSpec, JniNativeSpecInternal {
    List<JavaCompile> getJavaCompileTasks();

    void setJavaCompileTasks(List<JavaCompile> compile);

    void setJniCrossCompileOptions(List<JniCrossCompileOptions> options);

    List<JniCrossCompileOptions> getJniCrossCompileOptions();

    boolean getEnableCheckTask();

    void setEnableCheckTask(boolean val);

    List<String> getCheckSkipSymbols();

    void setCheckSkipSymbols(List<String> symbols);

    Map<JavaCompile, DirectoryProperty> getJniHeaderLocations();
}
