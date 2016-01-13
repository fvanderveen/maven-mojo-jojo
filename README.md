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

## Suggested patch/fix for Maven

If I understand the idea behing `MojoExecutor`'s `ensureDependenciesAreResolved` method correctly, I'm assuming it should call `resolveProjectDependencies` only on modules that are housed under the current project. In this sample project, I would expect this method to resolve the dependencies for the modules `module-b1` and `module-b2`, as these are child-modules of `sub-parent-b`.

Currently, this method calls `resolveProjectDependencies` for all `MavenProject` instances returned by `MavenSession#getProjects`. Given the code; this method seems to return all projects included in the build reactor, which without any `--projects` argument boils down to all projects housed by the current parent.

I would suggest (recursively) finding projects that are housed under the current project, and _only_ call `resolveProjectDependencies` for those projects.

A diff for `MojoExecutor` that seems to result in this behaviour would be:
```git
diff --git a/maven-core/src/main/java/org/apache/maven/lifecycle/internal/MojoExecutor.java b/maven-core/src/main/java/org/apache/maven/life
cycle/internal/MojoExecutor.java
index 8524c5e..9dc3424 100644
--- a/maven-core/src/main/java/org/apache/maven/lifecycle/internal/MojoExecutor.java
+++ b/maven-core/src/main/java/org/apache/maven/lifecycle/internal/MojoExecutor.java
@@ -257,14 +257,11 @@ public void ensureDependenciesAreResolved( MojoDescriptor mojoDescriptor, MavenS
 
             if ( dependencyContext.isResolutionRequiredForAggregatedProjects( scopesToCollect, scopesToResolve ) )
             {
-                for ( MavenProject aggregatedProject : session.getProjects() )
+                for ( MavenProject aggregatedProject : collectChildModules(project, session.getProjects()) )
                 {
-                    if ( aggregatedProject != project )
-                    {
-                        lifeCycleDependencyResolver.resolveProjectDependencies( aggregatedProject, scopesToCollect,
-                                                                                scopesToResolve, session, aggregating,
-                                                                                Collections.<Artifact>emptySet() );
-                    }
+                    lifeCycleDependencyResolver.resolveProjectDependencies( aggregatedProject, scopesToCollect,
+                                                                            scopesToResolve, session, aggregating,
+                                                                            Collections.<Artifact>emptySet() );
                 }
             }
         }
@@ -279,6 +276,19 @@ public void ensureDependenciesAreResolved( MojoDescriptor mojoDescriptor, MavenS
         }
     }
 
+    private List<MavenProject> collectChildModules(MavenProject parent, List<MavenProject> projects) {
+        List<MavenProject> children = new ArrayList<>();
+        
+        for (MavenProject project : projects) {
+            if (project.getParent() == parent) {
+                children.add(project);
+                children.addAll(collectChildModules(project, projects));
+            }
+        }
+        
+        return children;
+    }
+    
     private ArtifactFilter getArtifactFilter( MojoDescriptor mojoDescriptor )
     {
         String scopeToResolve = mojoDescriptor.getDependencyResolutionRequired();
```

