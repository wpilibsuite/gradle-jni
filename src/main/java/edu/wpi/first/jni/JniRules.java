package edu.wpi.first.jni;

import java.util.ArrayList;
import java.util.List;

import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.plugins.ExtensionContainer;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.api.tasks.compile.JavaCompile;
import org.gradle.internal.service.ServiceRegistry;
import org.gradle.language.base.internal.ProjectLayout;
import org.gradle.language.nativeplatform.tasks.AbstractNativeSourceCompileTask;
import org.gradle.model.Finalize;
import org.gradle.model.ModelMap;
import org.gradle.model.Mutate;
import org.gradle.model.RuleSource;
import org.gradle.model.Validate;
import org.gradle.nativeplatform.NativeBinarySpec;
import org.gradle.nativeplatform.SharedLibraryBinarySpec;
import org.gradle.nativeplatform.toolchain.GccCompatibleToolChain;
import org.gradle.nativeplatform.toolchain.NativeToolChainRegistry;
import org.gradle.nativeplatform.toolchain.VisualCpp;
import org.gradle.nativeplatform.toolchain.internal.msvcpp.VisualStudioLocator;
import org.gradle.platform.base.BinarySpec;
import org.gradle.platform.base.ComponentSpec;
import org.gradle.platform.base.ComponentSpecContainer;
import org.gradle.platform.base.ComponentType;
import org.gradle.platform.base.TypeBuilder;

public class JniRules extends RuleSource {
  @ComponentType
  void registerJni(TypeBuilder<JniNativeLibrarySpec> builder) {
    builder.defaultImplementation(DefaultJniNativeLibrary.class);
    builder.internalView(JniNativeLibraryInternal.class);
  }

  private void setupCheckTasks(NativeBinarySpec binary, ModelMap<Task> tasks, JniNativeLibrarySpec jniComponent,
      Project project) {
    if (!binary.isBuildable()) {
      return;
    }
    if (!(binary instanceof SharedLibraryBinarySpec)) {
      return;
    }
    SharedLibraryBinarySpec sharedBinary = (SharedLibraryBinarySpec) binary;

    String input = binary.getBuildTask().getName();
    String projName = input.substring(0, 1).toUpperCase() + input.substring(1);
    String checkTaskName = "check" + projName + "JniSymbols";
    tasks.create(checkTaskName, JniSymbolCheck.class, task -> {
      task.setGroup("Build");
      task.setDescription("Checks that JNI symbols exist in the native libraries");
      task.dependsOn(sharedBinary.getTasks().getLink());
      task.getInputs().file(sharedBinary.getSharedLibraryFile());
      for (String j : jniComponent.getJniHeaderLocations().values()) {
        task.getInputs().dir(j);
      }
      task.getOutputs().file(task.foundSymbols);
      task.binaryToCheck = sharedBinary;
      task.jniComponent = jniComponent;
      task.foundSymbols.set(project.getLayout().getBuildDirectory().file("jnisymbols/" + projName + "/symbols.txt"));
    });
    binary.checkedBy(tasks.get(checkTaskName));
  }

  @Finalize
  void getPlatformToolChainsJNI(NativeToolChainRegistry toolChains, ExtensionContainer extCont,
      ServiceRegistry serviceRegistry) {
    GradleJniConfiguration ext = extCont.getByType(GradleJniConfiguration.class);
    ext.vsLocator = serviceRegistry.get(VisualStudioLocator.class);
    toolChains.all(tc -> {
      if (tc instanceof VisualCpp) {
        VisualCpp vtc = (VisualCpp) tc;
        vtc.eachPlatform(t -> {
          ext.visualCppPlatforms.add(t);
        });
      } else if (tc instanceof GccCompatibleToolChain) {
        GccCompatibleToolChain gtc = (GccCompatibleToolChain) tc;
        gtc.eachPlatform(t -> {
          ext.gccLikePlatforms.add(t);
        });
      }
    });
  }

  @Mutate
  void addJniDependencies(ModelMap<Task> tasks, ComponentSpecContainer components, ProjectLayout projectLayout,
      NativeToolChainRegistry toolChains) {

    Project project = (Project) projectLayout.getProjectIdentifier();
    for (ComponentSpec oComponent : components) {
      if (oComponent instanceof JniNativeLibrarySpec) {
        JniNativeLibrarySpec component = (JniNativeLibrarySpec) oComponent;
        for (BinarySpec oBinary : component.getBinaries()) {
          if (!oBinary.isBuildable()) {
            continue;
          }
          NativeBinarySpec binary = (NativeBinarySpec) oBinary;
          binary.getTasks().withType(AbstractNativeSourceCompileTask.class, it -> {
            it.dependsOn(component.getJavaCompileTasks().toArray());
          });

          List<String> jniFiles = new ArrayList<>();

          boolean cross = false;

          if (component.getEnableCheckTask()) {
            setupCheckTasks(binary, tasks, component, project);
          }

          for (JniCrossCompileOptions config : component.getJniCrossCompileOptions()) {
            if ((binary.getTargetPlatform().getArchitecture().getName().equals(config.architecture)
                && binary.getTargetPlatform().getOperatingSystem().getName().equals(config.operatingSystem))
                || binary.getTargetPlatform().getName().equals(config.name)) {
              cross = true;
              if (config.jniHeaderLocations == null) {
                TaskProvider<Task> extractTask = project.getRootProject().getTasks()
                    .named("extractEmbeddedJni");
                binary.getTasks().withType(AbstractNativeSourceCompileTask.class, it -> {
                  it.dependsOn(extractTask);
                });
                binary.lib(new JniExtractedDependencySet(extractTask, project));
              } else {
                jniFiles.addAll(config.jniHeaderLocations);
                binary.lib(new JniSystemDependencySet(jniFiles, project));
              }
              break;
            }
          }

          if (!cross) {
            String base = org.gradle.internal.jvm.Jvm.current().getJavaHome().toString() + "/include";

            jniFiles.add(base);
            if (binary.getTargetPlatform().getOperatingSystem().isMacOsX()) {
              jniFiles.add(base.concat("/darwin").toString());
            } else if (binary.getTargetPlatform().getOperatingSystem().isLinux()) {
              jniFiles.add(base.concat("/linux").toString());
            } else if (binary.getTargetPlatform().getOperatingSystem().isWindows()) {
              jniFiles.add(base.concat("/win32").toString());
            } else if (binary.getTargetPlatform().getOperatingSystem().isFreeBSD()) {
              jniFiles.add(base.concat("/freebsd").toString());
            } else if (project.file(base.concat("/darwin")).exists()) {
              // As of Gradle 2.8, targetPlatform.operatingSystem.macOsX returns false
              // on El Capitan. We therefore manually test for the darwin folder and include
              // it
              // if it exists
              jniFiles.add(base.concat("/darwin").toString());
            }
            binary.lib(new JniSystemDependencySet(jniFiles, project));
          }

          binary.lib(new JniSourceDependencySet(component.getJniHeaderLocations().values(), project));
        }
      }
    }
  }

  @Validate
  void createJniTasks(ComponentSpecContainer components, ProjectLayout projectLayout) {
    Project project = (Project) projectLayout.getProjectIdentifier();
    for (ComponentSpec oComponent : components) {
      if (oComponent instanceof JniNativeLibrarySpec) {
        JniNativeLibrarySpec component = (JniNativeLibrarySpec) oComponent;

        assert !component.getJavaCompileTasks().isEmpty();

        for (JavaCompile compileTask : component.getJavaCompileTasks()) {
          String jniHeaderLocation = project.getBuildDir().toString() + "/jniinclude/" + compileTask.getName();
          compileTask.getOutputs().dir(jniHeaderLocation);
          component.getJniHeaderLocations().put(compileTask, jniHeaderLocation);
          List<String> args = compileTask.getOptions().getCompilerArgs();
          args.add("-h");
          args.add(jniHeaderLocation);
          compileTask.doFirst(t -> {
            project.delete(jniHeaderLocation);
          });
        }
      }
    }
  }
}
