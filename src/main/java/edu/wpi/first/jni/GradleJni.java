package edu.wpi.first.jni;

import java.util.ArrayList;
import java.util.List;

import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.tasks.compile.JavaCompile;
import org.gradle.language.base.internal.ProjectLayout;
import org.gradle.language.nativeplatform.tasks.AbstractNativeSourceCompileTask;
import org.gradle.model.ModelMap;
import org.gradle.model.Mutate;
import org.gradle.model.RuleSource;
import org.gradle.model.Validate;
import org.gradle.nativeplatform.NativeBinarySpec;
import org.gradle.platform.base.BinarySpec;
import org.gradle.platform.base.ComponentSpec;
import org.gradle.platform.base.ComponentSpecContainer;
import org.gradle.platform.base.ComponentType;
import org.gradle.platform.base.TypeBuilder;

class GradleJni implements Plugin<Project> {
  public void apply(Project project) {
  }

  static class Rules extends RuleSource {
    @ComponentType
    void registerJni(TypeBuilder<JniNativeLibrarySpec> builder) {
      builder.defaultImplementation(DefaultJniNativeLibrary.class);
      builder.internalView(JniNativeLibraryInternal.class);
    }

    @Mutate
    void addJniDependencies(ModelMap<Task> tasks, ComponentSpecContainer components, ProjectLayout projectLayout) {
      Project project = (Project)projectLayout.getProjectIdentifier();
      for (ComponentSpec oComponent : components) {
        if (oComponent instanceof JniNativeLibrarySpec) {
          JniNativeLibrarySpec component = (JniNativeLibrarySpec)oComponent;
          for (BinarySpec oBinary : component.getBinaries()) {
            NativeBinarySpec binary = (NativeBinarySpec)oBinary;
            binary.getTasks().withType(AbstractNativeSourceCompileTask.class, it -> {
              it.dependsOn(component.getJavaCompileTasks().toArray());
            });

            List<String> jniFiles = new ArrayList<>();

            boolean cross = false;

            for (JniCrossCompileOptions config : component.getJniCrossCompileOptions()) {
              if (binary.getTargetPlatform().getArchitecture().getName() == config.architecture
              && binary.getTargetPlatform().getOperatingSystem().getName() == config.operatingSystem ) {
                  cross = true;
                  jniFiles.addAll(config.jniHeaderLocations);
                  break;
              }
            }

            if (!cross) {
              String base = org.gradle.internal.jvm.Jvm.current().getJavaHome().toString() + "/include";

              jniFiles.add(base);
              if (binary.getTargetPlatform().getOperatingSystem().isMacOsX()) {
                  jniFiles.add(base.concat("darwin").toString());
              } else if (binary.getTargetPlatform().getOperatingSystem().isLinux()) {
                  jniFiles.add(base.concat("linux").toString());
              } else if (binary.getTargetPlatform().getOperatingSystem().isWindows()) {
                  jniFiles.add(base.concat("win32").toString());
              } else if (binary.getTargetPlatform().getOperatingSystem().isFreeBSD()) {
                  jniFiles.add(base.concat("freebsd").toString());
              } else if (project.file(base.concat("darwin")).exists()) {
                  // As of Gradle 2.8, targetPlatform.operatingSystem.macOsX returns false
                  // on El Capitan. We therefore manually test for the darwin folder and include it
                  // if it exists
                  jniFiles.add(base.concat("darwin").toString());
              }
            }

            binary.lib(new JniSystemDependencySet(jniFiles, project));

            binary.lib(new JniSourceDependencySet(component.getJniHeaderLocations(), project));
          }
        }
      }
    }

    @Validate
    void createJniTasks(ComponentSpecContainer components, ProjectLayout projectLayout) {
      Project project = (Project)projectLayout.getProjectIdentifier();
      for (ComponentSpec oComponent : components) {
        if (oComponent instanceof JniNativeLibrarySpec) {
          JniNativeLibrarySpec component = (JniNativeLibrarySpec)oComponent;

          assert !component.getJavaCompileTasks().isEmpty();

          for (JavaCompile compileTask : component.getJavaCompileTasks()) {
            String jniHeaderLocation = project.getBuildDir().toString() + "/jniinclude/" + compileTask.getName();
            component.getJniHeaderLocations().add(jniHeaderLocation);
            List<String> args = compileTask.getOptions().getCompilerArgs();
            args.add("-h");
            args.add(jniHeaderLocation);
          }
        }
      }
    }
  }
}
