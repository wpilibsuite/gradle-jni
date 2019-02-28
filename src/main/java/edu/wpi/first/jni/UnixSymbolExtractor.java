package edu.wpi.first.jni;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import org.gradle.api.Action;
import org.gradle.internal.operations.BuildOperationExecutor;
import org.gradle.internal.operations.BuildOperationQueue;
import org.gradle.internal.work.WorkerLeaseService;
import org.gradle.nativeplatform.internal.SymbolExtractorSpec;
import org.gradle.nativeplatform.toolchain.internal.AbstractCompiler;
import org.gradle.nativeplatform.toolchain.internal.ArgsTransformer;
import org.gradle.nativeplatform.toolchain.internal.CommandLineToolContext;
import org.gradle.nativeplatform.toolchain.internal.CommandLineToolInvocation;
import org.gradle.nativeplatform.toolchain.internal.CommandLineToolInvocationWorker;

public class UnixSymbolExtractor extends AbstractCompiler<SymbolExtractorSpec> {
  public UnixSymbolExtractor(BuildOperationExecutor buildOperationExecutor, CommandLineToolInvocationWorker commandLineToolInvocationWorker, CommandLineToolContext invocationContext, WorkerLeaseService workerLeaseService) {
    super(buildOperationExecutor, commandLineToolInvocationWorker, invocationContext, new SymbolExtractorArgsTransformer(), false, workerLeaseService);
  }

  @Override
  protected Action<BuildOperationQueue<CommandLineToolInvocation>> newInvocationAction(final SymbolExtractorSpec spec, List<String> args) {

      final CommandLineToolInvocation invocation = newInvocation(
          "Extracting symbols from " + spec.getBinaryFile().getName(), args, spec.getOperationLogger());

      return new Action<BuildOperationQueue<CommandLineToolInvocation>>() {
          @Override
          public void execute(BuildOperationQueue<CommandLineToolInvocation> buildQueue) {
              buildQueue.setLogLocation(spec.getOperationLogger().getLogLocation());
              buildQueue.add(invocation);
          }
      };
  }

  @Override
  protected void addOptionsFileArgs(List<String> args, File tempDir) { }

  private static class SymbolExtractorArgsTransformer implements ArgsTransformer<SymbolExtractorSpec> {
    @Override
    public List<String> transform(SymbolExtractorSpec spec) {
        //SymbolExtractorOsConfig symbolExtractorOsConfig = SymbolExtractorOsConfig.current();
        List<String> args = new ArrayList<>();
        args.addAll(spec.getArgs());
        args.add(spec.getBinaryFile().getAbsolutePath());
        return args;
    }
}
}
