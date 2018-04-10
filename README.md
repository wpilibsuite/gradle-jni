# gradle-jni
gradle-jni is a utility library for enabling easy to build JNI compatible plugins

```gradle
plugins {
  id 'edu.wpi.first.gradle-jni' version '0.0.2'
}

model {
  components {
    library(JniNativeLibrarySpec) { // Use JniNativeLibrarySpec to get a JNI library
      enableCheckTask true // Set to true to enable a JNI check task. This will search all generated JNI headers, and check to ensure their symbols exist in the native library
      javaCompileTasks << compileJava // set javaCompileTasks to any java compile tasks that contain your JNI classes. It is a list of tasks
      jniCrossCompileOptions << new JniCrossCompileOptions('linux', 'athena', ["${rootDir}/src/arm-linux-jni".toString(), "${rootDir}/src/arm-linux-jni/linux".toString()])
      // Line above is used to add custom system includes for specific operating systems and architectures.
      // The order is (`operatingSystem`, `architecture`, `List<String> of directories`)
    }
  }
}
```
