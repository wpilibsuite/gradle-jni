package edu.wpi.first.jni;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import javax.inject.Inject;

import org.gradle.api.DefaultTask;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.TaskAction;

public class ExtractJniFilesTask extends DefaultTask {

  private final DirectoryProperty outputDirectory;

  @OutputDirectory
  public DirectoryProperty getOutputDirectory() {
      return outputDirectory;
  }

  @Inject
  public ExtractJniFilesTask(ObjectFactory factory) {
    outputDirectory = factory.directoryProperty();
    getOutputs().dir(outputDirectory);
    outputDirectory.set(getProject().getLayout().getBuildDirectory().dir("embeddedJniHeaders"));
    setGroup("JNI");
    setDescription("Extracts the embedded JNI headers");
  }

  @TaskAction
  public void extract() {
    File mainDir = outputDirectory.getAsFile().get();
    mainDir = new File(mainDir, "arm-linux-jni");
    File linuxDir = new File(mainDir, "linux");
    linuxDir.mkdirs();

    InputStream is = ExtractJniFilesTask.class.getResourceAsStream("/arm-linux-jni/jni.h");
    OutputStream os = null;

    byte[] buffer = new byte[1024];
    int readBytes = 0;
    try {
        os = new FileOutputStream(new File(mainDir, "jni.h"));
        while ((readBytes = is.read(buffer)) != -1) {
            os.write(buffer, 0, readBytes);
        }
    } catch (IOException ex) {
    } finally {
        try {
            if (os != null) {
                os.close();
            }
            is.close();
        } catch (IOException ex) {
        }
    }

    is = ExtractJniFilesTask.class.getResourceAsStream("/arm-linux-jni/linux/jni_md.h");
    os = null;

    buffer = new byte[1024];
    readBytes = 0;
    try {
        os = new FileOutputStream(new File(linuxDir, "jni_md.h"));
        while ((readBytes = is.read(buffer)) != -1) {
            os.write(buffer, 0, readBytes);
        }
    } catch (IOException ex) {
    } finally {
        try {
            if (os != null) {
                os.close();
            }
            is.close();
        } catch (IOException ex) {
        }
    }

  }
}
