package edu.wpi.first.jni;

import java.util.ArrayList;
import java.util.List;

import javax.inject.Inject;

import org.gradle.api.Project;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.tasks.compile.JavaCompile;

public class JavaJniExtension {

  private JavaCompile compileTask;
  private Project project;
  private boolean headerGenerationConfigured = false;

  public DirectoryProperty jniHeaderLoc;


  @Inject
  public JavaJniExtension(Project project, JavaCompile compileTask) {
    this.compileTask = compileTask;
    this.project = project;
    jniHeaderLoc = project.getObjects().directoryProperty();
    jniHeaderLoc.set(project.file(project.getBuildDir().toString() + "/jniinclude/" + compileTask.getName()));
  }

  public void addJniHeaderGeneration() {
    if (!headerGenerationConfigured) {
      return;
    }
    headerGenerationConfigured = true;
    compileTask.getOutputs().dir(jniHeaderLoc);
    compileTask.getOptions().getCompilerArgumentProviders().add(() -> {
      List<String> args =  new ArrayList<String>();
      args.add("-h");
      args.add(jniHeaderLoc.get().getAsFile().toString());
      return args;
    });
    compileTask.doFirst(t -> {
        project.delete(jniHeaderLoc.get());
    });
  }
}
