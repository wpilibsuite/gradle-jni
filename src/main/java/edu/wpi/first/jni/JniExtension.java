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
import org.gradle.api.file.RegularFileProperty;
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
import org.gradle.nativeplatform.tasks.LinkSharedLibrary;
import org.gradle.nativeplatform.toolchain.GccPlatformToolChain;
import org.gradle.nativeplatform.toolchain.VisualCppPlatformToolChain;
import org.gradle.process.JavaForkOptions;

public class JniExtension {

  private CppLibrary library;

  private List<TargetMachine> crossMap = new ArrayList<>();

  private String rootFolder;
  private Map<TargetMachine, String[]> includeMap = new HashMap<>();

  public Set<GccPlatformToolChain> gccToolChains = new HashSet<>();
  public Set<VisualCppPlatformToolChain> vsppToolChains = new HashSet<>();

  private List<TaskProvider<GatherExpectedJniSymbols>> expectedSymbolsTasks = new ArrayList<>();

  private boolean javaAdded = false;

  private boolean checkSymbols = false;

  @Inject
  public JniExtension(Project project, CppLibrary library, TaskProvider<ExtractCrossJniHeaders> extractTask) {
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
        String nme = b.getName();
        String name = nme.substring(0, 1).toUpperCase() + nme.substring(1);
        CppSharedLibrary bin = (CppSharedLibrary)b;
        if (b.getTargetMachine().getOperatingSystemFamily().isWindows()) {
          TaskProvider<WindowsExtractSymbols> extractSymbolsTask = project.getTasks().register("extract" + name + "Symbols", WindowsExtractSymbols.class, c -> {
            LinkSharedLibrary linkTask = bin.getLinkTask().get();
            c.getToolChain().set(linkTask.getToolChain());
            c.getTargetPlatform().set(linkTask.getTargetPlatform());
            c.getBinaryFile().set(linkTask.getLinkedFile());
            c.getSymbolFile().set(project.file("build/symbols/" + name + "Symbols.txt"));
          });
          project.getTasks().register("check" + name + "JniSymbols", JniSymbolCheck.class, c -> {
            c.dependsOn(extractSymbolsTask);
            c.isWindows().set(true);
            c.getFoundSymbols().set(project.file("build/jnisymbols/" + name + "Symbols.txt"));
            c.getCompiledSymbols().set(extractSymbolsTask.get().getSymbolFile());
            for (TaskProvider<GatherExpectedJniSymbols> gather : expectedSymbolsTasks) {
              RegularFileProperty prop = project.getObjects().fileProperty();
              prop.set(gather.get().getExpectedJniSymbols());
              c.getExpectedSymbols().add(prop);
            }
          });
        } else {
          TaskProvider<UnixExtractSymbols> extractSymbolsTask = project.getTasks().register("extract" + name + "Symbols", UnixExtractSymbols.class, c -> {
            LinkSharedLibrary linkTask = bin.getLinkTask().get();
            c.getToolChain().set(linkTask.getToolChain());
            c.getTargetPlatform().set(linkTask.getTargetPlatform());
            c.getBinaryFile().set(linkTask.getLinkedFile());
            c.getSymbolFile().set(project.file("build/symbols/" + name + "Symbols.txt"));
          });
          project.getTasks().register("check" + name + "JniSymbols", JniSymbolCheck.class, c -> {
            c.dependsOn(extractSymbolsTask);
            c.isWindows().set(false);
            c.getFoundSymbols().set(project.file("build/jnisymbols/" + name + "Symbols.txt"));
            c.getCompiledSymbols().set(extractSymbolsTask.get().getSymbolFile());
            for (TaskProvider<GatherExpectedJniSymbols> gather : expectedSymbolsTasks) {
              RegularFileProperty prop = project.getObjects().fileProperty();
              prop.set(gather.get().getExpectedJniSymbols());
              c.getExpectedSymbols().add(prop);
            }
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
        ExtractCrossJniHeaders exTask = extractTask.get();
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

  public void addJavaCompile(JavaCompile compileTask) {
    javaAdded = true;
    ExtensionAware ext = (ExtensionAware)compileTask;
    JavaJniExtension javaJni = ext.getExtensions().findByType(JavaJniExtension.class);
    javaJni.setupJni();
    // Depend on include
    library.getPrivateHeaders().from(javaJni.jniHeaderLocation);
    expectedSymbolsTasks.add(javaJni.gatherExpectedJniSymbols);

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
