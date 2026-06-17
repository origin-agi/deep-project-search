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
import java.util.Locale;
import java.util.Set;

final class DeepSearchService {
    private static final int MAX_RESULTS = 700;
    private static final long MAX_TEXT_FILE_BYTES = 1_000_000L;
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
        String normalizedQuery = caseSensitive ? query.trim() : query.trim().toLowerCase(Locale.ROOT);
        List<DeepSearchResult> results = new ArrayList<>();
        if (normalizedQuery.isBlank()) {
            return results;
        }

        if (scope.includesProjectFiles()) {
            searchProjectFiles(project, normalizedQuery, caseSensitive, indicator, results);
        }
        if (scope.includesDependencies() && results.size() < MAX_RESULTS) {
            searchDependencyRoots(project, normalizedQuery, caseSensitive, indicator, results);
        }
        return results;
    }

    private static void searchProjectFiles(Project project, String query, boolean caseSensitive, ProgressIndicator indicator, List<DeepSearchResult> results) {
        ProjectFileIndex fileIndex = ProjectRootManager.getInstance(project).getFileIndex();
        String basePath = project.getBasePath();
        VirtualFile baseDirectory = project.getBaseDir();
        if (baseDirectory == null) {
            return;
        }
        collectProjectMatches(baseDirectory, baseDirectory, basePath, query, caseSensitive, fileIndex, indicator, results);
    }

    private static void collectProjectMatches(
            VirtualFile root,
            VirtualFile file,
            String basePath,
            String query,
            boolean caseSensitive,
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
                collectProjectMatches(root, child, basePath, query, caseSensitive, fileIndex, indicator, results);
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
        boolean pathMatched = matches(file.getName(), path, query, caseSensitive);
        int contentLine = findTextMatchLine(file, query, caseSensitive);
        if (!pathMatched && contentLine < 0) {
            return;
        }
        if (contentLine >= 0) {
            source = source + " line " + contentLine;
        }

        results.add(new DeepSearchResult(
                DeepSearchResult.SourceType.PROJECT,
                file.getName(),
                path,
                source,
                file
        ));
    }

    private static void searchDependencyRoots(Project project, String query, boolean caseSensitive, ProgressIndicator indicator, List<DeepSearchResult> results) {
        Set<VirtualFile> roots = new LinkedHashSet<>();
        roots.addAll(List.of(OrderEnumerator.orderEntries(project).withoutSdk().librariesOnly().recursively().classes().getRoots()));
        roots.addAll(List.of(OrderEnumerator.orderEntries(project).withoutSdk().librariesOnly().recursively().sources().getRoots()));

        for (VirtualFile root : roots) {
            ProgressManager.checkCanceled();
            indicator.checkCanceled();
            if (results.size() >= MAX_RESULTS) {
                return;
            }
            collectDependencyMatches(root, root, query, caseSensitive, indicator, results);
        }
    }

    private static void collectDependencyMatches(VirtualFile root, VirtualFile file, String query, boolean caseSensitive, ProgressIndicator indicator, List<DeepSearchResult> results) {
        ProgressManager.checkCanceled();
        indicator.checkCanceled();
        if (results.size() >= MAX_RESULTS) {
            return;
        }

        if (file.isDirectory()) {
            for (VirtualFile child : file.getChildren()) {
                collectDependencyMatches(root, child, query, caseSensitive, indicator, results);
                if (results.size() >= MAX_RESULTS) {
                    return;
                }
            }
            return;
        }

        String relativePath = VfsUtilCore.getRelativePath(file, root, '/');
        String path = relativePath == null ? file.getPath() : relativePath;
        if (!matches(file.getName(), path, query, caseSensitive)) {
            return;
        }

        results.add(new DeepSearchResult(
                DeepSearchResult.SourceType.DEPENDENCY,
                file.getName(),
                path,
                dependencySourceName(root),
                file
        ));
    }

    private static boolean matches(String name, String path, String query, boolean caseSensitive) {
        String haystack = name + " " + path;
        if (!caseSensitive) {
            haystack = haystack.toLowerCase(Locale.ROOT);
        }

        for (String token : query.split("\\s+")) {
            if (!haystack.contains(token)) {
                return false;
            }
        }
        return true;
    }

    private static boolean shouldSkipProjectDirectory(VirtualFile directory, ProjectFileIndex fileIndex) {
        return fileIndex.isExcluded(directory) || SKIPPED_PROJECT_DIRECTORIES.contains(directory.getName());
    }

    private static int findTextMatchLine(VirtualFile file, String query, boolean caseSensitive) {
        if (!file.isValid() || file.getLength() > MAX_TEXT_FILE_BYTES || file.getFileType().isBinary()) {
            return -1;
        }

        try {
            String text = VfsUtilCore.loadText(file);
            String searchableText = caseSensitive ? text : text.toLowerCase(Locale.ROOT);
            int index = 0;
            for (String token : query.split("\\s+")) {
                index = searchableText.indexOf(token, index);
                if (index < 0) {
                    return -1;
                }
            }
            return StringUtil.offsetToLineNumber(text, index) + 1;
        } catch (Exception ignored) {
            return -1;
        }
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
}
