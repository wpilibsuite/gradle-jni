package edu.wpi.first.jni;

import java.util.List;

public class JniCrossCompileOptions {
    public final String operatingSystem;
    public final String architecture;
    public final List<String> jniHeaderLocations;

    public JniCrossCompileOptions(String os, String arch, List<String> locs) {
        operatingSystem = os;
        architecture = arch;
        jniHeaderLocations = locs;
    }
}
