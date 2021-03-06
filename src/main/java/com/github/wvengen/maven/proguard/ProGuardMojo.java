/**
 * Pyx4me framework
 * Copyright (C) 2006-2008 pyx4j.com.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 *
 * @author vlads
 * @version $Id$
 */
package com.github.wvengen.maven.proguard;

import org.apache.maven.archiver.MavenArchiveConfiguration;
import org.apache.maven.archiver.MavenArchiver;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.model.Dependency;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.MavenProjectHelper;
import org.apache.tools.ant.DefaultLogger;
import org.apache.tools.ant.Project;
import org.apache.tools.ant.Target;
import org.apache.tools.ant.taskdefs.Expand;
import org.apache.tools.ant.taskdefs.Java;
import org.codehaus.plexus.archiver.jar.JarArchiver;
import org.codehaus.plexus.util.FileUtils;

import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.net.URL;
import java.util.*;

/**
 *
 * <p>
 * The Obfuscate task provides a stand-alone obfuscation task
 * </p>
 *
 * @goal proguard
 * @phase package
 * @description Create small jar files using ProGuard
 * @requiresDependencyResolution compile
 * @threadSafe
 */

public class ProGuardMojo extends AbstractMojo {

	/**
	 * Set this to 'true' to bypass ProGuard processing entirely.
	 *
	 * @parameter property="proguard.skip"
	 */
	private boolean skip;

	/**
	 * Recursively reads configuration options from the given file filename
	 *
	 * @parameter default-value="${basedir}/proguard.conf"
	 */
	private File proguardInclude;

	/**
	 * Select specific ProGuard version from plugin dependencies
	 *
	 * @parameter
	 */
	private String proguardVersion;

	/**
	 * To run DexGuard instead of ProGuard, set this to "true".
	 *
	 * @parameter default-value="false"
	 */
	private boolean useDexGuard;

	/**
	 * ProGuard configuration options
	 *
	 * @parameter
	 */
	private String[] options;

	/**
	 * Specifies not to obfuscate the input class files.
	 *
	 * @parameter default-value="true"
	 */
	private boolean obfuscate;

	/**
	 * Specifies that project compile dependencies be added as -libraryjars to proguard arguments. Dependency itself is
	 * not included in resulting jar unless you set includeDependencyInjar to true
	 *
	 * @parameter default-value="true"
	 */
	private boolean includeDependency;

	/**
	 * @parameter expression="${project.build.directory}/tempLibraryjars"
	 * @readonly
	 */
	private File tempLibraryjarsDir;

	/**
	 * Specifies to copy all the -libraryjars dependencies into a temporary directory and pass that directory
	 * as the only -libraryjars argument to ProGuard.
	 *
	 * @parameter default-value="false"
	 */
	private boolean putLibraryJarsInTempDir;


	/**
	 * Specifies that project compile dependencies should be added as injar.
	 *
	 * @parameter default-value="false"
	 */
	private boolean includeDependencyInjar;

	/**
	 * Bundle project dependency to resulting jar. Specifies list of artifact inclusions
	 *
	 * @parameter
	 */
	private Assembly assembly;

	/**
	 * Additional -libraryjars e.g. ${java.home}/lib/rt.jar Project compile dependency are added automatically. See
	 * exclusions
	 *
	 * @parameter
	 */
	private List<String> libs;

	/**
	 * List of dependency exclusions
	 *
	 * @parameter
	 */
	private List<Exclusion> exclusions;

	/**
	 * Specifies the input jar name (or wars, ears, zips) of the application to be
	 * processed.
	 *
	 * You may specify a classes directory e.g. 'classes'. This way plugin will processed
	 * the classes instead of jar. You would need to bind the execution to phase 'compile'
	 * or 'process-classes' in this case.
	 *
	 * @parameter expression="${project.build.finalName}.jar"
	 * @required
	 */
	protected String injar;

	/**
	 * Set this to 'true' to bypass ProGuard processing when injar does not exists.
	 *
	 * @parameter default-value="false"
	 */
	private boolean injarNotExistsSkip;

	/**
	 * Apply ProGuard classpathentry Filters to input jar. e.g. <code>!**.gif,!**&#47;tests&#47;**'</code>
	 *
	 * @parameter
	 */
	protected String inFilter;

	/**
	 * Specifies the names of the output jars. If attach=true the value ignored and name constructed base on classifier
	 * If empty input jar would be overdriven.
	 *
	 * @parameter
	 */
	protected String outjar;

	/**
	 * Apply ProGuard classpathentry Filters to output jar. e.g. <code>!**.gif,!**&#47;tests&#47;**'</code>
	 *
	 * @parameter
	 */
	protected String outFilter;

	/**
	 * Specifies whether or not to attach the created artifact to the project
	 *
	 * @parameter default-value="false"
	 */
	private boolean attach;

	/**
	 * Determines if {@link #attach} also attaches the {@link #mappingFileName} file.
	 *
	 * @parameter default-value="false"
	 */
	private boolean attachMap;

	/**
	 * Determines if {@link #attach} also attaches the {@link #seedFileName} file.
	 *
	 * @parameter default-value="false"
	 */
	private boolean attachSeed;

	/**
	 * Specifies attach artifact type
	 *
	 * @parameter default-value="jar"
	 */
	private String attachArtifactType;

	/**
	 * Specifies attach artifact Classifier, Ignored if attach=false
	 *
	 * @parameter default-value="small"
	 */
	private String attachArtifactClassifier;

	/**
	 * Set to false to exclude the attachArtifactClassifier from the Artifact final name. Default value is true.
	 *
	 * @parameter default-value="true"
	 */
	private boolean appendClassifier;

	/**
	 * Specifies attach proguard map artifact type
	 *
	 * @parameter default-value="txt"
	 */
	private String attachMapArtifactType = "txt";

	/**
	 * Specifies attach proguard seed artifact type
	 *
	 * @parameter default-value="txt"
	 */
	private String attachSeedArtifactType = "txt";

	/**
	 * Specifies attach artifact Classifier, ignored if attachMap=false
	 *
	 * @parameter default-value="proguard-map"
	 */
	private String attachMapArtifactClassifier = "proguard-map";

	/**
	 * Specifies attach artifact Classifier, ignored if attachSeed=false
	 *
	 * @parameter default-value="proguard-seed"
	 */
	private String attachSeedArtifactClassifier = "proguard-seed";



	/**
	 * Set to true to include META-INF/maven/** maven descriptord
	 *
	 * @parameter default-value="false"
	 */
	private boolean addMavenDescriptor;

	/**
	 * Directory containing the input and generated JAR.
	 *
	 * @parameter property="project.build.directory"
	 * @required
	 */
	protected File outputDirectory;

	/**
	 * The Maven project reference where the plugin is currently being executed. The default value is populated from
	 * maven.
	 *
	 * @parameter property="project"
	 * @readonly
	 * @required
	 */
	protected MavenProject mavenProject;

	/**
	 * The plugin dependencies.
	 *
	 * @parameter property="plugin.artifacts"
	 * @required
	 * @readonly
	 */
	protected List<Artifact> pluginArtifacts;

	/**
	 * @component
	 */
	private MavenProjectHelper projectHelper;

	/**
	 * The Jar archiver.
	 *
	 * @component role="org.codehaus.plexus.archiver.Archiver" roleHint="jar"
	 * @required
	 */
	private JarArchiver jarArchiver;


	/**
	 * The maven archive configuration to use. only if assembly is used.
	 *
	 * @parameter
	 */
	protected MavenArchiveConfiguration archive = new MavenArchiveConfiguration();

	/**
	 * The max memory the forked java process should use, e.g. 256m
	 *
	 * @parameter
	 */
	protected String maxMemory;

	/**
	 * ProGuard main class name.
	 *
	 * @parameter default-value="proguard.ProGuard"
	 */
	protected String proguardMainClass = "proguard.ProGuard";

	/**
	 * Specifies whether or not to pass war's WEB-INF/classes/ directory to Proguard.
	 *
	 * @parameter default-value="true"
	 */
	private boolean processWarClassesDir = true;

	/**
	 * Sets the name of the ProGuard mapping file.
	 *
	 * @parameter default-value="proguard_map.txt"
	 */
	protected String mappingFileName = "proguard_map.txt";

	/**
	 * Sets the name of the ProGuard seed file.
	 *
	 * @parameter default-value="proguard_seed.txt"
	 */
	protected String seedFileName = "proguard_seeds.txt";

	/**
	 * The proguard jar to use. useful for using beta versions of
	 * progaurd that aren't yet on Maven central.
	 *
	 * @parameter
	 */
	protected File proguardJar;

	/**
	 * If the plugin should be silent.
	 *
	 * @parameter default-value="false"
	 */
	private boolean silent;

	private Log log;

	/**
	 * ProGuard docs: Names with special characters like spaces and parentheses must be quoted with single or double
	 * quotes.
	 */
	private String fileNameToString(String fileName) {
		return "'" + fileName + "'";
	}

	private String fileToString(File file) {
		return fileNameToString(file.toString());
	}

	private boolean useArtifactClassifier() {
		return appendClassifier && ((attachArtifactClassifier != null) && (attachArtifactClassifier.length() > 0));
	}

	private boolean useMapArtifactClassifier() {
		return ((attachMapArtifactClassifier != null) && (attachMapArtifactClassifier.length() > 0));
	}

	private boolean useSeedArtifactClassifier() {
		return ((attachSeedArtifactClassifier != null) && (attachSeedArtifactClassifier.length() > 0));
	}

	public void execute() throws MojoExecutionException, MojoFailureException {

		log = getLog();

		if (skip) {
			log.info("Bypass ProGuard processing because \"proguard.skip=true\"");
			return;
		}

		boolean mainIsJar = mavenProject.getPackaging().equals("jar");

		File inJarFile = new File(outputDirectory, injar);
		if (!inJarFile.exists()) {
			if (injarNotExistsSkip) {
				log.info("Bypass ProGuard processing because \"injar\" dos not exist");
				return;
			} else if (mainIsJar) {
				throw new MojoFailureException("Can't find file " + inJarFile);
			}
		}

		// check and extract war
		boolean processingWar = false;
		boolean mainIsWar = mavenProject.getPackaging().equals("war");
		File expandedDir = null;
		File priorityLibsDir = null;
		if (mainIsWar) {
			processingWar = injar.endsWith(".war");
			if (processingWar) {
				expandedDir = (new File(outputDirectory, nameNoType(injar) + "_war_proguard_expanded")).getAbsoluteFile();
				if (expandedDir.exists()) {
					if (!deleteFileOrDirectory(expandedDir)) {
						throw new MojoFailureException("Can't delete " + expandedDir);
					}
				}
				if (!expandedDir.mkdirs()) {
					throw new MojoFailureException("Can't create " + outputDirectory);
				}

				try {
					unzip(inJarFile, expandedDir);
				} catch (IOException e) {
					throw new MojoFailureException("Can't extract " + inJarFile, e);
				}

				priorityLibsDir = new File(expandedDir, "WEB-INF/lib");
			}
		}


		if (!outputDirectory.exists()) {
			if (!outputDirectory.mkdirs()) {
				throw new MojoFailureException("Can't create " + outputDirectory);
			}
		}

		File outJarFile;
		boolean sameArtifact;

		if (attach) {
			outjar = nameNoType(injar);
			if (useArtifactClassifier()) {
				outjar += "-" + attachArtifactClassifier;
			}
			outjar += "." + attachArtifactType;
		}

		if (processingWar) {
			// When processing war, we update outJarFile to point to proguarded jar
			if (outjar == null) {
				outjar = injar;
			}
			String outJarName = nameNoType(outjar) + ".jar";
			outJarFile = (new File(priorityLibsDir, outJarName)).getAbsoluteFile();
			if (outJarFile.exists()) {
				if (!deleteFileOrDirectory(outJarFile)) {
					throw new MojoFailureException("Can't delete " + outJarFile);
				}
			}
			sameArtifact = (outjar != null) && (outjar.equals(injar));
		} else if ((outjar != null) && (!outjar.equals(injar))) {
			sameArtifact = false;
			outJarFile = (new File(outputDirectory, outjar)).getAbsoluteFile();
			if (outJarFile.exists()) {
				if (!deleteFileOrDirectory(outJarFile)) {
					throw new MojoFailureException("Can't delete " + outJarFile);
				}
			}
		} else {
			sameArtifact = true;
			outJarFile = inJarFile.getAbsoluteFile();
			File baseFile;
			if (inJarFile.isDirectory()) {
				baseFile = new File(outputDirectory, nameNoType(injar) + "_proguard_base");
			} else {
				baseFile = new File(outputDirectory, nameNoType(injar) + "_proguard_base.jar");
			}
			if (baseFile.exists()) {
				if (!deleteFileOrDirectory(baseFile)) {
					throw new MojoFailureException("Can't delete " + baseFile);
				}
			}
			if (inJarFile.exists()) {
				if (!inJarFile.renameTo(baseFile)) {
					throw new MojoFailureException("Can't rename " + inJarFile);
				}
			}
			inJarFile = baseFile;
		}

		ArrayList<String> args = new ArrayList<String>();
		ArrayList<File> libraryJars = new ArrayList<File>();

		if (log.isDebugEnabled()) {
			@SuppressWarnings("unchecked")
			List<Artifact> dependancy = mavenProject.getCompileArtifacts();
			for (Artifact artifact : dependancy) {
				log.debug("--- compile artifact " + artifact.getGroupId() + ":" + artifact.getArtifactId() + ":"
						+ artifact.getType() + ":" + artifact.getClassifier() + " Scope:" + artifact.getScope());
			}

			@SuppressWarnings("unchecked")
			final Set<Artifact> artifacts = mavenProject.getArtifacts();
			for (Artifact artifact : artifacts) {
				log.debug("--- artifact " + artifact.getGroupId() + ":" + artifact.getArtifactId() + ":"
						+ artifact.getType() + ":" + artifact.getClassifier() + " Scope:" + artifact.getScope());
			}
			@SuppressWarnings("unchecked")
			final List<Dependency> dependencies = mavenProject.getDependencies();
			for (Dependency artifact : dependencies) {
				log.debug("--- dependency " + artifact.getGroupId() + ":" + artifact.getArtifactId() + ":"
						+ artifact.getType() + ":" + artifact.getClassifier() + " Scope:" + artifact.getScope());
			}
		}

		Set<File> inFiles = new HashSet<File>();

		if (processingWar) {
			File classesDir = new File(expandedDir, "WEB-INF/classes");
			if (processWarClassesDir) {
				File classesDirInput = new File(expandedDir, "WEB-INF/classes_input");
				classesDir.renameTo(classesDirInput);

				args.add("-injars");
				args.add(fileToString(classesDirInput));
				inFiles.add(classesDirInput);

				// class files are processes separately!
				args.add("-outjars");
				args.add(fileToString(classesDir));

			} else {
				args.add("-libraryjars");
				args.add(fileToString(classesDir));
			}
		}

		Set<String> inPath = new HashSet<String>();
		boolean hasInclusionLibrary = false;
		if (assembly != null && assembly.inclusions != null) {
			@SuppressWarnings("unchecked")
			final List<Inclusion> inclusions = assembly.inclusions;
			for (Inclusion inc : inclusions) {
				Set<Artifact> deps = getDependancies(inc, mavenProject); // get all matching dependencies as wildcard may have been used
				for (Artifact artifact : deps) {
					if (!inc.library) {
						File file = getClasspathElement(artifact, mavenProject, priorityLibsDir);
						if (processingWar && "*".equals(inc.artifactId) && !priorityLibsDir.equals(file.getParentFile())) {
							log.debug("Wildcard matching artifact will be included as a library (as does not belong to enclosed libs): " + file);
							inPath.add(file.toString());
							args.add("-libraryjars");
							args.add(fileToString(file));
							continue;
						}
						inPath.add(file.toString());
						log.debug("--- ADD injars:" + inc.artifactId);
						StringBuffer filter = new StringBuffer(fileToString(file));
						filter.append("(!META-INF/MANIFEST.MF");
						if (!addMavenDescriptor) {
							filter.append(",");
							filter.append("!META-INF/maven/**");
						}
						if (inc.filter != null) {
							filter.append(",").append(inc.filter);
						}
						filter.append(")");
						inFiles.add(file);
						args.add("-injars");
						args.add(filter.toString());
					} else {
						hasInclusionLibrary = true;
						log.debug("--- ADD libraryjars:" + inc.artifactId);
						// This may not be CompileArtifacts, maven 2.0.6 bug
						File file = getClasspathElement(artifact, mavenProject, priorityLibsDir);
						inPath.add(file.toString());
						if(putLibraryJarsInTempDir){
							libraryJars.add(file);
						} else {
							args.add("-libraryjars");
							args.add(fileToString(file));
						}
					}
				}
			}
		}

		if (inJarFile.exists() && !processingWar) {
			args.add("-injars");
			args.add(buildJarReference(inJarFile, inFilter));
		}


		if (includeDependency) {
			@SuppressWarnings("unchecked")
			List<Artifact> dependency = this.mavenProject.getCompileArtifacts();
			for (Artifact artifact : dependency) {
				// dependency filter
				if (isExclusion(artifact)) {
					continue;
				}
				File file = getClasspathElement(artifact, mavenProject, priorityLibsDir);

				if (inPath.contains(file.toString())) {
					log.debug("--- ignore library since one in injar:" + artifact.getArtifactId());
					continue;
				}
				if (includeDependencyInjar) {
					log.debug("--- ADD library as injars:" + artifact.getArtifactId());
					inFiles.add(file);
					args.add("-injars");
					args.add(fileToString(file));
				} else {
					log.debug("--- ADD libraryjars:" + artifact.getArtifactId());
					if (putLibraryJarsInTempDir) {
						libraryJars.add(file);
					} else {
						args.add("-libraryjars");
						args.add(fileToString(file));
					}
				}
			}
		}

		if (args.contains("-injars")) {
			args.add("-outjars");
			args.add(buildJarReference(outJarFile, outFilter));
  	}

		if (!obfuscate) {
			args.add("-dontobfuscate");
		}

		if (proguardInclude != null) {
			if (proguardInclude.exists()) {
				args.add("-include");
				args.add(fileToString(proguardInclude));
				log.debug("proguardInclude " + proguardInclude);
			} else {
				log.debug("proguardInclude config does not exists " + proguardInclude);
			}
		}

		if (libs != null) {
			for (String lib : libs) {
				if (putLibraryJarsInTempDir) {
					libraryJars.add(new File(lib));
				} else {
					args.add("-libraryjars");
					args.add(fileNameToString(lib));
				}
			}
		}

		if (!libraryJars.isEmpty()) {
			log.debug("Copy libraryJars to temporary directory");
			log.debug("Temporary directory: " + tempLibraryjarsDir);
			if (tempLibraryjarsDir.exists()) {
							try{
								FileUtils.deleteDirectory(tempLibraryjarsDir);
							} catch(IOException ignored){
								// NO-OP
							}
			}
			tempLibraryjarsDir.mkdir();
			if (!tempLibraryjarsDir.exists()) {
				throw new MojoFailureException("Can't create temporary libraryJars directory: " + tempLibraryjarsDir.getAbsolutePath());
			}
			for (File libraryJar : libraryJars) {
				try {
					FileUtils.copyFileToDirectory(libraryJar, tempLibraryjarsDir);
				} catch (IOException e) {
					throw new MojoFailureException("Can't copy to temporary libraryJars directory", e);
				}
			}
			args.add("-libraryjars");
			args.add(fileToString(tempLibraryjarsDir));
		}

		File proguardMapFile = (new File(outputDirectory, mappingFileName).getAbsoluteFile());
		args.add("-printmapping");
		args.add(fileToString(proguardMapFile));

		File proguardSeedFile = (new File(outputDirectory, seedFileName).getAbsoluteFile());
		args.add("-printseeds");
		args.add(fileToString(proguardSeedFile));

		if (log.isDebugEnabled()) {
			args.add("-verbose");
		}

		if (options != null) {
			Collections.addAll(args, options);
		}

		log.debug("Run Proguard with options" + args.toString());
		proguardMain(getProguardJar(this), args, this);

		if (processingWar) {
			for (File f : inFiles) {
				if (f.isDirectory()) {
					log.info("Removing proguarded: " + f);
					if (!deleteFileOrDirectory(f)) {
						throw new MojoFailureException("Can't delete " + f);
					}
				} else if (priorityLibsDir.equals(f.getParentFile())) {
					log.info("Removing proguarded: " + f);
					if (!deleteFileOrDirectory(f)) {
						throw new MojoFailureException("Can't delete " + f);
					}
				}
			}
		}

		if (!libraryJars.isEmpty()) {
			deleteFileOrDirectory(tempLibraryjarsDir);
		}

		if ((assembly != null) && (hasInclusionLibrary)) {

			log.info("creating assembly");

			File baseFile = new File(outputDirectory, nameNoType(injar) + "_proguard_result.jar");
			if (baseFile.exists()) {
				if (!baseFile.delete()) {
					throw new MojoFailureException("Can't delete " + baseFile);
				}
			}
			File archiverFile = outJarFile.getAbsoluteFile();
			if (!outJarFile.renameTo(baseFile)) {
				throw new MojoFailureException("Can't rename " + outJarFile);
			}

			MavenArchiver archiver = new MavenArchiver();
			archiver.setArchiver(jarArchiver);
			archiver.setOutputFile(archiverFile);
			archive.setAddMavenDescriptor(addMavenDescriptor);

			try {
				jarArchiver.addArchivedFileSet(baseFile);
				@SuppressWarnings("unchecked")
				final List<Inclusion> inclusions = assembly.inclusions;
				for (Inclusion inc : inclusions) {
					if (inc.library) {
						File file;
						Artifact artifact = getDependancy(inc, mavenProject);
						file = getClasspathElement(artifact, mavenProject, priorityLibsDir);
						if (file.isDirectory()) {
							getLog().info("merge project: " + artifact.getArtifactId() + " " + file);
							jarArchiver.addDirectory(file);
						} else {
							getLog().info("merge artifact: " + artifact.getArtifactId());
							jarArchiver.addArchivedFileSet(file);
						}
					}
				}

				archiver.createArchive(mavenProject, archive);

				// delete baseFile right away so we don't include it in war
				if (!baseFile.delete()) {
					throw new MojoFailureException("Can't delete " + baseFile);
				}

			} catch (Exception e) {
				throw new MojoExecutionException("Unable to create jar", e);
			}

		}

		if (processingWar) {
			File outputWar = new File(outputDirectory, outjar);
			if (outputWar.exists()) {
				if (!outputWar.delete()) {
					throw new MojoFailureException("Can't delete " + outputWar);
				}
			}
			MavenArchiver archiver = new MavenArchiver();
			archiver.setArchiver(jarArchiver);
			archiver.setOutputFile(outputWar);
			archive.setAddMavenDescriptor(addMavenDescriptor);
			try {
				jarArchiver.addDirectory(expandedDir);
				archiver.createArchive(mavenProject, archive);
			} catch (Exception e) {
				throw new MojoExecutionException("Unable to create war", e);
			}
			outJarFile = outputWar;
		}

		if (attach) {
			if (!sameArtifact) {
				final String classifier;
				if (useArtifactClassifier()) {
					classifier = attachArtifactClassifier;
				} else {
					classifier = null;
				}

				projectHelper.attachArtifact(mavenProject, attachArtifactType, classifier, outJarFile);
			}

			final String mainClassifier = useArtifactClassifier() ? attachArtifactClassifier : null;
			final File buildOutput = new File(mavenProject.getBuild().getDirectory());
			if (attachMap) {
				attachTextFile(new File(buildOutput, mappingFileName), mainClassifier, "map");
			}
			if (attachSeed) {
				attachTextFile(new File(buildOutput, seedFileName), mainClassifier, "seed");
			}
		}

		if (attachMap && attach) {
			if (!proguardMapFile.exists()) {
				log.warn("Cannot attach proguard map artifact as file does nto exist.");
			} else if (useMapArtifactClassifier()) {
				projectHelper.attachArtifact(mavenProject, attachMapArtifactType, attachMapArtifactClassifier, proguardMapFile);
			} else {
				throw new MojoExecutionException("Map artifact classifier cannot be empty");
//				projectHelper.attachArtifact(mavenProject, attachMapArtifactType, null, proguardMapFile);
			}
		}

		if (attachSeed && attach) {
			if (!proguardSeedFile.exists()) {
				log.warn("Cannot attach proguard seed artifact as file does nto exist.");
			} else if (useSeedArtifactClassifier()) {
				projectHelper.attachArtifact(mavenProject, attachSeedArtifactType, attachSeedArtifactClassifier, proguardSeedFile);
			} else {
				throw new MojoExecutionException("Seed artifact classifier cannot be empty");
//				projectHelper.attachArtifact(mavenProject, attachSeedArtifactType, null, proguardSeedFile);
			}
		}
	}

	private void attachTextFile(File theFile, String mainClassifier, String suffix) {
		final String classifier = (null == mainClassifier ? "" : mainClassifier+"-") + suffix;
		log.info("Attempting to attach "+suffix+" artifact");
		if (theFile.exists()) {
			if (theFile.isFile()) {
				projectHelper.attachArtifact(mavenProject, "txt", classifier, theFile);
			} else {
				log.warn("Cannot attach file because it is not a file: "+theFile);
			}
		} else {
			log.warn("Cannot attach file because it does not exist: "+theFile);

		}
	}

	private File getProguardJar(ProGuardMojo mojo) throws MojoExecutionException {

		if (proguardJar != null) {
			if (proguardJar.exists()) {
				if (proguardJar.isFile()) {
					return proguardJar;
				} else {
					mojo.getLog().warn("proguard jar (" + proguardJar + ") is not a file");
					throw new MojoExecutionException("proguard jar (" + proguardJar + ") is not a file");
				}
			} else {
				mojo.getLog().warn("proguard jar (" + proguardJar + ") does not exist");
				throw new MojoExecutionException("proguard jar (" + proguardJar + ") does not exist");
			}
		}

		Artifact proguardArtifact = null;
		int proguardArtifactDistance = -1;
		// This should be solved in Maven 2.1
		for (Artifact artifact : mojo.pluginArtifacts) {
			mojo.getLog().debug("pluginArtifact: " + artifact.getFile());
			final String artifactId = artifact.getArtifactId();
			if (artifactId.startsWith((useDexGuard?"dexguard":"proguard")) &&
				!artifactId.startsWith("proguard-maven-plugin")) {
				int distance = artifact.getDependencyTrail().size();
				mojo.getLog().debug("proguard DependencyTrail: " + distance);
				if ((mojo.proguardVersion != null) && (mojo.proguardVersion.equals(artifact.getVersion()))) {
					proguardArtifact = artifact;
					break;
				} else if (proguardArtifactDistance == -1) {
					proguardArtifact = artifact;
					proguardArtifactDistance = distance;
				} else if (distance < proguardArtifactDistance) {
					proguardArtifact = artifact;
					proguardArtifactDistance = distance;
				}
			}
		}
		if (proguardArtifact != null) {
			mojo.getLog().debug("proguardArtifact: " + proguardArtifact.getFile());
			return proguardArtifact.getFile().getAbsoluteFile();
		}
		mojo.getLog().info((useDexGuard?"dexguard":"proguard") + " jar not found in pluginArtifacts");

		ClassLoader cl;
		cl = mojo.getClass().getClassLoader();
		// cl = Thread.currentThread().getContextClassLoader();
		String classResource = "/" + mojo.proguardMainClass.replace('.', '/') + ".class";
		URL url = cl.getResource(classResource);
		if (url == null) {
			throw new MojoExecutionException("Obfuscation failed ProGuard (" + mojo.proguardMainClass
					+ ") not found in classpath");
		}
		String proguardJar = url.toExternalForm();
		if (proguardJar.startsWith("jar:file:")) {
			proguardJar = proguardJar.substring("jar:file:".length());
			proguardJar = proguardJar.substring(0, proguardJar.indexOf('!'));
		} else {
			throw new MojoExecutionException("Unrecognized location (" + proguardJar + ") in classpath");
		}
		return new File(proguardJar);
	}

	private void proguardMain(File proguardJar, List<String> argsList, ProGuardMojo mojo)
			throws MojoExecutionException {

		Java java = new Java();

		Project antProject = new Project();
		antProject.setName(mojo.mavenProject.getName());
		antProject.init();

		DefaultLogger antLogger = new DefaultLogger();
		antLogger.setOutputPrintStream(System.out);
		antLogger.setErrorPrintStream(System.err);
		int logLevel = mojo.log.isDebugEnabled() ? Project.MSG_DEBUG : Project.MSG_INFO;
		antLogger.setMessageOutputLevel(silent ? Project.MSG_ERR : logLevel);

		antProject.addBuildListener(antLogger);
		antProject.setBaseDir(mojo.mavenProject.getBasedir());

		java.setProject(antProject);
		java.setTaskName("proguard");

		mojo.getLog().info("proguard jar: " + proguardJar);

		java.createClasspath().setLocation(proguardJar);
		// java.createClasspath().setPath(System.getProperty("java.class.path"));
		java.setClassname(mojo.proguardMainClass);

		java.setFailonerror(true);

		java.setFork(true);

		// get the maxMemory setting
		if (mojo.maxMemory != null) {
			java.setMaxmemory(mojo.maxMemory);
		}

		for (String arg : argsList) {
			java.createArg().setValue(arg);
		}

		int result = java.executeJava();
		if (result != 0) {
			throw new MojoExecutionException("Obfuscation failed (result=" + result + ")");
		}
	}

	private String nameNoType(String fileName) {
		int extStart = fileName.lastIndexOf('.');
		if (extStart == -1) {
			return fileName;
		}
		return fileName.substring(0, extStart);
	}

	private boolean deleteFileOrDirectory(File path) throws MojoFailureException {
		if (path.isDirectory()) {
			File[] files = path.listFiles();
			if (null != files) {
				for (File file : files) {
					if (file.isDirectory()) {
						if (!deleteFileOrDirectory(file)) {
							throw new MojoFailureException("Can't delete dir " + file);
						}
					} else {
						if (!file.delete()) {
							throw new MojoFailureException("Can't delete file " + file);
						}
					}
				}
			}
			return path.delete();
		} else {
			return path.delete();
		}
	}

	private static Set<Artifact> getDependancies(Inclusion inc, MavenProject mavenProject) throws MojoExecutionException {
		@SuppressWarnings("unchecked")
		Set<Artifact> artifacts = mavenProject.getArtifacts();
		Set<Artifact> dependencies = new HashSet<Artifact>();
		for (Artifact artifact: artifacts) {
			if (inc.match(artifact)) {
				dependencies.add(artifact);
			}
		}
		if (dependencies.size() == 0)
			throw new MojoExecutionException("artifactId Not found " + inc.artifactId);
		return dependencies;
	}

	private static Artifact getDependancy(Inclusion inc, MavenProject mavenProject) throws MojoExecutionException {
		@SuppressWarnings("unchecked")
		Set<Artifact> dependency = mavenProject.getArtifacts();
		for (Artifact artifact : dependency) {
			if (inc.match(artifact)) {
				return artifact;
			}
		}
		throw new MojoExecutionException("artifactId Not found " + inc.artifactId);
	}

	private boolean isExclusion(Artifact artifact) {
		if (exclusions == null) {
			return false;
		}
		for (Exclusion excl : exclusions) {
			if (excl.match(artifact)) {
				return true;
			}
		}
		return false;
	}

	private File getClasspathElement(Artifact artifact, MavenProject mavenProject, File libsDir) throws MojoExecutionException {
		if (artifact.getClassifier() != null) {
			return artifact.getFile();
		}
		String refId = artifact.getGroupId() + ":" + artifact.getArtifactId();
		MavenProject project = (MavenProject) mavenProject.getProjectReferences().get(refId);
		if (project != null) {
			return new File(project.getBuild().getOutputDirectory());
		} else {

			if (libsDir != null) {
				final String filenameBase = artifact.getArtifactId() + "-";
				File[] artifactFilesInLib = libsDir.listFiles(new FilenameFilter() {
					public boolean accept(File dir, String name) {
						if (name.startsWith(filenameBase) && name.length() > filenameBase.length() + 4) {
							// check if the first char after base name is a digit (assuming version number)
							boolean digitFound = Character.isDigit(name.charAt(filenameBase.length()));
							return digitFound;
						}
						return false;
					}
				});
				if (artifactFilesInLib.length > 1)
					System.out.println("Warn: Found more than one library for artifact " + artifact + ": " + artifactFilesInLib);
				if (artifactFilesInLib.length > 0)
					return artifactFilesInLib[0];
			}

			File file = artifact.getFile();
			if ((file == null) || (!file.exists())) {
				throw new MojoExecutionException("Dependency Resolution Required " + artifact);
			}
			return file;
		}
	}

	/**
	 * Unzips archive from the given path to the given destination dir.
	 */
	private static void unzip(File archiveFile, File destDir) throws IOException, MojoFailureException {
		final class Expander extends Expand {
			public Expander() {
				project = new Project();
				project.init();
				taskType = "unzip";
				taskName = "unzip";
				target = new Target();
			}
		}
		Expander expander = new Expander();
		expander.setSrc(archiveFile);
		expander.setDest(destDir);
		expander.execute();
	}

	private String buildJarReference(File jarFile, String jarFilter) {
		StringBuffer filter = new StringBuffer(fileToString(jarFile));
		if ((jarFilter != null) || (!addMavenDescriptor)) {
			filter.append("(");
			boolean coma = false;

			if (!addMavenDescriptor) {
				coma = true;
				filter.append("!META-INF/maven/**");
			}

			if (jarFilter != null) {
				if (coma) {
					filter.append(",");
				}
				filter.append(jarFilter);
			}

			filter.append(")");
		}
		return filter.toString();
	}
}
