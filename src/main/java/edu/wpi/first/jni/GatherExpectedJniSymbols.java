package edu.wpi.first.jni;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import javax.inject.Inject;

import org.gradle.api.DefaultTask;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.FileTree;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.tasks.InputDirectory;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;
import org.gradle.api.tasks.compile.JavaCompile;

public class GatherExpectedJniSymbols extends DefaultTask {
  private DirectoryProperty jniHeaderLocation;

  private RegularFileProperty expectedJniSymbols;

  /**
   * @return the jniHeaderLocation
   */
  @InputDirectory
  public DirectoryProperty getJniHeaderLocation() {
    return jniHeaderLocation;
  }

  /**
   * @return the expectedJniSymbols
   */
  @OutputFile
  public RegularFileProperty getExpectedJniSymbols() {
    return expectedJniSymbols;
  }

  @Inject
  public GatherExpectedJniSymbols(ObjectFactory factory) {
    jniHeaderLocation = factory.directoryProperty();
    getInputs().dir(jniHeaderLocation);
    expectedJniSymbols = factory.fileProperty();
    getOutputs().file(expectedJniSymbols);
    setGroup("JNI");
  }

  public void setJavaCompileTask(JavaCompile compile) {
    JavaJniExtension jniExt = compile.getExtensions().getByType(JavaJniExtension.class);
    jniHeaderLocation.set(jniExt.jniHeaderLocation);
    expectedJniSymbols.set(getProject()
        .file(getProject().getBuildDir().toString() + "/jni/" + compile.getName() + "/ExpectedSymbols.txt"));
  }

  private List<String> getExpectedSymbols() {
    // Get expected symbols
    List<String> symbolList = new ArrayList<>();
    FileTree tree = getProject().fileTree(jniHeaderLocation.get().getAsFile().toString());
    for (File file : tree) {
      try (Stream<String> stream = Files.lines(file.toPath())) {
        stream.map(s -> s.trim()).filter(s -> !s.isEmpty() && (s.startsWith("JNIEXPORT ") && s.contains("JNICALL")))
            .forEach(line -> {
              symbolList.add(line.split("JNICALL")[1].trim());
            });
      } catch (IOException e) {
        continue;
      }
    }
    return symbolList;
  }

  @TaskAction
  private void execute() throws IOException {
    List<String> symbols = getExpectedSymbols();
    Files.write(expectedJniSymbols.get().getAsFile().toPath(), symbols);
  }
}
