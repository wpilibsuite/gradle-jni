package edu.wpi.first.jni;

import org.gradle.api.DefaultTask;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.tasks.OutputFile;

public class JniSymbolCheck extends DefaultTask {
  @OutputFile
  public RegularFileProperty foundSymbols = newOutputFile();
}
