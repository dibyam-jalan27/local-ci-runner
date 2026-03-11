package com.localci.parser;

import com.localci.model.Pipeline;
import com.localci.exception.PipelineValidationException;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.Constructor;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashSet;
import java.util.Set;

/**
 * Parses a YAML pipeline file into a {@link Pipeline} object.
 * <p>
 * Supports:
 * - Direct file parsing (SnakeYAML)
 * - Pipeline inheritance via {@code extends} key
 * - Remote pipeline fetching via {@code pipeline_ref} key
 */
public class PipelineParser {

    /**
     * Reads and parses the given YAML file.
     * Handles extends (inheritance) and pipeline_ref (remote fetching).
     *
     * @param filePath path to the pipeline YAML file
     * @return a fully populated, merged {@link Pipeline}
     * @throws IOException if the file cannot be read
     */
    public Pipeline parse(String filePath) throws IOException {
        return parseWithInheritance(filePath, new HashSet<>());
    }

    /**
     * Internal parse with circular-reference tracking.
     */
    private Pipeline parseWithInheritance(String filePath, Set<String> visited) throws IOException {
        // Check for circular extends
        try {
            PipelineMerger.checkCircular(filePath, visited);
        } catch (PipelineValidationException e) {
            throw new IOException(e.getMessage());
        }

        // Parse the YAML file
        Pipeline pipeline = parseRaw(filePath);

        // Handle remote pipeline_ref
        if (pipeline.getPipeline_ref() != null && !pipeline.getPipeline_ref().isBlank()) {
            String remoteFile = RemotePipelineFetcher.fetch(
                    pipeline.getPipeline_ref(), pipeline.getChecksum());
            Pipeline remotePipeline = parseRaw(remoteFile);
            // Remote pipeline becomes the base, current becomes the child
            pipeline = PipelineMerger.merge(remotePipeline, pipeline);
        }

        // Handle extends (inheritance)
        if (pipeline.getExtends() != null && !pipeline.getExtends().isBlank()) {
            String basePath = resolveRelativePath(filePath, pipeline.getExtends());
            Pipeline basePipeline = parseWithInheritance(basePath, visited);
            pipeline = PipelineMerger.merge(basePipeline, pipeline);
        }

        return pipeline;
    }

    /**
     * Raw YAML parse without inheritance resolution.
     */
    private Pipeline parseRaw(String filePath) throws IOException {
        LoaderOptions loaderOptions = new LoaderOptions();
        Yaml yaml = new Yaml(new Constructor(Pipeline.class, loaderOptions));

        try (InputStream input = new FileInputStream(filePath)) {
            return yaml.load(input);
        }
    }

    /**
     * Resolves a relative path against the directory of the current file.
     */
    private String resolveRelativePath(String currentFile, String relativePath) {
        File current = new File(currentFile);
        File parent = current.getParentFile();
        if (parent == null)
            parent = new File(".");

        File resolved = new File(parent, relativePath);
        return resolved.getAbsolutePath();
    }
}
