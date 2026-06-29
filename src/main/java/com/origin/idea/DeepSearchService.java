package com.origin.idea;

import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.OrderEnumerator;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileVisitor;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

import org.jetbrains.annotations.NotNull;

final class DeepSearchService {
    private static final int MAX_RESULTS = 700;
    private static final long MAX_TEXT_FILE_BYTES = 1_000_000L;
    private static final int MAX_PREVIEW_CHARS = 220;
    private static final Pattern WHITESPACE = Pattern.compile("\\s+");
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
    private static final Set<String> FRONTEND_DEPENDENCY_DIRECTORIES = Set.of(
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
        VirtualFile baseDirectory = basePath == null ? null : VfsUtil.findFile(Path.of(basePath), true);
        if (baseDirectory == null) {
            return;
        }
        VfsUtilCore.visitChildrenRecursively(baseDirectory, new VirtualFileVisitor<Void>() {
            @Override
            public boolean visitFile(@NotNull VirtualFile file) {
                if (results.size() >= MAX_RESULTS) {
                    return false;
                }
                ProgressManager.checkCanceled();
                indicator.checkCanceled();

                if (file.isDirectory()) {
                    return !shouldSkipProjectDirectory(file, fileIndex);
                }

                if (fileIndex.isInLibrary(file)) {
                    return true;
                }

                String path = displayProjectPath(file, basePath);
                String source = MyMessageBundle.message("toolwindow.DeepProjectSearch.source.project");
                boolean pathMatched = query.matchesPath(file.getName(), path);
                TextMatch textMatch = findTextMatch(file, query, indicator);
                if (!pathMatched && textMatch == null) {
                    return true;
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
                return true;
            }
        });
    }

    private static void searchDependencyRoots(Project project, SmartSearchQuery query, ProgressIndicator indicator, List<DeepSearchResult> results) {
        Set<VirtualFile> roots = new LinkedHashSet<>();
        roots.addAll(List.of(OrderEnumerator.orderEntries(project).withoutSdk().librariesOnly().recursively().classes().getRoots()));
        roots.addAll(List.of(OrderEnumerator.orderEntries(project).withoutSdk().librariesOnly().recursively().sources().getRoots()));
        addFrontendDependencyRoots(project, roots, indicator);

        for (VirtualFile root : roots) {
            ProgressManager.checkCanceled();
            indicator.checkCanceled();
            if (results.size() >= MAX_RESULTS) {
                return;
            }
            VfsUtilCore.visitChildrenRecursively(root, new VirtualFileVisitor<Void>() {
                @Override
                public boolean visitFile(@NotNull VirtualFile file) {
                    if (results.size() >= MAX_RESULTS) {
                        return false;
                    }
                    ProgressManager.checkCanceled();
                    indicator.checkCanceled();

                    if (file.isDirectory()) {
                        return true;
                    }

                    String relativePath = VfsUtilCore.getRelativePath(file, root, '/');
                    String path = relativePath == null ? file.getPath() : relativePath;
                    boolean pathMatched = query.matchesPath(file.getName(), path);
                    TextMatch textMatch = findTextMatch(file, query, indicator);
                    if (!pathMatched && textMatch == null) {
                        return true;
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
                    return true;
                }
            });
        }
    }

    private static void addFrontendDependencyRoots(Project project, Set<VirtualFile> roots, ProgressIndicator indicator) {
        String basePath = project.getBasePath();
        VirtualFile baseDirectory = basePath == null ? null : VfsUtil.findFile(Path.of(basePath), true);
        if (baseDirectory == null) {
            return;
        }
        VfsUtilCore.visitChildrenRecursively(baseDirectory, new VirtualFileVisitor<Void>() {
            @Override
            public boolean visitFile(@NotNull VirtualFile file) {
                ProgressManager.checkCanceled();
                indicator.checkCanceled();

                if (file.isDirectory()) {
                    if (FRONTEND_DEPENDENCY_DIRECTORIES.contains(file.getName())) {
                        roots.add(file);
                        return false;
                    }
                    return !shouldSkipFrontendRootScan(file);
                }
                return true;
            }
        });
    }

    private static boolean shouldSkipProjectDirectory(VirtualFile directory, ProjectFileIndex fileIndex) {
        return fileIndex.isExcluded(directory) || SKIPPED_PROJECT_DIRECTORIES.contains(directory.getName());
    }

    private static boolean shouldSkipFrontendRootScan(VirtualFile directory) {
        return SKIPPED_PROJECT_DIRECTORIES.contains(directory.getName());
    }

    private static TextMatch findTextMatch(VirtualFile file, SmartSearchQuery query, ProgressIndicator indicator) {
        if (!file.isValid() || file.getLength() > MAX_TEXT_FILE_BYTES || file.getFileType().isBinary()) {
            return null;
        }

        Charset charset = file.getCharset();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(file.getInputStream(), charset))) {
            String line;
            int lineNumber = 0;
            while ((line = reader.readLine()) != null) {
                lineNumber++;
                indicator.checkCanceled();
                int columnNumber = query.findInLine(line, 0);
                if (columnNumber >= 0) {
                    return new TextMatch(lineNumber, columnNumber, buildPreview(line));
                }
            }
            return null;
        } catch (IOException ignored) {
            return null;
        }
    }

    private static String buildPreview(String lineText) {
        String preview = lineText.replace('\t', ' ').trim();
        if (preview.isEmpty()) {
            return preview;
        }
        preview = WHITESPACE.matcher(preview).replaceAll(" ");
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
