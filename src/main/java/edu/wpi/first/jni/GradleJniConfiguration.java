package edu.wpi.first.jni;

import java.util.ArrayList;
import java.util.List;

import org.gradle.nativeplatform.toolchain.GccPlatformToolChain;
import org.gradle.nativeplatform.toolchain.VisualCppPlatformToolChain;
import org.gradle.nativeplatform.toolchain.internal.msvcpp.VisualStudioLocator;

public class GradleJniConfiguration {
  public VisualStudioLocator vsLocator;
  public List<VisualCppPlatformToolChain> visualCppPlatforms = new ArrayList<>();
  public List<GccPlatformToolChain> gccLikePlatforms = new ArrayList<>();
}
