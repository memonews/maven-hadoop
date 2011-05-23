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
package com.github.hadoop.maven.plugin.pack;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.OutputStream;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;

/**
 * Writes jars.
 * 
 */
public class JarWriter {

    /**
     * Given a root directory, this writes the contents of the same as a jar
     * file. The path to files inside the jar are relative paths, relative to
     * the root directory specified.
     * 
     * @param jarRootDir
     *            Root Directory that serves as an input to writing the jars.
     * @param os
     *            OutputStream to which the jar is to be packed
     * @param mainClass
     *            the main class as attribute for the manifest
     * @throws FileNotFoundException
     * @throws IOException
     */
    public void packToJar(final File jarRootDir, final OutputStream os, final String mainClass) throws FileNotFoundException, IOException {
	final Manifest manifest = new Manifest();
	manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
	manifest.getMainAttributes().put(Attributes.Name.MAIN_CLASS, mainClass);

	final JarOutputStream target = new JarOutputStream(os, manifest);
	for (final File nestedFile : jarRootDir.listFiles()) {
	    add(jarRootDir.getPath().replace("\\", "/"), nestedFile, target);
	}
	target.close();
    }

    private void add(final String prefix, final File source, final JarOutputStream target) throws IOException {
	BufferedInputStream in = null;
	try {
	    if (source.isDirectory()) {
		String name = source.getPath().replace("\\", "/");
		if (!name.isEmpty()) {
		    if (!name.endsWith("/")) {
			name += "/";
		    }
		    final JarEntry entry = new JarEntry(name.substring(prefix.length() + 1));
		    entry.setTime(source.lastModified());
		    target.putNextEntry(entry);
		    target.closeEntry();
		}
		for (final File nestedFile : source.listFiles()) {
		    add(prefix, nestedFile, target);
		}
		return;
	    }

	    final String jarentryName = source.getPath().replace("\\", "/").substring(prefix.length() + 1);
	    final JarEntry entry = new JarEntry(jarentryName);
	    entry.setTime(source.lastModified());
	    target.putNextEntry(entry);
	    in = new BufferedInputStream(new FileInputStream(source));

	    final byte[] buffer = new byte[1024];
	    while (true) {
		final int count = in.read(buffer);
		if (count == -1) {
		    break;
		}
		target.write(buffer, 0, count);
	    }
	    target.closeEntry();
	} finally {
	    if (in != null) {
		in.close();
	    }
	}
    }
}
