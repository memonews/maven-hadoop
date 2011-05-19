/**
 * Maven Plugin for Hadoop
 * 
 * Copyright (C) 2011 MeMo News AG
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
package com.github.hadoop.maven.plugin.deploy;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.IOUtils;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.project.MavenProject;

/**
 * Deploys an artifact to a given path on hdfs.
 * 
 * <h3>Installation</h3> Install the plugin as follows.
 * 
 * <pre>
 * <plugin>
 * 	<groupId>com.github.maven-hadoop.plugin</groupId>
 * 	<artifactId>maven-hadoop-plugin</artifactId>
 * 	<version>0.20.2-SNAPSHOT</version>
 * 	<configuration>
 * 		<hadoopHome>/var/lib/hadoop-0.20.2-cdh3u0</hadoopHome>
 * 		<path>hdfs://localhost:8020/user/${user.name}</path>
 * 	</configuration>
 * 	<executions>
 * 		<execution>
 * 			<phase>package</phase>
 * 			<goals>
 * 				<goal>pack</goal>
 * 			</goals>
 * 		</execution>
 * 	</executions>
 * </plugin>
 * </pre>
 * 
 * </pre>
 * 
 * <h3>Usage:</h3>
 * 
 * <code>
 * $ mvn hadoop:deploy
 * </code>
 * <p>
 * 
 * @goal deploy
 * @phase deploy
 * @requiresDependencyResolution compile
 * @execute phase="package"
 */
public class DeployMojo extends AbstractMojo {

    /**
     * The maven project.
     * 
     * @parameter expression="${project}"
     * @required
     * @readonly
     */
    protected MavenProject project;
    /**
     * @parameter expression="${project.build.directory}/hadoop-deploy"
     * @readonly
     */
    protected File outputDirectory;

    /**
     * The full hdfs path.
     * 
     * @parameter expression="${hdfs.path}"
     *            default-value="hdfs://localhost:8020/"
     * @required
     */
    private String path;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
	if (this.path == null) {
	    throw new MojoExecutionException("path property needs to be set for the plugin to work");
	}

	final File jarFile = new File(this.outputDirectory.getAbsolutePath() + File.separator + this.project.getArtifactId() + "-" + this.project.getVersion() + "-hdeploy.jar");

	InputStream is;
	try {
	    is = new BufferedInputStream(new FileInputStream(jarFile));
	} catch (final FileNotFoundException e1) {
	    throw new MojoExecutionException("The artifact was not found. Please run goal: hadoop:pack");
	}
	final Configuration conf = new Configuration();
	try {
	    final FileSystem fs = FileSystem.get(URI.create(path), conf);
	    final OutputStream out = fs.create(new Path(path, jarFile.getName()));
	    IOUtils.copyBytes(is, out, conf);
	    getLog().info("Successful transferred artifact to " + path);
	} catch (final IOException e) {
	    throw new MojoExecutionException("error while accessing hdfs");
	}
    }
}
