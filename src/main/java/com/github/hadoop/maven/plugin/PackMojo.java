/**
 * Maven Plugin for Hadoop
 * 
 * Copyright (C) 2010 Karthik Kumar
 * 
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.github.hadoop.maven.plugin;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Properties;
import java.util.Set;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.resolver.ArtifactNotFoundException;
import org.apache.maven.artifact.resolver.ArtifactResolutionException;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.artifact.InvalidDependencyVersionException;

/**
 * Pack the dependencies into an archive to be fed to the hadoop jar command.
 * <p>
 * Important: The list of dependencies in the hadoop CP are ignored / filtered
 * to avoid namespace collision.
 * <p>
 * Internal Workings:
 * <ul>
 * <li>To go hand in hand with the compile target, this copies the dependencies
 * to target/hadoop-deploy/lib directory.</li>
 * <li>The dependencies that are already present in $HADOOP_HOME/lib/*.jar , if
 * present in the project's dependencies are ignored.</li>
 * </ul>
 * <p>
 * <h3>Installation</h3> Install the plugin as follows.
 * 
 * <pre>
 * 
 *  &lt;plugin&gt; 
 *         &lt;groupId&gt;com.github.maven-hadoop.plugin&lt;/groupId&gt;
 *         &lt;artifactId&gt;maven-hadoop-plugin&lt;/artifactId&gt;
 *         &lt;version&gt;0.20.0&lt;/version&gt;
 *         &lt;configuration&gt;
 *           &lt;hadoopHome&gt;/opt/software/hadoop&lt;/hadoopHome&gt;
 *         &lt;/configuration&gt;
 *      &lt;/plugin&gt;
 * </pre>
 * 
 * <h3>Usage:</h3>
 * 
 * <code>
 * $ mvn compile hadoop:pack
 * </code>
 * <p>
 * Pack the dependencies of the project , along with the class files in a single
 * jar , amenable to be submitted to a M-R instance
 * 
 * @goal pack
 * @requiresDependencyResolution compile
 * @execute phase="compile"
 * 
 */
public class PackMojo extends AbstractMojo {

  /**
   * The maven project.
   * 
   * @parameter expression="${project}"
   * @required
   * @readonly
   */
  protected MavenProject project;

  /**
   * Hadoop Configuration properties
   * 
   * @parameter
   */
  private Properties hadoopConfiguration;

  /**
   * @parameter expression="${project.build.directory}/hadoop-deploy"
   * @readonly
   */
  protected File outputDirectory;

  /**
   * HADOOP_HOME directory that points to the installation of the hadoop on
   * which the job is scheduled to be run
   * 
   * @parameter
   */
  private File hadoopHome;

  /**
   * Writes jar files.
   */
  private JarWriter jarWriter = new JarWriter();

  public void execute() throws MojoExecutionException {
    if (this.hadoopHome == null) {
      throw new MojoExecutionException(
          "hadoopHome property needs to be set for the plugin to work");
    }
    getLog().info("Hadoop home set to " + this.hadoopHome);
    try {
      File jarRootDir = createHadoopDeployArtifacts();
      File jarName = packToJar(jarRootDir);
      getLog().info("Hadoop  job jar file available at " + jarName);
    } catch (IOException e) {
      throw new IllegalStateException("Error creating output directory", e);
    }
  }

  /**
   * Create the hadoop deploy artifacts
   * 
   * @throws IOException
   * @return File that contains the root of jar file to be packed.
   * @throws InvalidDependencyVersionException
   * @throws ArtifactNotFoundException
   * @throws ArtifactResolutionException
   */
  private File createHadoopDeployArtifacts() throws IOException {
    FileUtils.deleteDirectory(outputDirectory);
    File rootDir = new File(outputDirectory.getAbsolutePath() + File.separator
        + "root");
    FileUtils.forceMkdir(rootDir);

    File jarlibdir = new File(rootDir.getAbsolutePath() + File.separator
        + "lib");
    FileUtils.forceMkdir(jarlibdir);

    File classesdir = new File(project.getBuild().getDirectory()
        + File.separator + "classes");
    FileUtils.copyDirectory(classesdir, rootDir);
    List<File> dependencies = this.getScopedDependencies("compile");
    List<File> filteredDependencies = this.filterDependencies(dependencies);
    getLog().info(
        "Dependencies of this project independent of hadoop classpath "
            + filteredDependencies);
    for (File dependency : filteredDependencies) {
      FileUtils.copyFileToDirectory(dependency, jarlibdir);
    }
    return rootDir;
  }

  /**
   * Filter hadoop dependencies from the classpath.
   * 
   * @param dependencies
   * @return
   */
  private List<File> filterDependencies(List<File> dependencies) {
    List<String> hadoopDependencies = getHadoopDependencies();
    List<File> output = new ArrayList<File>();
    for (final File inputDependency : dependencies) {
      final String name = inputDependency.getName();
      if (name.startsWith("hadoop") || hadoopDependencies.contains(name)) {
        continue; // skip other dependencies in hadoop cp as well.
      } else {
        output.add(inputDependency);
      }
    }
    return output;

  }

  /**
   * Retrieve the list of hadoop dependencies since we want to perform a set
   * operation of A - B before packing our jar.
   * 
   * @return
   */
  private List<String> getHadoopDependencies() {
    File hadoopLib = new File(this.hadoopHome.getAbsoluteFile()
        + File.separator + "lib");
    Collection<File> hadoopDependencies = FileUtils.listFiles(hadoopLib,
        new String[] { "jar" }, true);
    List<String> outputJars = new ArrayList<String>();
    for (final File hadoopDependency : hadoopDependencies) {
      outputJars.add(hadoopDependency.getName());
    }
    return outputJars;
  }

  /**
   * Retrieve the project dependencies.
   * 
   * @param scope
   *          Scope of the dependency to resolve to .
   * @return
   */
  @SuppressWarnings("unchecked")
  private List<File> getScopedDependencies(final String scope) {
    List<File> jarDependencies = new ArrayList<File>();
    final Set<Artifact> artifacts = project.getDependencyArtifacts();
    for (Artifact artifact : artifacts) {
      if ("jar".equals(artifact.getType())) {
        File file = artifact.getFile();
        if (file != null && file.exists()) {
          jarDependencies.add(file);
        } else {
          getLog().warn("Dependency file not found: " + artifact);
        }
      }
    }
    return jarDependencies;
  }

  private File packToJar(final File jarRootDir) throws FileNotFoundException,
      IOException {
    File jarName = new File(this.outputDirectory.getAbsolutePath()
        + File.separator + this.project.getArtifactId() + "-hdeploy.jar");
    FileOutputStream fos = null;
    try {
      fos = new FileOutputStream(jarName);
      this.jarWriter.packToJar(jarRootDir, fos);
      return jarName;
    } finally {
      IOUtils.closeQuietly(fos);
    }

  }
}
