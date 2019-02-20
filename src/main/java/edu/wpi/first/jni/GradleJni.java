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
    TaskProvider<ExtractJniFilesTask> extractEmbeddedJniTask = null;
    try {
      extractEmbeddedJniTask = project.getRootProject().getTasks().named("extractEmbeddedJni", ExtractJniFilesTask.class);
    } catch (UnknownTaskException ex) {
      extractEmbeddedJniTask = project.getRootProject().getTasks().register("extractEmbeddedJni", ExtractJniFilesTask.class);
    }

    final TaskProvider<ExtractJniFilesTask> extractTask = extractEmbeddedJniTask;

    //CppBasePlugin

    project.getComponents().withType(CppLibrary.class, o -> {
      ExtensionAware ext = (ExtensionAware)o;
      ext.getExtensions().add(JniExtension.class, "jni", project.getObjects().newInstance(JniExtension.class, project, o, extractTask));
    });

    project.getTasks().withType(JavaCompile.class).configureEach(c -> {
      ExtensionAware ext = (ExtensionAware)c;
      ext.getExtensions().add(JavaJniExtension.class, "jni", project.getObjects().newInstance(JavaJniExtension.class, project, c));
    });
  }
}
