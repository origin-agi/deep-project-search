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
    private final int lineNumber;
    private final int columnNumber;
    private final String preview;
    private final VirtualFile virtualFile;

    DeepSearchResult(
            SourceType sourceType,
            String name,
            String path,
            String source,
            int lineNumber,
            int columnNumber,
            String preview,
            VirtualFile virtualFile
    ) {
        this.sourceType = sourceType;
        this.name = name;
        this.path = path;
        this.source = source;
        this.lineNumber = lineNumber;
        this.columnNumber = columnNumber;
        this.preview = preview;
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

    int getLineNumber() {
        return lineNumber;
    }

    int getColumnNumber() {
        return columnNumber;
    }

    String getPreview() {
        return preview;
    }

    VirtualFile getVirtualFile() {
        return virtualFile;
    }
}
