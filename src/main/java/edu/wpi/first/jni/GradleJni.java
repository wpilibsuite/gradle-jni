package edu.wpi.first.jni;

import groovy.lang.Closure;
import org.gradle.api.GradleException;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.file.FileTree;
import org.gradle.api.tasks.compile.JavaCompile;
import org.gradle.language.base.internal.ProjectLayout;
import org.gradle.language.base.plugins.ComponentModelBasePlugin;
import org.gradle.language.nativeplatform.tasks.AbstractNativeSourceCompileTask;
import org.gradle.model.ModelMap;
import org.gradle.model.Mutate;
import org.gradle.model.RuleSource;
import org.gradle.model.Validate;
import org.gradle.nativeplatform.NativeBinarySpec;
import org.gradle.nativeplatform.SharedLibraryBinarySpec;
import org.gradle.nativeplatform.platform.internal.NativePlatformInternal;
import org.gradle.nativeplatform.toolchain.NativeToolChain;
import org.gradle.nativeplatform.toolchain.NativeToolChainRegistry;
import org.gradle.nativeplatform.toolchain.internal.PlatformToolProvider;
import org.gradle.nativeplatform.toolchain.internal.ToolType;
import org.gradle.nativeplatform.toolchain.internal.gcc.AbstractGccCompatibleToolChain;
import org.gradle.nativeplatform.toolchain.internal.msvcpp.VisualCppToolChain;
import org.gradle.nativeplatform.toolchain.internal.tools.ToolRegistry;
import org.gradle.platform.base.*;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;


class GradleJni implements Plugin<Project> {
    public void apply(Project project) {
        project.getPluginManager().apply(ComponentModelBasePlugin.class);
        Closure c = new Closure(null) {
            public Object doCall(String a, String b, List<String> c) {
                return new JniCrossCompileOptions(a, b, c);
            }
        };
        project.getExtensions().getExtraProperties().set("JniNativeLibrarySpec", JniNativeLibrarySpec.class);
        project.getExtensions().getExtraProperties().set("JniCrossCompileOptions", c);
    }

    static class Rules extends RuleSource {
        @ComponentType
        void registerJni(TypeBuilder<JniNativeLibrarySpec> builder) {
            builder.defaultImplementation(DefaultJniNativeLibrary.class);
            builder.internalView(JniNativeLibraryInternal.class);
        }

        private void setupGccCheckTask(String prefix, NativeBinarySpec binary,
                                       ModelMap<Task> tasks, JniNativeLibrarySpec jniComponent,
                                       Project project) {
            if (!binary.isBuildable()) {
                return;
            }
            if (!(binary instanceof SharedLibraryBinarySpec)) {
                return;
            }
            SharedLibraryBinarySpec sharedBinary = (SharedLibraryBinarySpec) binary;
            String nmPath = prefix + "nm";

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
                task.foundSymbols.set(project.getLayout().getBuildDirectory().file("jnisymbols/" + projName + "/symbols.txt"));
                task.doLast(runner -> {
                    File symbolFile = task.foundSymbols.getAsFile().get();
                    symbolFile.getParentFile().mkdirs();

                    String library = sharedBinary.getSharedLibraryFile().getAbsolutePath();
                    // Get expected symbols
                    List<String> symbolList = new ArrayList<>();
                    for (String loc : jniComponent.getJniHeaderLocations().values()) {
                        FileTree tree = project.fileTree(loc);
                        for (File file : tree) {
                            try (Stream<String> stream = Files.lines(file.toPath())) {
                                stream.map(s -> s.trim())
                                        .filter(s -> !s.isEmpty() && (s.startsWith("JNIEXPORT ") && s.contains("JNICALL")))
                                        .forEach(line -> {
                                            symbolList.add(line.split("JNICALL")[1].trim());
                                        });
                            } catch (IOException e) {
                                continue;
                            }
                        }
                    }

                    ByteArrayOutputStream nmOutput = new ByteArrayOutputStream();
                    project.exec(exec -> {
                        exec.commandLine(nmPath, library);
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
                        for(String str: symbolList) {
                            writer.write(str);
                            writer.write('\n');
                        }
                    } catch (IOException ex) {
                        System.out.println(ex);
                    }
                });
            });
            binary.checkedBy(tasks.get(checkTaskName));
        }

        @Mutate
        void addJniDependencies(ModelMap<Task> tasks, ComponentSpecContainer components, ProjectLayout projectLayout, NativeToolChainRegistry toolChains) {

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

                        NativeToolChain toolchain = binary.getToolChain();

                        if (component.getEnableCheckTask()) {
                            if (toolchain instanceof AbstractGccCompatibleToolChain) {
                                AbstractGccCompatibleToolChain gccToolchain = (AbstractGccCompatibleToolChain) toolchain;
                                PlatformToolProvider gp = gccToolchain.select((NativePlatformInternal) binary.getTargetPlatform());

                                try {
                                    Class c = Class.forName("org.gradle.nativeplatform.toolchain.internal.gcc.GccPlatformToolProvider");
                                    Field f = c.getDeclaredField("toolRegistry");
                                    f.setAccessible(true);
                                    ToolRegistry tr = (ToolRegistry) f.get(gp);
                                    f.setAccessible(false);
                                    String cpp = tr.getTool(ToolType.CPP_COMPILER).getExecutable();
                                    String prefix = "";
                                    int index = cpp.lastIndexOf('-');
                                    if (index != -1) {
                                        prefix = cpp.substring(0, cpp.lastIndexOf('-') + 1);
                                    }

                                    setupGccCheckTask(prefix, binary, tasks, component, project);
                                } catch (ClassNotFoundException e) {
                                    System.out.println("Class Not Found");
                                } catch (NoSuchFieldException ex) {
                                    System.out.println("No Fields");
                                } catch (IllegalAccessException ex) {
                                    System.out.println("Illegal access");
                                }
                            } else if (toolchain instanceof VisualCppToolChain) {
                                // TODO: Get MSVC Working
                            }
                        }

                        for (JniCrossCompileOptions config : component.getJniCrossCompileOptions()) {
                            if (binary.getTargetPlatform().getArchitecture().getName() == config.architecture
                                    && binary.getTargetPlatform().getOperatingSystem().getName() == config.operatingSystem) {
                                cross = true;
                                jniFiles.addAll(config.jniHeaderLocations);
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
                                // on El Capitan. We therefore manually test for the darwin folder and include it
                                // if it exists
                                jniFiles.add(base.concat("/darwin").toString());
                            }
                        }

                        binary.lib(new JniSystemDependencySet(jniFiles, project));

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
                    }
                }
            }
        }
    }
}
