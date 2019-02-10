package edu.wpi.first.jni;

import java.io.ByteArrayOutputStream;
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
import org.gradle.api.file.FileTree;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;
import org.gradle.internal.os.OperatingSystem;
import org.gradle.nativeplatform.SharedLibraryBinarySpec;
import org.gradle.nativeplatform.platform.internal.NativePlatformInternal;
import org.gradle.nativeplatform.toolchain.GccPlatformToolChain;
import org.gradle.nativeplatform.toolchain.NativeToolChain;
import org.gradle.nativeplatform.toolchain.VisualCpp;
import org.gradle.nativeplatform.toolchain.VisualCppPlatformToolChain;
import org.gradle.nativeplatform.toolchain.internal.ToolType;
import org.gradle.nativeplatform.toolchain.internal.msvcpp.VisualStudioInstall;
import org.gradle.nativeplatform.toolchain.internal.tools.CommandLineToolSearchResult;
import org.gradle.nativeplatform.toolchain.internal.tools.ToolSearchPath;
import org.gradle.platform.base.internal.toolchain.SearchResult;

public class JniSymbolCheck extends DefaultTask {
  @OutputFile
  public RegularFileProperty foundSymbols;

  @Input
  public SharedLibraryBinarySpec binaryToCheck;

  @Input
  public JniNativeLibrarySpec jniComponent;

  @Inject
  public JniSymbolCheck(ObjectFactory factory) {
    foundSymbols = factory.fileProperty();
  } 

  private List<String> getExpectedSymbols() {
    // Get expected symbols
    List<String> symbolList = new ArrayList<>();
    for (String loc : jniComponent.getJniHeaderLocations().values()) {
      FileTree tree = getProject().fileTree(loc);
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

  private void handleWindowsSymbolCheck(File dumpBinLoc) {
    File symbolFile = foundSymbols.getAsFile().get();
    symbolFile.getParentFile().mkdirs();

    List<String> symbolList = getExpectedSymbols();

    String library = binaryToCheck.getSharedLibraryFile().getAbsolutePath();

    ByteArrayOutputStream dumpbinOutput = new ByteArrayOutputStream();
    getProject().exec(exec -> {
      exec.commandLine(dumpBinLoc, "/NOLOGO", "/EXPORTS", library);
      exec.setStandardOutput(dumpbinOutput);
    });

    List<String> missingSymbols = new ArrayList<>();

    String dumpBinSymbols = dumpbinOutput.toString();

    for (String symbol : symbolList) {
      if (!dumpBinSymbols.contains(symbol + " =") && !dumpBinSymbols.contains(symbol + "@")) {
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
    try (FileWriter writer = new FileWriter(symbolFile)) {
      for (String str : symbolList) {
        writer.write(str);
        writer.write('\n');
      }
    } catch (IOException ex) {
      System.out.println(ex);
    }
  }

  private void handleUnixSymbolCheck(File nmLoc) {
    File symbolFile = foundSymbols.getAsFile().get();
    symbolFile.getParentFile().mkdirs();

    List<String> symbolList = getExpectedSymbols();

    String library = binaryToCheck.getSharedLibraryFile().getAbsolutePath();

    ByteArrayOutputStream nmOutput = new ByteArrayOutputStream();
    getProject().exec(exec -> {
      exec.commandLine(nmLoc, library);
      exec.setStandardOutput(nmOutput);
    });

    String nmSymbols = nmOutput.toString().replace("\r", "");
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
    try (FileWriter writer = new FileWriter(symbolFile)) {
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
    GradleJniConfiguration ext = getProject().getExtensions().getByType(GradleJniConfiguration.class);
    boolean found = false;

    NativeToolChain toolChain = binaryToCheck.getToolChain();

    for (VisualCppPlatformToolChain msvcPlat : ext.visualCppPlatforms) {
      if (msvcPlat.getPlatform().equals(binaryToCheck.getTargetPlatform())) {
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
  }
}
