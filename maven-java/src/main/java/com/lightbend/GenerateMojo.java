package com.lightbend;

import com.lightbend.akkasls.codegen.SourceGenerator;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;

import org.apache.maven.plugin.logging.Log;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

import java.io.File;
import java.nio.file.Path;

import com.lightbend.akkasls.codegen.ModelBuilder;
import scala.collection.Iterable;

/**
 * Goal which reads in protobuf files and produces entities given their
 * commands, events and states. Entities are produced in the source file
 * directory for Java, unless they already exist, in which case they will be
 * modified appropriately. Only type declarations associated with commands,
 * events and state are affected i.e. not tbe body of existing methods.
 */
@Mojo(name = "generate", defaultPhase = LifecyclePhase.PROCESS_SOURCES)
public class GenerateMojo extends AbstractMojo {
    @SuppressWarnings("unused")
    @Parameter(defaultValue = "${project.basedir}", property = "baseDir", required = true)
    private File baseDir;

    // target/classes
    @SuppressWarnings("unused")
    @Parameter(defaultValue = "${project.build.outputDirectory}", property = "outputDirectory", required = true)
    private File outputDirectory;

    // target/generated-sources/protobuf/java
    @SuppressWarnings("unused")
    @Parameter(defaultValue = "${project.build.directory}/generated-sources/protobuf/java", property = "protoOutputDirectory", required = true)
    private File protoOutputDirectory;

    // src/main/java
    @SuppressWarnings("unused")
    @Parameter(defaultValue = "${project.build.sourceDirectory}", property = "sourceDirectory", required = true)
    private File sourceDirectory;

    // src/test/java
    @SuppressWarnings("unused")
    @Parameter(defaultValue = "${project.build.testSourceDirectory}", property = "testSourceDirectory", required = true)
    private File testSourceDirectory;

    @SuppressWarnings("unused")
    @Parameter(defaultValue = "${project.groupId}.Main", property = "mainClass", required = true)
    private String mainClass;

    @SuppressWarnings("unused")
    @Parameter(defaultValue = ".*ServiceEntity", property = "serviceNamesFilter", required = true)
    private String serviceNamesFilter;

    private final Log log = getLog();

    /**
     * We recursively enumerate the protoOutputDirectory to provide a list of Java
     * source files generated by protoc, and then load their respective compiled
     * classes from the outputDirectory into an isolated class loader. Once done, we
     * then introspect each class and search for entities, commands, events and
     * state declarations, storing them in an approprate structure. That structure
     * then drives the code generation phase.
     */
    public void execute() throws MojoExecutionException {
        if (protoOutputDirectory.exists()) {
            Iterable<Path> protobufSources = ModelBuilder
                    .collectProtobufSources(protoOutputDirectory.toPath());
            Iterable<Path> protobufClasses = ModelBuilder.mapProtobufClasses(protoOutputDirectory.toPath(),
                    protobufSources, outputDirectory.toPath());
            Iterable<Path> newProtobufSources = ModelBuilder.filterNewProtobufSources(protobufSources,
                    protobufClasses);

            int nrOfNewProtobufSource = newProtobufSources.size();
            if (nrOfNewProtobufSource > 0) {
                log.info(String.format("Inspecting %d proto file(s) for entity generation...", nrOfNewProtobufSource));
                if (ModelBuilder.compileProtobufSources(newProtobufSources, outputDirectory.toPath()) == 0) {
                    Iterable<ModelBuilder.Entity> entities = ModelBuilder.introspectProtobufClasses(outputDirectory.toPath(), protobufClasses, serviceNamesFilter, e -> {
                        throw new RuntimeException(new MojoExecutionException("There was a problem introspecting the protobuf classes", e));
                    });
                    Iterable<Path> generated = SourceGenerator.generate(entities, sourceDirectory.toPath(), testSourceDirectory.toPath(), mainClass);
                    generated.foreach(p -> {
                        log.info("Generated: " + baseDir.toPath().relativize(p));
                        return null;
                    });
                } else {
                    throw new MojoExecutionException("There was a problem compiling the protobuf files");
                }
            } else {
                log.info("Skipping generation because target directory newer than sources.");
            }
        }
    }
}
