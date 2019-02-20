package edu.wpi.first.jni;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import javax.inject.Inject;

import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.file.Directory;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.FileTree;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;
import org.gradle.internal.service.ServiceRegistry;

public class JniSymbolCheck extends DefaultTask {
  private RegularFileProperty foundSymbols;

  private RegularFileProperty symbolFile;

  private List<DirectoryProperty> headerLocations;

  private Property<Boolean> isWindows;

  @Inject
  public JniSymbolCheck(ObjectFactory factory, ServiceRegistry serviceRegistry) {
    foundSymbols = factory.fileProperty();
    symbolFile = factory.fileProperty();
    headerLocations = new ArrayList<>();
    isWindows = factory.property(Boolean.class);

    setGroup("JNI");
    setDescription("Checks that the JNI symbols exist in the native libraries");
  }

  /**
   * @return the isWindows
   */
  @Input
  public Property<Boolean> isWindows() {
    return isWindows;
  }

  /**
   * @return the foundSymbols
   */
  @OutputFile
  public RegularFileProperty getFoundSymbols() {
    return foundSymbols;
  }

  /**
   * @return the symbolFile
   */
  @InputFile
  public RegularFileProperty getSymbolFile() {
    return symbolFile;
  }

  /**
   * @return the headerLocations
   */
  @Input
  public List<DirectoryProperty> getHeaderLocations() {
    return headerLocations;
  }

  private List<String> getExpectedSymbols() {
    // Get expected symbols
    List<String> symbolList = new ArrayList<>();
    for (DirectoryProperty loc : headerLocations) {
      FileTree tree = getProject().fileTree(loc.get().getAsFile().toString());
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
    }
    return symbolList;
  }

  private void handleWindowsSymbolCheck() {

    List<String> missingSymbols = new ArrayList<>();

    String dumpBinSymbols;

    try {
      dumpBinSymbols = new String(Files.readAllBytes(symbolFile.get().getAsFile().toPath()));
    } catch (IOException e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
      dumpBinSymbols = "";
    }

    List<String> symbolList = getExpectedSymbols();

    for (String symbol : symbolList) {
      if (!dumpBinSymbols.contains(symbol + " =") && !dumpBinSymbols.contains(symbol + "@")) {
        System.out.println("Missing Symbol");
        missingSymbols.add(symbol);
      }
    }

    if (!missingSymbols.isEmpty()) {
      StringBuilder missingString = new StringBuilder();
      for (String symbol : missingSymbols) {
        missingString.append(symbol);
        missingString.append('\n');
      }
      throw new GradleException("Found a definition that does not have a matching symbol " + missingString.toString());
    }
    File writeSymbolFile = foundSymbols.get().getAsFile();
    writeSymbolFile.getParentFile().mkdirs();
    try (FileWriter writer = new FileWriter(writeSymbolFile)) {
      for (String str : symbolList) {
        writer.write(str);
        writer.write('\n');
      }
    } catch (IOException ex) {
      System.out.println(ex);
    }
  }

  private void handleUnixSymbolCheck() {

    List<String> symbolList = getExpectedSymbols();

    String nmSymbols;
    try {
      nmSymbols = new String(Files.readAllBytes(symbolFile.get().getAsFile().toPath()));
      nmSymbols.replace("\r", "");
    } catch (IOException e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
      nmSymbols = "";
    }

    List<String> missingSymbols = new ArrayList<>();

    for (String symbol : symbolList) {
      if (!nmSymbols.contains(symbol + "\n")) {
        missingSymbols.add(symbol);
      }
    }

    if (!missingSymbols.isEmpty()) {
      StringBuilder missingString = new StringBuilder();
      for (String symbol : missingSymbols) {
        missingString.append(symbol);
        missingString.append('\n');
      }
      throw new GradleException("Found a definition that does not have a matching symbol " + missingString.toString());
    }
    File writeSymbolFile = foundSymbols.get().getAsFile();
    writeSymbolFile.getParentFile().mkdirs();
    try (FileWriter writer = new FileWriter(writeSymbolFile)) {
      for (String str : symbolList) {
        writer.write(str);
        writer.write('\n');
      }
    } catch (IOException ex) {
      System.out.println(ex);
    }
  }

  @TaskAction
  public void checkSymbols() {
    if (isWindows.get()) {
      handleWindowsSymbolCheck();
    } else {
      handleUnixSymbolCheck();
    }
    /*
    if (this.vcpToolChain != null) {
      // Windows C++
      VisualStudioLocator vsLocator = serviceRegistry.get(VisualStudioLocator.class);
      VisualCpp vcpp = (VisualCpp)cppBinary.getToolChain();
      SearchResult<VisualStudioInstall> vsiSearch = vsLocator.locateComponent(vcpp.getInstallDir());
      if (vsiSearch.isAvailable()) {
        VisualStudioInstall vsi = vsiSearch.getComponent();
        org.gradle.nativeplatform.toolchain.internal.msvcpp.VisualCpp vscpp = vsi.getVisualCpp()
                .forPlatform((NativePlatformInternal) cppBinary.getTargetPlatform());
        File cppPath = vscpp.getCompilerExecutable();
        File cppDir = new File(cppPath.getParentFile().getParentFile().toString(), "x64");
        if (cppPath.toString().contains("Microsoft Visual Studio 14.0")) {
          cppDir = new File(cppPath.getParentFile().getParentFile().toString(), "amd64");
        }

        File dumpbinDir = new File(cppDir, "dumpbin.exe");
        handleWindowsSymbolCheck(dumpbinDir);
      }
    } else if (this.gccToolChain != null) {
      // GCC/Clang
      ToolSearchPath tsp = new ToolSearchPath(OperatingSystem.current());
      this.gccToolChain.getCppCompiler();
          CommandLineToolSearchResult cppSearch = tsp.locate(ToolType.SYMBOL_EXTRACTOR,
              gccPlat.getCppCompiler().getExecutable());
          if (cppSearch.isAvailable()) {
            found = true;
            File cppPath = cppSearch.getTool();
            File cppDir = cppPath.getParentFile();
            String exeName = cppPath.getName();
            String prefix = "";
            int index = exeName.lastIndexOf('-');
            if (index != -1) {
              prefix = exeName.substring(0, exeName.lastIndexOf('-') + 1);
            }
            File nmDir = new File(cppDir, prefix + "nm");
            handleUnixSymbolCheck(nmDir);
          }

    } else {
      throw new GradleException("Unknown Platform?");
    }




    for (VisualCppPlatformToolChain msvcPlat : jniComponent.vsppToolChains) {
      if (msvcPlat.getPlatform().equals(toolChain)) {
        if (toolChain instanceof VisualCpp) {
          VisualCpp vcpp = (VisualCpp) toolChain;
          SearchResult<VisualStudioInstall> vsiSearch = ext.vsLocator.locateComponent(vcpp.getInstallDir());
          if (vsiSearch.isAvailable()) {
            VisualStudioInstall vsi = vsiSearch.getComponent();
            org.gradle.nativeplatform.toolchain.internal.msvcpp.VisualCpp vscpp = vsi.getVisualCpp()
                .forPlatform((NativePlatformInternal) binaryToCheck.getTargetPlatform());
            File cppPath = vscpp.getCompilerExecutable();
            File cppDir = new File(cppPath.getParentFile().getParentFile().toString(), "x64");
            if (cppPath.toString().contains("Microsoft Visual Studio 14.0")) {
              cppDir = new File(cppPath.getParentFile().getParentFile().toString(), "amd64");
            }

            File dumpbinDir = new File(cppDir, "dumpbin.exe");
            handleWindowsSymbolCheck(dumpbinDir);
            found = true;
            break;
          }
        }
      }
    }

    if (!found) {
      for (GccPlatformToolChain gccPlat : ext.gccLikePlatforms) {
        if (gccPlat.getPlatform().equals(binaryToCheck.getTargetPlatform())) {

          ToolSearchPath tsp = new ToolSearchPath(OperatingSystem.current());
          CommandLineToolSearchResult cppSearch = tsp.locate(ToolType.CPP_COMPILER,
              gccPlat.getCppCompiler().getExecutable());
          if (cppSearch.isAvailable()) {
            found = true;
            File cppPath = cppSearch.getTool();
            File cppDir = cppPath.getParentFile();
            String exeName = cppPath.getName();
            String prefix = "";
            int index = exeName.lastIndexOf('-');
            if (index != -1) {
              prefix = exeName.substring(0, exeName.lastIndexOf('-') + 1);
            }
            File nmDir = new File(cppDir, prefix + "nm");
            handleUnixSymbolCheck(nmDir);
            break;
          }
        }
      }
    }

    if (!found) {
      throw new GradleException("Failed to find a toolchain matching the binary to check");
    }
    */
  }
}
