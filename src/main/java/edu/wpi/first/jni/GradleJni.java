package edu.wpi.first.jni;

import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.language.base.plugins.ComponentModelBasePlugin;

class GradleJni implements Plugin<Project> {
  public void apply(Project project) {
    if (project.equals(project.getRootProject())) {
      project.getTasks().create("extractEmbeddedJni", ExtractJniFilesTask.class);
    }

    if(project.getPlugins().hasPlugin(ComponentModelBasePlugin.class)) {
      project.getExtensions().getExtraProperties().set("JniNativeLibrarySpec", JniNativeLibrarySpec.class);
      project.getExtensions().getExtraProperties().set("JniCrossCompileOptions", new CreateJniCrossCompileOptions());
      project.getExtensions().create("gradleJniConfiguration", GradleJniConfiguration.class);
      project.getPluginManager().apply(JniRules.class);
    }


  }
}
