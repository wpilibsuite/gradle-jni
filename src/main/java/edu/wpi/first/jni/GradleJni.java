package edu.wpi.first.jni;

import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.UnknownTaskException;
import org.gradle.api.plugins.ExtensionAware;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.api.tasks.compile.JavaCompile;
import org.gradle.language.cpp.CppLibrary;

class GradleJni implements Plugin<Project> {
  public void apply(Project project) {
    TaskProvider<ExtractCrossJniHeaders> extractTask;
    try {
      extractTask = project.getRootProject().getTasks().named("extractEmbeddedCrossJniHeaders", ExtractCrossJniHeaders.class);
    } catch (UnknownTaskException ex) {
      extractTask = project.getRootProject().getTasks().register("extractEmbeddedCrossJniHeaders", ExtractCrossJniHeaders.class);
    }

    TaskProvider<ExtractCrossJniHeaders> extractCrossJniTask = extractTask;

    project.getTasks().withType(JavaCompile.class, c -> {
      ExtensionAware ext = (ExtensionAware)c;
      ext.getExtensions().add(JavaJniExtension.class, "jni", project.getObjects().newInstance(JavaJniExtension.class, project, c));
    });

    try {
      project.getComponents().withType(CppLibrary.class, o -> {
        ExtensionAware ext = (ExtensionAware)o;
        ext.getExtensions().add(JniExtension.class, "jni", project.getObjects().newInstance(JniExtension.class, project, o, extractCrossJniTask));
      });
    } catch (NoClassDefFoundError ex) {

    }

  }
}
