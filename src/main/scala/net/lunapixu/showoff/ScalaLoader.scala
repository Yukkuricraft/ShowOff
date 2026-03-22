package net.lunapixu.showoff

import io.papermc.paper.plugin.loader.PluginClasspathBuilder
import io.papermc.paper.plugin.loader.PluginLoader
import io.papermc.paper.plugin.loader.library.impl.MavenLibraryResolver
import org.eclipse.aether.artifact.DefaultArtifact
import org.eclipse.aether.graph.Dependency
import org.eclipse.aether.repository.RemoteRepository

class ScalaLoader extends PluginLoader:
  override def classloader(classpathBuilder: PluginClasspathBuilder): Unit = 
    val resolver = MavenLibraryResolver()
    resolver.addRepository(RemoteRepository.Builder("paper", "default", "https://repo.papermc.io/repository/maven-public/").build());
    // Paper does not have the full Scala 3 library so it needs to be added to classpath
    resolver.addDependency(Dependency(DefaultArtifact("org.scala-lang:scala3-library_3:3.7.2"), null))

    classpathBuilder.addLibrary(resolver)