package com.origin.idea;

import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.OrderEnumerator;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

final class DeepSearchService {
    private static final int MAX_RESULTS = 700;

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
        fileIndex.iterateContent(file -> {
            ProgressManager.checkCanceled();
            indicator.checkCanceled();
            if (results.size() >= MAX_RESULTS) {
                return false;
            }
            if (file.isDirectory() || fileIndex.isInLibrary(file) || fileIndex.isExcluded(file)) {
                return true;
            }

            String path = displayProjectPath(file, basePath);
            if (matches(file.getName(), path, query, caseSensitive)) {
                results.add(new DeepSearchResult(
                        DeepSearchResult.SourceType.PROJECT,
                        file.getName(),
                        path,
                        MyMessageBundle.message("toolwindow.DeepProjectSearch.source.project"),
                        file
                ));
            }
            return true;
        });
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
