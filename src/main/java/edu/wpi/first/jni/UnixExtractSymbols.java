package edu.wpi.first.jni;

import java.io.File;
import java.util.Map;

import javax.inject.Inject;

import org.gradle.api.DefaultTask;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.Nested;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskAction;
import org.gradle.api.tasks.WorkResult;
import org.gradle.internal.jvm.Jvm;
import org.gradle.internal.operations.BuildOperationExecutor;
import org.gradle.internal.operations.logging.BuildOperationLogger;
import org.gradle.internal.operations.logging.BuildOperationLoggerFactory;
import org.gradle.internal.work.WorkerLeaseService;
import org.gradle.language.base.internal.compile.Compiler;
import org.gradle.nativeplatform.internal.BuildOperationLoggingCompilerDecorator;
import org.gradle.nativeplatform.internal.DefaultSymbolExtractorSpec;
import org.gradle.nativeplatform.internal.SymbolExtractorSpec;
import org.gradle.nativeplatform.platform.NativePlatform;
import org.gradle.nativeplatform.platform.internal.NativePlatformInternal;
import org.gradle.nativeplatform.toolchain.NativeToolChain;
import org.gradle.nativeplatform.toolchain.internal.CommandLineToolContext;
import org.gradle.nativeplatform.toolchain.internal.CommandLineToolInvocationWorker;
import org.gradle.nativeplatform.toolchain.internal.DefaultCommandLineToolInvocationWorker;
import org.gradle.nativeplatform.toolchain.internal.DefaultMutableCommandLineToolContext;
import org.gradle.nativeplatform.toolchain.internal.MutableCommandLineToolContext;
import org.gradle.nativeplatform.toolchain.internal.NativeToolChainInternal;
import org.gradle.nativeplatform.toolchain.internal.ToolType;
import org.gradle.process.internal.ExecActionFactory;

public class UnixExtractSymbols extends DefaultTask {
  private final RegularFileProperty binaryFile;
  private final RegularFileProperty symbolFile;
  private final Property<NativePlatform> targetPlatform;
  private final Property<NativeToolChain> toolChain;

  private final ExecActionFactory execActionFactory;
  private final BuildOperationExecutor buildOperationExecutor;
  private final WorkerLeaseService workerLeaseService;

  @Inject
  public UnixExtractSymbols(ExecActionFactory execActionFactory, BuildOperationExecutor buildOperationExecutor, WorkerLeaseService workerLeaseService) {
    this.execActionFactory = execActionFactory;
    ObjectFactory objectFactory = getProject().getObjects();

    this.binaryFile = objectFactory.fileProperty();
    this.symbolFile = objectFactory.fileProperty();
    this.targetPlatform = objectFactory.property(NativePlatform.class);
    this.toolChain = objectFactory.property(NativeToolChain.class);
    this.buildOperationExecutor = buildOperationExecutor;
    this.workerLeaseService = workerLeaseService;

  }

/**
     * The file to extract debug symbols from.
     */
    @InputFile
    @PathSensitive(PathSensitivity.NONE)
    public RegularFileProperty getBinaryFile() {
        return binaryFile;
    }

    /**
     * The destination file to extract debug symbols into.
     */
    @OutputFile
    public RegularFileProperty getSymbolFile() {
        return symbolFile;
    }

    /**
     * The tool chain used for extracting symbols.
     *
     * @since 4.7
     */
    @Internal
    public Property<NativeToolChain> getToolChain() {
        return toolChain;
    }

    /**
     * The platform for the binary.
     *
     * @since 4.7
     */
    @Nested
    public Property<NativePlatform> getTargetPlatform() {
        return targetPlatform;
    }

    @TaskAction
    public void extractSymbols() {
      BuildOperationLogger operationLogger = getServices().get(BuildOperationLoggerFactory.class).newOperationLogger(getName(), getTemporaryDir());

      SymbolExtractorSpec spec = new DefaultSymbolExtractorSpec();
      spec.setBinaryFile(binaryFile.get().getAsFile());
      spec.setSymbolFile(symbolFile.get().getAsFile());
      spec.setOperationLogger(operationLogger);

      Compiler<SymbolExtractorSpec> symbolExtractor = createCompiler();
      symbolExtractor = BuildOperationLoggingCompilerDecorator.wrap(symbolExtractor);
      WorkResult result = symbolExtractor.execute(spec);
      setDidWork(result.getDidWork());
    }

    private Compiler<SymbolExtractorSpec> createCompiler() {
        File cppPath = ((NativeToolChainInternal)toolChain.get()).select((NativePlatformInternal)targetPlatform.get()).locateTool(ToolType.SYMBOL_EXTRACTOR).getTool();
        File cppDir = new File(cppPath.getParentFile().getParentFile().toString(), "x64");
        if (cppPath.toString().contains("Microsoft Visual Studio 14.0")) {
          cppDir = new File(cppPath.getParentFile().getParentFile().toString(), "amd64");
        }

        File dumpbinDir = new File(cppDir, "dumpbin.exe");
        CommandLineToolInvocationWorker commandLineToolInvocationWorker = new DefaultCommandLineToolInvocationWorker("C++ Symbols", dumpbinDir, execActionFactory);
        return new WindowsSymbolExtractor(buildOperationExecutor, commandLineToolInvocationWorker, context(), workerLeaseService);

    }

    private CommandLineToolContext context(/*CommandLineToolConfigurationInternal commandLineToolConfiguration*/) {
      MutableCommandLineToolContext invocationContext = new DefaultMutableCommandLineToolContext();
      // The visual C++ tools use the path to find other executables
      // TODO:ADAM - restrict this to the specific path for the target tool
     // invocationContext.addPath(visualCpp.getPath());
      //invocationContext.addPath(sdk.getPath());
      // Clear environment variables that might effect cl.exe & link.exe
      clearEnvironmentVars(invocationContext, "INCLUDE", "CL", "LIBPATH", "LINK", "LIB");

      //invocationContext.setArgAction(commandLineToolConfiguration.getArgAction());
      return invocationContext;
  }
  private void clearEnvironmentVars(MutableCommandLineToolContext invocation, String... names) {
    // TODO: This check should really be done in the compiler process
    Map<String, ?> environmentVariables = Jvm.current().getInheritableEnvironmentVariables(System.getenv());
    for (String name : names) {
        Object value = environmentVariables.get(name);
        if (value != null) {
            //VisualCppToolChain.LOGGER.debug("Ignoring value '{}' set for environment variable '{}'.", value, name);
            invocation.addEnvironmentVar(name, "");
        }
    }
}

}
