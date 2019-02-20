package edu.wpi.first.jni;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.inject.Inject;

import org.gradle.api.GradleException;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.plugins.ExtensionAware;
import org.gradle.api.tasks.JavaExec;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.api.tasks.compile.JavaCompile;
import org.gradle.api.tasks.testing.Test;
import org.gradle.language.cpp.CppLibrary;
import org.gradle.language.cpp.CppSharedLibrary;
import org.gradle.language.cpp.internal.DefaultCppBinary;
import org.gradle.nativeplatform.TargetMachine;
import org.gradle.nativeplatform.TargetMachineFactory;
import org.gradle.nativeplatform.toolchain.GccCompatibleToolChain;
import org.gradle.nativeplatform.toolchain.GccPlatformToolChain;
import org.gradle.nativeplatform.toolchain.NativeToolChain;
import org.gradle.nativeplatform.toolchain.VisualCpp;
import org.gradle.nativeplatform.toolchain.VisualCppPlatformToolChain;
import org.gradle.process.JavaForkOptions;

public class JniExtension {

  private CppLibrary library;

  private List<TargetMachine> crossMap = new ArrayList<>();

  private String rootFolder;
  private Map<TargetMachine, String[]> includeMap = new HashMap<>();

  private List<DirectoryProperty> jniHeaderLocs = new ArrayList<>();

  public Set<GccPlatformToolChain> gccToolChains = new HashSet<>();
  public Set<VisualCppPlatformToolChain> vsppToolChains = new HashSet<>();

  private boolean javaAdded = false;

  private boolean checkSymbols = false;

  @Inject
  public JniExtension(Project project, CppLibrary library, TaskProvider<ExtractJniFilesTask> extractTask) {
    this.library = library;

    rootFolder = org.gradle.internal.jvm.Jvm.current().getJavaHome().toString() + "/include";

    TargetMachineFactory machineFactory = project.getExtensions().getByType(TargetMachineFactory.class);

    includeMap.put(machineFactory.getLinux().getX86(), new String[] { rootFolder, rootFolder.concat("/linux") });
    includeMap.put(machineFactory.getLinux().getX86_64(), new String[] { rootFolder, rootFolder.concat("/linux") });
    includeMap.put(machineFactory.getMacOS().getX86(), new String[] { rootFolder, rootFolder.concat("/darwin") });
    includeMap.put(machineFactory.getMacOS().getX86_64(), new String[] { rootFolder, rootFolder.concat("/darwin") });
    includeMap.put(machineFactory.getWindows().getX86(), new String[] { rootFolder, rootFolder.concat("/win32") });
    includeMap.put(machineFactory.getWindows().getX86_64(), new String[] { rootFolder, rootFolder.concat("/win32") });

    library.getBinaries().configureEach(b -> {
      if (!javaAdded)
        return;

      if (b instanceof CppSharedLibrary) {
        String name = b.getName();
        name = name.substring(0, 1).toUpperCase() + name.substring(1);
        project.getTasks().register("check" + name + "JniSymbols", JniSymbolCheck.class, c -> {
          c.jniComponent = this;
          c.cppBinary = (CppSharedLibrary)b;
        });

        NativeToolChain tc = b.getToolChain();

        if (tc instanceof GccCompatibleToolChain) {
          GccCompatibleToolChain gtc = (GccCompatibleToolChain) tc;
          gtc.eachPlatform(a -> {
            gccToolChains.add(a);
          });
        } else if (tc instanceof VisualCpp) {
          VisualCpp vctc = (VisualCpp) tc;
          vctc.eachPlatform(a -> {
            vsppToolChains.add(a);
          });
        }
      }

      String[] includePath = includeMap.getOrDefault(b.getTargetMachine(), null);
      if (includePath != null) {
        DefaultCppBinary bin = (DefaultCppBinary) b;
        project.getDependencies().add(bin.getIncludePathConfiguration().getName(),
            project.files((Object[]) includePath));
        return;
      }

      if (crossMap.contains(b.getTargetMachine())) {
        b.getCompileTask().get().dependsOn(extractTask);
        DefaultCppBinary bin = (DefaultCppBinary) b;
        ExtractJniFilesTask exTask = extractTask.get();
        File dir = new File(exTask.outputDirectory.getAsFile().get(), "arm-linux-jni");
        File linuxDir = new File(dir, "linux");
        project.getDependencies().add(bin.getIncludePathConfiguration().getName(), project.files(dir, linuxDir));
      } else {
        throw new GradleException("No known JNI configs");
      }
    });
  }

  /**
   * @return the checkSymbols
   */
  public boolean isCheckSymbols() {
    return checkSymbols;
  }

  /**
   * @param checkSymbols the checkSymbols to set
   */
  public void setCheckSymbols(boolean checkSymbols) {
    this.checkSymbols = checkSymbols;
  }

  public List<DirectoryProperty> getJniHeaderLocations() {
    return jniHeaderLocs;
  }

  public void addJavaCompile(TaskProvider<JavaCompile> compileTask) {
    javaAdded = true;
    compileTask.configure(c -> {
      ExtensionAware ext = (ExtensionAware)c;
      JavaJniExtension javaJni = ext.getExtensions().findByType(JavaJniExtension.class);
      javaJni.addJniHeaderGeneration();
      // Depend on include
      library.getPrivateHeaders().from(javaJni.jniHeaderLoc);
      jniHeaderLocs.add(javaJni.jniHeaderLoc);
    });

    library.getBinaries().configureEach(c -> {
      c.getCompileTask().get().dependsOn(compileTask);
    });
  }

  public void addCrossCompile(TargetMachine machine) {
    crossMap.add(machine);
  }

  public void addCrossCompile(TargetMachine machine, String[] paths, Object... tasks) {
    // TODO
    //includeMap.put(machine, value)
  }

  private void setupJavaExecTestTask(JavaForkOptions option, Task task) {
    CppSharedLibrary devBinary = (CppSharedLibrary)library.getDevelopmentBinary().get();
    task.dependsOn(devBinary.getLinkFileProducer());
    task.doFirst(c -> {
      String locs = "";
      for (File f : devBinary.getRuntimeLibraries()) {
        locs = f.getParent() + File.pathSeparator;
      }
      locs += devBinary.getRuntimeFile().get().getAsFile().getParent() + File.pathSeparator;


      Map<String, Object> env = option.getEnvironment();

      boolean set = false;

      for(Map.Entry<String, Object> kvp : env.entrySet()) {
        if (kvp.getKey().equalsIgnoreCase("PATH")) {
          String oldPath = (String)kvp.getValue();
          String newPath = locs + oldPath;
          //env
          env.put(kvp.getKey(), newPath);
          option.setEnvironment(env);
          set = true;
          break;
        }
      }

      if (!set) {
        option.environment("PATH", locs);
      }

      set = false;

      env = option.getSystemProperties();

      for(Map.Entry<String, Object> kvp : env.entrySet()) {
        if (kvp.getKey().equalsIgnoreCase("java.library.path")) {
          String oldPath = (String)kvp.getValue();
          String newPath = locs + oldPath;
          //env
          env.put(kvp.getKey(), newPath);
          option.setSystemProperties(env);
          set = true;
          break;
        }
      }

      if (!set) {
        option.systemProperty("java.library.path", locs);
      }
    });
  }

  public void addJavaTest(TaskProvider<Test> javaExec) {
    javaExec.configure(c -> {
      setupJavaExecTestTask(c, c);
    });
  }

  public void addJavaExec(TaskProvider<JavaExec> javaExec) {
    javaExec.configure(c -> {
      setupJavaExecTestTask(c, c);
    });
  }
}
