package com.origin.idea;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

final class SmartSearchQuery {
    private final SearchScopeOption scope;
    private final MatchMode mode;
    private final boolean caseSensitive;
    private final String text;
    private final List<String> terms;
    private final Pattern pattern;

    private SmartSearchQuery(
            SearchScopeOption scope,
            MatchMode mode,
            boolean caseSensitive,
            String text,
            List<String> terms,
            Pattern pattern
    ) {
        this.scope = scope;
        this.mode = mode;
        this.caseSensitive = caseSensitive;
        this.text = text;
        this.terms = terms;
        this.pattern = pattern;
    }

    static SmartSearchQuery parse(String input, SearchScopeOption selectedScope, boolean caseSensitive) {
        SearchScopeOption scope = selectedScope == null ? SearchScopeOption.ALL : selectedScope;
        String searchText = input == null ? "" : input.trim();
        String lowerSearchText = searchText.toLowerCase(Locale.ROOT);

        if (lowerSearchText.startsWith("project:")) {
            scope = SearchScopeOption.PROJECT_FILES;
            searchText = searchText.substring(searchText.indexOf(':') + 1).trim();
        } else if (lowerSearchText.startsWith("dep:")) {
            scope = SearchScopeOption.DEPENDENCIES;
            searchText = searchText.substring(searchText.indexOf(':') + 1).trim();
        }

        if (searchText.length() >= 2 && searchText.startsWith("\"") && searchText.endsWith("\"")) {
            String phrase = searchText.substring(1, searchText.length() - 1);
            return new SmartSearchQuery(scope, MatchMode.PHRASE, caseSensitive, normalized(phrase, caseSensitive), List.of(), null);
        }

        if (searchText.length() >= 2 && searchText.startsWith("/") && searchText.endsWith("/")) {
            String regex = searchText.substring(1, searchText.length() - 1);
            try {
                return new SmartSearchQuery(scope, MatchMode.REGEX, caseSensitive, searchText, List.of(), compilePattern(regex, caseSensitive));
            } catch (PatternSyntaxException ignored) {
                return termsQuery(scope, searchText, caseSensitive);
            }
        }

        if (searchText.indexOf('*') >= 0 || searchText.indexOf('?') >= 0) {
            return new SmartSearchQuery(scope, MatchMode.WILDCARD, caseSensitive, searchText, List.of(), compilePattern(globToRegex(searchText), caseSensitive));
        }

        return termsQuery(scope, searchText, caseSensitive);
    }

    boolean isBlank() {
        return text.isBlank() && terms.isEmpty();
    }

    boolean includesProjectFiles() {
        return scope.includesProjectFiles();
    }

    boolean includesDependencies() {
        return scope.includesDependencies();
    }

    boolean matchesPath(String name, String path) {
        String haystack = normalized(name + " " + path, caseSensitive);
        return switch (mode) {
            case TERMS -> termsMatch(haystack);
            case PHRASE -> haystack.contains(text);
            case WILDCARD -> pattern.matcher(name).matches() || pattern.matcher(path).matches();
            case REGEX -> pattern.matcher(name + " " + path).find();
        };
    }

    int findContentIndex(String textContent) {
        String searchableText = normalized(textContent, caseSensitive);
        return switch (mode) {
            case TERMS -> findTermsIndex(searchableText);
            case PHRASE -> searchableText.indexOf(text);
            case WILDCARD -> -1;
            case REGEX -> {
                var matcher = pattern.matcher(textContent);
                yield matcher.find() ? matcher.start() : -1;
            }
        };
    }

    private static SmartSearchQuery termsQuery(SearchScopeOption scope, String searchText, boolean caseSensitive) {
        List<String> terms = Arrays.stream(normalized(searchText, caseSensitive).split("\\s+"))
                .filter(term -> !term.isBlank())
                .toList();
        return new SmartSearchQuery(scope, MatchMode.TERMS, caseSensitive, "", terms, null);
    }

    private static Pattern compilePattern(String expression, boolean caseSensitive) {
        int flags = caseSensitive ? 0 : Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE;
        return Pattern.compile(expression, flags);
    }

    private static String normalized(String value, boolean caseSensitive) {
        return caseSensitive ? value : value.toLowerCase(Locale.ROOT);
    }

    private static String globToRegex(String glob) {
        StringBuilder regex = new StringBuilder();
        for (int i = 0; i < glob.length(); i++) {
            char c = glob.charAt(i);
            switch (c) {
                case '*' -> regex.append(".*");
                case '?' -> regex.append('.');
                case '.', '\\', '+', '(', ')', '^', '$', '{', '}', '[', ']', '|' -> regex.append('\\').append(c);
                default -> regex.append(c);
            }
        }
        return regex.toString();
    }

    private boolean termsMatch(String haystack) {
        for (String term : terms) {
            if (!haystack.contains(term)) {
                return false;
            }
        }
        return true;
    }

    private int findTermsIndex(String searchableText) {
        int index = 0;
        int firstIndex = -1;
        for (String term : terms) {
            index = searchableText.indexOf(term, index);
            if (index < 0) {
                return -1;
            }
            if (firstIndex < 0) {
                firstIndex = index;
            }
        }
        return firstIndex;
    }

    private enum MatchMode {
        TERMS,
        PHRASE,
        WILDCARD,
        REGEX
    }
}
