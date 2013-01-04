/*
 * Copyright 2002-2012 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.build.gradle

import java.io.File;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.gradle.api.*
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.ProjectDependency;
import org.gradle.api.artifacts.maven.Conf2ScopeMapping
import org.gradle.api.plugins.MavenPlugin
import org.gradle.api.tasks.*
import org.gradle.plugins.ide.eclipse.EclipsePlugin
import org.gradle.plugins.ide.eclipse.model.EclipseClasspath;
import org.gradle.plugins.ide.idea.IdeaPlugin
import org.gradle.api.invocation.*


class SplitPackageDetectorPlugin implements Plugin<Project> {
	public void apply(Project project) {
		Task diagnoseSplitPackages = project.tasks.add('diagnoseSplitPackages', SplitPackageDetectorTask.class)
		diagnoseSplitPackages.setDescription('Detects packages which will be split across JARs')
		//project.tasks.findByName('build').dependsOn(diagnoseSplitPackages)
	}
}

public class SplitPackageDetectorTask extends DefaultTask {
	@InputDirectory
	File inputDir

    @Input
    Map<File, String>  permissibleSplitPackages

	@TaskAction
	public final void diagnoseSplitPackages() {
        def Map<File, File> mergeMap= [:]
		def projects = project.subprojects.findAll { it.plugins.findPlugin(org.springframework.build.gradle.MergePlugin) }.findAll { it.merge.into }
		projects.each { p ->
			println '    > The project directory '+ p.projectDir + ' will merge into ' + p.merge.into.projectDir
            mergeMap.put(p.projectDir, p.merge.into.projectDir)
		}
		def splitFound = new org.springframework.build.gradle.SplitPackageDetector(inputDir.absolutePath, mergeMap, project.logger).diagnoseSplitPackages();
		assert !splitFound // see error log messages for details of split packages
	}
}

class SplitPackageDetector {

	private static final String HIDDEN_DIRECTORY_PREFIX = "."

	private static final String JAVA_FILE_SUFFIX = ".java"

	private static final String SRC_MAIN_JAVA = "src" + File.separator + "main" + File.separator + "java"

    private final Map<File, File> mergeMap

	private final Map<File, Set<String>> pkgMap = [:]

	private final logger

	SplitPackageDetector(baseDir, mergeMap, logger) {
        this.mergeMap = mergeMap
		this.logger = logger
		dirList(baseDir).each { File dir ->
			def packages = getPackagesInDirectory(dir)
			if (!packages.isEmpty()) {
				pkgMap.put(dir, packages)
			}
		}
	}

	private File[] dirList(String dir) {
		dirList(new File(dir))
	}

	private File[] dirList(File dir) {
		dir.listFiles({ file -> file.isDirectory() && !file.getName().startsWith(HIDDEN_DIRECTORY_PREFIX) } as FileFilter)
	}

	private Set<String> getPackagesInDirectory(File dir) {
		def pkgs = new HashSet<String>()
		addPackagesInDirectory(pkgs, new File(dir, SRC_MAIN_JAVA), "")
		return pkgs;
	}

	boolean diagnoseSplitPackages() {
		def splitFound = false;
		def dirs = pkgMap.keySet().toArray()
		def numDirs = dirs.length
		for (int i = 0; i < numDirs - 1; i++) {
			for (int j = i + 1; j < numDirs - 1; j++) {
				def di = dirs[i]
				def pi = new HashSet(pkgMap.get(di))
				def dj = dirs[j]
				def pj = pkgMap.get(dj)
				pi.retainAll(pj)
				if (!pi.isEmpty() && mergeMap.get(di) != dj && mergeMap.get(dj) != di) {
					logger.error("Packages $pi are split between directories '$di' and '$dj'")
					splitFound = true
				}
			}
		}
		return splitFound
	}

	private void addPackagesInDirectory(HashSet<String> packages, File dir, String pkg) {
		def scanDir = new File(dir, pkg)
		def File[] javaFiles = scanDir.listFiles({ file -> !file.isDirectory() && file.getName().endsWith(JAVA_FILE_SUFFIX) } as FileFilter)
		if (javaFiles != null && javaFiles.length != 0) {
			packages.add(pkg)
		}
		dirList(scanDir).each { File subDir ->
			addPackagesInDirectory(packages, dir, pkg + File.separator + subDir.getName())
		}
	}
}