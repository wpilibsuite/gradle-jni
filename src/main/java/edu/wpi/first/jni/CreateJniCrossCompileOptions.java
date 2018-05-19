package edu.wpi.first.jni;

import java.util.List;

import groovy.lang.Closure;

public class CreateJniCrossCompileOptions extends Closure<JniCrossCompileOptions> {

  public CreateJniCrossCompileOptions() {
    super(null);
  }

  private static final long serialVersionUID = -2465995793739686728L;

  public JniCrossCompileOptions doCall(String a) {
    return new JniCrossCompileOptions(a, null);
  }

  public JniCrossCompileOptions doCall(String a, String b) {
    return new JniCrossCompileOptions(a, b, null);
  }

  public JniCrossCompileOptions doCall(String a, List<String> b) {
    return new JniCrossCompileOptions(a, b);
  }

  public JniCrossCompileOptions doCall(String a, String b, List<String> c) {
    return new JniCrossCompileOptions(a, b, c);
  }
}
