package com.origin.idea;

import com.intellij.openapi.vfs.VirtualFile;

final class DeepSearchResult {
    enum SourceType {
        PROJECT,
        DEPENDENCY
    }

    private final SourceType sourceType;
    private final String name;
    private final String path;
    private final String source;
    private final VirtualFile virtualFile;

    DeepSearchResult(SourceType sourceType, String name, String path, String source, VirtualFile virtualFile) {
        this.sourceType = sourceType;
        this.name = name;
        this.path = path;
        this.source = source;
        this.virtualFile = virtualFile;
    }

    SourceType getSourceType() {
        return sourceType;
    }

    String getName() {
        return name;
    }

    String getPath() {
        return path;
    }

    String getSource() {
        return source;
    }

    VirtualFile getVirtualFile() {
        return virtualFile;
    }
}
