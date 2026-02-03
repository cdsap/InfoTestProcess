# Info Test Process Plugin
Includes information about Test processes in the Build Scans.
The plugin is compatible with configuration cache.

> [!NOTE]
> Since version 1.0.0 the plugin is applied in `settings.gradle(.kts)`

## Usage
Apply the plugin in the main `settings.gradle(.kts)` configuration file:

#### Kotlin
Using the plugins DSL:
``` groovy
plugins {
  id("io.github.cdsap.testprocess") version "1.0.1"
}
```

## Output
### Build Scans
If you are using Gradle Enterprise, the information:
* Total number of processes created
* Processes by task
* Information about the process

![](images/buildscan.png)


## Requirements
* Gradle 8
* Develocity

---

### Trademark Notice
Gradle®, Develocity®, Build Scan®, and Gradle Enterprise® are registered trademarks of Gradle, Inc.
