# maven-mojo-jojo
Repository with a small sample project illustrating a problem with Maven's MojoExecutor when building multi-threaded

## Prerequisites
* [Apache Maven 3](https://maven.apache.org/) (tested with 3.3.9 and 3.4.0-SNAPSHOT)
* Java (duh?)

## Reproduction steps

1. Clone this repository (`git clone https://github.com/fvanderveen/maven-mojo-jojo.git`)
2. Make sure the clone works single-threaded: `mvn clean package`. (This should succeed)
3. Clean the workspace (`mvn clean`)
4. Attempt multi-threaded compilation with at least 2 threads (`mvn package -T2`)

Step 4 will fail (most of the time?) on a race condition, causing it to no longer have junit on the compile classpath for the `test-compile` execution of the `maven-compiler-plugin`.

### Example failure output:
```
[BuilderThread 3] [INFO] -------------------------------------------------------------
[BuilderThread 3] [ERROR] COMPILATION ERROR : 
[BuilderThread 3] [INFO] -------------------------------------------------------------
[BuilderThread 3] [ERROR] /usr/maven-mojo-jojo/sub-parent-a/module-a2/src/test/java/maven/bug/modulea/ModuleA2Test.java:[3,17] package org.junit does not exist
[BuilderThread 3] [ERROR] /usr/maven-mojo-jojo/sub-parent-a/module-a2/src/test/java/maven/bug/modulea/ModuleA2Test.java:[6,10] cannot find symbol
  symbol:   class Test
  location: class maven.bug.modulea.ModuleA2Test
[BuilderThread 3] [INFO] 2 errors 
[BuilderThread 3] [INFO] -------------------------------------------------------------
```

## Critical parts of the reproduction

This breaks if, and only if, the following events happen in the following order:

1. Mojo configuration for the `maven-compiler-plugin:test-compile` in `module-a2` is done. This will configure the resolved artifacts for the project.
2. Due to the `maven-javadoc-plugin` in `sub-parent-b` defines `aggregator = true` goal _and_ `requiresDependencyResolution = ResolutionScope.COMPILE`, the `MojoExecutor` class will start resolving dependencies for _all_ projects in the current `MavenSession` (since `getProjects()` returns all projects that are included to be built). This will _reset_ the resolved artifacts for the project `module-a2` to the `compile` scope dependencies (and thus missing the `test` scoped ones)
3. The `maven-compiler-plugin` starts, and in its mojo execution queries the `getTestClasspathElements` of the `module-a2` project. Since the resolved artifacts have been reset to the `compile` dependencies, this will result in a classpath without `junit:junit:4.12`, causing javac to fail.

Due to this order, the sub-parent `sub-parent-b` has a `maven-javadoc-plugin` execution bound to the `test-compile` phase; increasing the chances of this problem occurring.
