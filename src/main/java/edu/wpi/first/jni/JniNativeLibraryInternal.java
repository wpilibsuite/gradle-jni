package edu.wpi.first.jni;

import java.util.List;

public interface JniNativeLibraryInternal {
    List<String> getJniHeaderLocations();
    void setJniHeaderLocations(List<String> location);
}
