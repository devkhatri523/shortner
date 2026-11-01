package com.url.shortner.orchestrator.model;

import java.util.List;

/** Model-generated proposal; paths and contents are policy checked before writing. */
public record CodeChange(String explanation, List<SourceFile> files) {
    public record SourceFile(String path, String content) {}
}
