package org.pragmatica.jbct.maven;

import org.apache.maven.archiver.MavenArchiveConfiguration;
import org.apache.maven.archiver.MavenArchiver;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.MavenProjectHelper;
import org.codehaus.plexus.archiver.jar.JarArchiver;
import org.pragmatica.jbct.slice.SliceManifest;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Packages slices into separate JAR artifacts.
 * Reads slice manifests from META-INF/slice/*.manifest and creates:
 * - {module}-{slice}-api.jar - API interface only
 * - {module}-{slice}.jar - Implementation + factory + request/response types
 */
@Mojo(name = "package-slices", defaultPhase = LifecyclePhase.PACKAGE)
public class PackageSlicesMojo extends AbstractMojo {

    @Parameter(defaultValue = "${project}", readonly = true, required = true)
    private MavenProject project;

    @Parameter(defaultValue = "${project.build.outputDirectory}", readonly = true)
    private File classesDirectory;

    @Parameter(defaultValue = "${project.build.directory}", readonly = true)
    private File outputDirectory;

    @Parameter(property = "jbct.skip", defaultValue = "false")
    private boolean skip;

    @Component
    private MavenProjectHelper projectHelper;

    private final List<SliceArtifact> generatedArtifacts = new ArrayList<>();

    @Override
    public void execute() throws MojoExecutionException {
        if (skip) {
            getLog().info("Skipping slice packaging");
            return;
        }

        var manifestDir = new File(classesDirectory, "META-INF/slice");
        if (!manifestDir.exists() || !manifestDir.isDirectory()) {
            getLog().info("No slice manifests found in " + manifestDir);
            return;
        }

        var manifestFiles = manifestDir.listFiles((dir, name) -> name.endsWith(".manifest"));
        if (manifestFiles == null || manifestFiles.length == 0) {
            getLog().info("No .manifest files found");
            return;
        }

        getLog().info("Found " + manifestFiles.length + " slice manifest(s)");

        for (var manifestFile : manifestFiles) {
            processManifest(manifestFile.toPath());
        }

        // Attach all generated artifacts to the project
        for (var artifact : generatedArtifacts) {
            projectHelper.attachArtifact(project, "jar", artifact.classifier(), artifact.file());
            getLog().info("Attached artifact: " + artifact.artifactId() + " (" + artifact.classifier() + ")");
        }
    }

    private void processManifest(Path manifestPath) throws MojoExecutionException {
        var result = SliceManifest.load(manifestPath);
        if (result.isFailure()) {
            throw new MojoExecutionException("Failed to load manifest: " + manifestPath);
        }

        var manifest = result.unwrap();
        getLog().info("Processing slice: " + manifest.sliceName());

        // Create API JAR
        createApiJar(manifest);

        // Create Impl JAR
        createImplJar(manifest);

        // Generate POMs
        generatePom(manifest, true);  // API POM
        generatePom(manifest, false); // Impl POM
    }

    private void createApiJar(SliceManifest manifest) throws MojoExecutionException {
        var jarName = manifest.apiArtifactId() + "-" + project.getVersion() + ".jar";
        var jarFile = new File(outputDirectory, jarName);

        try {
            var archiver = new JarArchiver();
            archiver.setDestFile(jarFile);

            // Add API classes
            for (var className : manifest.allApiClasses()) {
                addClassFiles(archiver, className);
            }

            var mavenArchiver = new MavenArchiver();
            mavenArchiver.setArchiver(archiver);
            mavenArchiver.setOutputFile(jarFile);

            var config = new MavenArchiveConfiguration();
            mavenArchiver.createArchive(null, project, config);

            generatedArtifacts.add(new SliceArtifact(
                    manifest.apiArtifactId(),
                    manifest.sliceName().toLowerCase() + "-api",
                    jarFile
            ));

            getLog().info("Created API JAR: " + jarFile.getName());
        } catch (Exception e) {
            throw new MojoExecutionException("Failed to create API JAR", e);
        }
    }

    private void createImplJar(SliceManifest manifest) throws MojoExecutionException {
        var jarName = manifest.implArtifactId() + "-" + project.getVersion() + ".jar";
        var jarFile = new File(outputDirectory, jarName);

        try {
            var archiver = new JarArchiver();
            archiver.setDestFile(jarFile);

            // Add impl classes (includes request/response types)
            for (var className : manifest.allImplClasses()) {
                addClassFiles(archiver, className);
            }

            var mavenArchiver = new MavenArchiver();
            mavenArchiver.setArchiver(archiver);
            mavenArchiver.setOutputFile(jarFile);

            var config = new MavenArchiveConfiguration();
            mavenArchiver.createArchive(null, project, config);

            generatedArtifacts.add(new SliceArtifact(
                    manifest.implArtifactId(),
                    manifest.sliceName().toLowerCase(),
                    jarFile
            ));

            getLog().info("Created Impl JAR: " + jarFile.getName());
        } catch (Exception e) {
            throw new MojoExecutionException("Failed to create Impl JAR", e);
        }
    }

    private void addClassFiles(JarArchiver archiver, String className) {
        var classesPath = classesDirectory.toPath();
        var paths = SliceManifest.classToPathsWithInner(className, classesPath);

        for (var relativePath : paths) {
            var classFile = new File(classesDirectory, relativePath);
            if (classFile.exists()) {
                archiver.addFile(classFile, relativePath);
            }
        }
    }

    private void generatePom(SliceManifest manifest, boolean isApi) throws MojoExecutionException {
        var artifactId = isApi ? manifest.apiArtifactId() : manifest.implArtifactId();
        var pomFile = new File(outputDirectory, artifactId + "-" + project.getVersion() + ".pom");

        try (var writer = new FileWriter(pomFile)) {
            writer.write(generatePomContent(manifest, isApi));
            getLog().debug("Generated POM: " + pomFile.getName());
        } catch (IOException e) {
            throw new MojoExecutionException("Failed to generate POM", e);
        }
    }

    private String generatePomContent(SliceManifest manifest, boolean isApi) {
        var artifactId = isApi ? manifest.apiArtifactId() : manifest.implArtifactId();
        var sb = new StringBuilder();

        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        sb.append("<project xmlns=\"http://maven.apache.org/POM/4.0.0\"\n");
        sb.append("         xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"\n");
        sb.append("         xsi:schemaLocation=\"http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd\">\n");
        sb.append("    <modelVersion>4.0.0</modelVersion>\n");
        sb.append("\n");
        sb.append("    <groupId>").append(project.getGroupId()).append("</groupId>\n");
        sb.append("    <artifactId>").append(artifactId).append("</artifactId>\n");
        sb.append("    <version>").append(project.getVersion()).append("</version>\n");
        sb.append("    <packaging>jar</packaging>\n");
        sb.append("\n");
        sb.append("    <name>").append(manifest.sliceName());
        sb.append(isApi ? " API" : "").append("</name>\n");
        sb.append("    <description>Generated slice artifact</description>\n");
        sb.append("\n");
        sb.append("    <dependencies>\n");

        if (isApi) {
            // API only depends on pragmatica-lite core
            sb.append("        <dependency>\n");
            sb.append("            <groupId>org.pragmatica-lite</groupId>\n");
            sb.append("            <artifactId>core</artifactId>\n");
            sb.append("            <version>0.9.10</version>\n");
            sb.append("        </dependency>\n");
        } else {
            // Impl depends on its own API
            sb.append("        <dependency>\n");
            sb.append("            <groupId>").append(project.getGroupId()).append("</groupId>\n");
            sb.append("            <artifactId>").append(manifest.apiArtifactId()).append("</artifactId>\n");
            sb.append("            <version>").append(project.getVersion()).append("</version>\n");
            sb.append("        </dependency>\n");
            // And slice-api for runtime
            sb.append("        <dependency>\n");
            sb.append("            <groupId>org.pragmatica.aether</groupId>\n");
            sb.append("            <artifactId>slice-api</artifactId>\n");
            sb.append("            <version>0.1.0</version>\n");
            sb.append("        </dependency>\n");
        }

        sb.append("    </dependencies>\n");
        sb.append("</project>\n");

        return sb.toString();
    }

    private record SliceArtifact(String artifactId, String classifier, File file) {}
}
