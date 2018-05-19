package edu.wpi.first.jni;

import java.io.File;

import org.gradle.api.Project;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.FileCollection;
import org.gradle.nativeplatform.NativeDependencySet;

public class JniExtractedDependencySet implements NativeDependencySet {
  protected DirectoryProperty m_property;
  protected Project m_project;

  public JniExtractedDependencySet(DirectoryProperty property, Project project) {
    m_property = property;
    m_project = project;
  }

  public FileCollection getIncludeRoots() {
    File dir = new File(m_property.getAsFile().get(), "arm-linux-jni");
    File linuxDir = new File(dir, "linux");
    return m_project.files(dir, linuxDir);
  }

  public FileCollection getLinkFiles() {
    return m_project.files();
  }

  public FileCollection getRuntimeFiles() {
    return m_project.files();
  }
}
