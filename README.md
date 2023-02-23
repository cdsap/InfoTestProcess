# Info Test Process Plugin
Includes information about Test processes in the Build Scans.
The plugin is compatible with configuration cache.

## Usage
Apply the plugin in the main `build.gradle(.kts)` configuration file:

#### Kotlin
Using the plugins DSL:
``` groovy
plugins {
  id("io.github.cdsap.testprocess") version "0.1.1"
}
```

Using legacy plugin application:
``` groovy
buildscript {
  repositories {
    gradlePluginPortal()
  }
  dependencies {
    classpath("io.github.cdsap:testprocess:0.1.1")
  }
}

apply(plugin = "io.github.cdsap.testprocess")
```

#### Groovy
Using the plugins DSL:
``` groovy
plugins {
  id "io.github.cdsap.testprocess" version "0.1.1"
}

```

Using legacy plugin application:
``` groovy
buildscript {
  repositories {
    gradlePluginPortal()
  }
  dependencies {
    classpath "io.github.cdsap:testprocess:0.1.1"
  }
}

apply plugin: "io.github.cdsap.testprocess"
```
## Output
### Build Scans
If you are using Gradle Enterprise, the information about the Test processes will be included as custom value in the
Build Scan:

![](images/buildscan.png)


## Requirements
* Gradle 7.5

## Libraries
* com.gradle.enterprise:com.gradle.enterprise.gradle.plugin
