package edu.wpi.first.jni;

import java.util.ArrayList;
import java.util.List;

import javax.inject.Inject;

import org.gradle.api.Project;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.api.tasks.compile.JavaCompile;

public class JavaJniExtension {

  private final JavaCompile compileTask;
  private final Project project;

  public DirectoryProperty jniHeaderLocation;
  public TaskProvider<GatherExpectedJniSymbols> gatherExpectedJniSymbols = null;


  @Inject
  public JavaJniExtension(Project project, JavaCompile compileTask) {
    this.compileTask = compileTask;
    this.project = project;
    jniHeaderLocation = project.getObjects().directoryProperty();
    jniHeaderLocation.set(project.file(project.getBuildDir().toString() + "/jni/" + compileTask.getName() + "/include"));
  }

  public void setupJni() {
    if (gatherExpectedJniSymbols != null) {
      return;
    }

    gatherExpectedJniSymbols = project.getTasks().register(compileTask.getName() + "GatherExpectedJniSymbols", GatherExpectedJniSymbols.class, (t) -> {
      t.setDescription("Gather expected JNI symbols for " + compileTask.getName());
      t.dependsOn(compileTask);
      t.setJavaCompileTask(compileTask);
    });


    compileTask.getOutputs().dir(jniHeaderLocation);
    compileTask.getOptions().getCompilerArgumentProviders().add(() -> {
      List<String> args =  new ArrayList<String>();
      args.add("-h");
      args.add(jniHeaderLocation.get().getAsFile().toString());
      return args;
    });
    compileTask.doFirst(t -> {
        project.delete(jniHeaderLocation.get());
    });
  }
}
