package com.origin.idea;

import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.OrderEnumerator;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

final class DeepSearchService {
    private static final int MAX_RESULTS = 700;
    private static final long MAX_TEXT_FILE_BYTES = 1_000_000L;
    private static final int MAX_PREVIEW_CHARS = 220;
    private static final Set<String> SKIPPED_PROJECT_DIRECTORIES = Set.of(
            ".git",
            ".gradle",
            ".idea",
            ".intellijPlatform",
            "build",
            "out",
            "target",
            "node_modules"
    );

    private DeepSearchService() {
    }

    static List<DeepSearchResult> search(Project project, String query, SearchScopeOption scope, boolean caseSensitive, ProgressIndicator indicator) {
        SmartSearchQuery smartQuery = SmartSearchQuery.parse(query, scope, caseSensitive);
        List<DeepSearchResult> results = new ArrayList<>();
        if (smartQuery.isBlank()) {
            return results;
        }

        if (smartQuery.includesProjectFiles()) {
            searchProjectFiles(project, smartQuery, indicator, results);
        }
        if (smartQuery.includesDependencies() && results.size() < MAX_RESULTS) {
            searchDependencyRoots(project, smartQuery, indicator, results);
        }
        return results;
    }

    private static void searchProjectFiles(Project project, SmartSearchQuery query, ProgressIndicator indicator, List<DeepSearchResult> results) {
        ProjectFileIndex fileIndex = ProjectRootManager.getInstance(project).getFileIndex();
        String basePath = project.getBasePath();
        VirtualFile baseDirectory = project.getBaseDir();
        if (baseDirectory == null) {
            return;
        }
        collectProjectMatches(baseDirectory, baseDirectory, basePath, query, fileIndex, indicator, results);
    }

    private static void collectProjectMatches(
            VirtualFile root,
            VirtualFile file,
            String basePath,
            SmartSearchQuery query,
            ProjectFileIndex fileIndex,
            ProgressIndicator indicator,
            List<DeepSearchResult> results
    ) {
        ProgressManager.checkCanceled();
        indicator.checkCanceled();
        if (results.size() >= MAX_RESULTS) {
            return;
        }

        if (file.isDirectory()) {
            if (!file.equals(root) && shouldSkipProjectDirectory(file, fileIndex)) {
                return;
            }
            for (VirtualFile child : file.getChildren()) {
                collectProjectMatches(root, child, basePath, query, fileIndex, indicator, results);
                if (results.size() >= MAX_RESULTS) {
                    return;
                }
            }
            return;
        }

        if (fileIndex.isInLibrary(file)) {
            return;
        }

        String path = displayProjectPath(file, basePath);
        String source = MyMessageBundle.message("toolwindow.DeepProjectSearch.source.project");
        boolean pathMatched = query.matchesPath(file.getName(), path);
        TextMatch textMatch = findTextMatch(file, query);
        if (!pathMatched && textMatch == null) {
            return;
        }
        if (textMatch != null) {
            source = source + " line " + textMatch.lineNumber();
        }

        results.add(new DeepSearchResult(
                DeepSearchResult.SourceType.PROJECT,
                file.getName(),
                path,
                source,
                textMatch == null ? -1 : textMatch.lineNumber(),
                textMatch == null ? -1 : textMatch.columnNumber(),
                textMatch == null ? "" : textMatch.preview(),
                file
        ));
    }

    private static void searchDependencyRoots(Project project, SmartSearchQuery query, ProgressIndicator indicator, List<DeepSearchResult> results) {
        Set<VirtualFile> roots = new LinkedHashSet<>();
        roots.addAll(List.of(OrderEnumerator.orderEntries(project).withoutSdk().librariesOnly().recursively().classes().getRoots()));
        roots.addAll(List.of(OrderEnumerator.orderEntries(project).withoutSdk().librariesOnly().recursively().sources().getRoots()));

        for (VirtualFile root : roots) {
            ProgressManager.checkCanceled();
            indicator.checkCanceled();
            if (results.size() >= MAX_RESULTS) {
                return;
            }
            collectDependencyMatches(root, root, query, indicator, results);
        }
    }

    private static void collectDependencyMatches(VirtualFile root, VirtualFile file, SmartSearchQuery query, ProgressIndicator indicator, List<DeepSearchResult> results) {
        ProgressManager.checkCanceled();
        indicator.checkCanceled();
        if (results.size() >= MAX_RESULTS) {
            return;
        }

        if (file.isDirectory()) {
            for (VirtualFile child : file.getChildren()) {
                collectDependencyMatches(root, child, query, indicator, results);
                if (results.size() >= MAX_RESULTS) {
                    return;
                }
            }
            return;
        }

        String relativePath = VfsUtilCore.getRelativePath(file, root, '/');
        String path = relativePath == null ? file.getPath() : relativePath;
        boolean pathMatched = query.matchesPath(file.getName(), path);
        TextMatch textMatch = findTextMatch(file, query);
        if (!pathMatched && textMatch == null) {
            return;
        }

        String source = dependencySourceName(root);
        if (textMatch != null) {
            source = source + " line " + textMatch.lineNumber();
        }

        results.add(new DeepSearchResult(
                DeepSearchResult.SourceType.DEPENDENCY,
                file.getName(),
                path,
                source,
                textMatch == null ? -1 : textMatch.lineNumber(),
                textMatch == null ? -1 : textMatch.columnNumber(),
                textMatch == null ? "" : textMatch.preview(),
                file
        ));
    }

    private static boolean shouldSkipProjectDirectory(VirtualFile directory, ProjectFileIndex fileIndex) {
        return fileIndex.isExcluded(directory) || SKIPPED_PROJECT_DIRECTORIES.contains(directory.getName());
    }

    private static TextMatch findTextMatch(VirtualFile file, SmartSearchQuery query) {
        if (!file.isValid() || file.getLength() > MAX_TEXT_FILE_BYTES || file.getFileType().isBinary()) {
            return null;
        }

        try {
            String text = VfsUtilCore.loadText(file);
            int firstIndex = query.findContentIndex(text);
            if (firstIndex < 0) {
                return null;
            }
            int lineStart = text.lastIndexOf('\n', Math.max(0, firstIndex - 1)) + 1;
            int lineEnd = text.indexOf('\n', firstIndex);
            if (lineEnd < 0) {
                lineEnd = text.length();
            }
            int lineNumber = StringUtil.offsetToLineNumber(text, firstIndex) + 1;
            int columnNumber = Math.max(0, firstIndex - lineStart);
            return new TextMatch(lineNumber, columnNumber, buildPreview(text.substring(lineStart, lineEnd)));
        } catch (Exception ignored) {
            return null;
        }
    }

    private static String buildPreview(String lineText) {
        String preview = lineText.replace('\t', ' ').trim().replaceAll("\\s+", " ");
        if (preview.length() <= MAX_PREVIEW_CHARS) {
            return preview;
        }
        return preview.substring(0, MAX_PREVIEW_CHARS - 1) + "...";
    }

    private static String displayProjectPath(VirtualFile file, String basePath) {
        String path = file.getPath();
        if (basePath == null || basePath.isBlank()) {
            return path;
        }
        String prefix = basePath.endsWith("/") ? basePath : basePath + "/";
        return path.startsWith(prefix) ? path.substring(prefix.length()) : path;
    }

    private static String dependencySourceName(VirtualFile root) {
        String url = root.getPresentableUrl();
        int jarIndex = url.indexOf(".jar");
        if (jarIndex >= 0) {
            int slashIndex = url.lastIndexOf('/', jarIndex);
            return url.substring(slashIndex + 1, jarIndex + 4);
        }
        return root.getName();
    }

    private record TextMatch(int lineNumber, int columnNumber, String preview) {
    }
}
