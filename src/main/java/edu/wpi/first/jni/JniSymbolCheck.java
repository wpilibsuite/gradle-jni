package edu.wpi.first.jni;

import static edu.wpi.first.jni.net.fornwall.jelf.ElfSection.SHT_DYNSYM;
import static edu.wpi.first.jni.net.fornwall.jelf.ElfSymbol.BINDING_GLOBAL;
import static edu.wpi.first.jni.net.fornwall.jelf.ElfSymbol.STT_FUNC;

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
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.FileTree;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;
import org.gradle.nativeplatform.SharedLibraryBinarySpec;
import org.gradle.nativeplatform.platform.OperatingSystem;
import org.gradle.nativeplatform.platform.internal.NativePlatformInternal;
import org.gradle.nativeplatform.toolchain.NativeToolChain;
import org.gradle.nativeplatform.toolchain.VisualCpp;
import org.gradle.nativeplatform.toolchain.VisualCppPlatformToolChain;
import org.gradle.nativeplatform.toolchain.internal.msvcpp.VisualStudioInstall;
import org.gradle.platform.base.internal.toolchain.SearchResult;

import edu.wpi.first.jni.net.fornwall.jelf.ElfException;
import edu.wpi.first.jni.net.fornwall.jelf.ElfFile;
import edu.wpi.first.jni.net.fornwall.jelf.ElfSection;
import edu.wpi.first.jni.net.fornwall.jelf.ElfSymbol;

public class JniSymbolCheck extends DefaultTask {
  private final RegularFileProperty foundSymbols;

  @OutputFile
  public RegularFileProperty getFoundSymbols() {
    return foundSymbols;
  }

  private final ListProperty<String> skipSymbols;

  @Input
  public ListProperty<String> getSkipCheckSymbols() {
    return skipSymbols;
  }

  private SharedLibraryBinarySpec binaryToCheck;

  public void setBinaryToCheck(SharedLibraryBinarySpec binary) {
    binaryToCheck = binary;
  }

  @Internal
  public SharedLibraryBinarySpec getBinaryToCheck() {
    return binaryToCheck;
  }

  private JniNativeLibrarySpec jniComponent;

  public void setJniComponent(JniNativeLibrarySpec jniComponent) {
    this.jniComponent = jniComponent;
  }

  @Internal
  public JniNativeLibrarySpec getJniComponent() {
    return jniComponent;
  }

  @Inject
  public JniSymbolCheck(ObjectFactory factory) {
    foundSymbols = factory.fileProperty();
    skipSymbols = factory.listProperty(String.class);
    skipSymbols.convention(List.of());
  }

  private List<String> getExpectedSymbols() {
    // Get expected symbols
    List<String> symbolList = new ArrayList<>();
    for (DirectoryProperty loc : jniComponent.getJniHeaderLocations().values()) {
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
    List<String> skip = skipSymbols.get();
    if (!skip.isEmpty()) {
      symbolList.removeAll(skip);
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

  private void handleElfSymbolCheck() throws ElfException, IOException {
    File symbolFile = foundSymbols.getAsFile().get();
    symbolFile.getParentFile().mkdirs();

    List<String> symbolList = getExpectedSymbols();

    File library = binaryToCheck.getSharedLibraryFile();

    ElfFile elfFile = ElfFile.fromFile(library);

    List<String> symbols = new ArrayList<>();

    for (int i = 0; i < elfFile.num_sh; i++) {
      ElfSection sh = elfFile.getSection(i);
      int numSymbols = sh.getNumberOfSymbols();
      if (sh.type != SHT_DYNSYM) {
        continue;
      }
      for (int j = 0; j < numSymbols; j++) {
        ElfSymbol sym = sh.getELFSymbol(j);
        if (sym.getType() == STT_FUNC && sym.getBinding() == BINDING_GLOBAL) {
          symbols.add(sym.getName());
        }

      }
    }

    List<String> missingSymbols = new ArrayList<>();

    for (String symbol : symbolList) {
      if (!symbols.contains(symbol)) {
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

  private void handleMacSymbolCheck() {
    File symbolFile = foundSymbols.getAsFile().get();
    symbolFile.getParentFile().mkdirs();

    List<String> symbolList = getExpectedSymbols();

    String library = binaryToCheck.getSharedLibraryFile().getAbsolutePath();

    ByteArrayOutputStream nmOutput = new ByteArrayOutputStream();
    getProject().exec(exec -> {
      exec.commandLine("nm", library);
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

    OperatingSystem targetOs = binaryToCheck.getTargetPlatform().getOperatingSystem();

    if (binaryToCheck.getTargetPlatform().getOperatingSystem().isLinux()) {
      try {
        handleElfSymbolCheck();
      } catch (ElfException | IOException e) {
        throw new GradleException("Failed to parse elf file?", e);
      }
    } else if (targetOs.isMacOsX()) {
      handleMacSymbolCheck();
    } else if (targetOs.isWindows()) {
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
        throw new GradleException("Unable to find toolchain for platform " + toolChain);
      }
    } else {
      throw new GradleException("Platform " + targetOs.getName() + " Is not supported for JNI Symbol Checking");
    }
  }
}
