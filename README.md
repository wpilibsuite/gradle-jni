# gradle-jni
gradle-jni is a utility library for enabling easy to build JNI compatible plugins

```gradle
import edu.wpi.first.jni.JniNativeLibrarySpec
import edu.wpi.first.jni.JniCrossCompileOptions

buildscript {
    repositories {
        mavenLocal()
        maven {
            url "https://plugins.gradle.org/m2/"
        }
    }
    dependencies {
        classpath 'gradle.plugin.edu.wpi.first:gradle-jni:0.0.1'
    }
}

model {
  components {
    library(JniNativeLibrarySpec) { // Use JniNativeLibrarySpec to get a JNI library
      javaCompileTasks << compileJava // set javaCompileTasks to any java compile tasks that contain your JNI classes. It is a list of tasks
      jniCrossCompileOptions << new JniCrossCompileOptions('linux', 'athena', ["${rootDir}/src/arm-linux-jni".toString(), "${rootDir}/src/arm-linux-jni/linux".toString()])
      // Line above is used to add custom system includes for specific operating systems and architectures.
      // The order is (`operatingSystem`, `architecture`, `List<String> of directories`)
    }
  }
}
```
