package com.origin.idea;

enum SearchScopeOption {
    ALL("All"),
    PROJECT_FILES("Project Files"),
    DEPENDENCIES("Dependencies");

    private final String label;

    SearchScopeOption(String label) {
        this.label = label;
    }

    boolean includesProjectFiles() {
        return this == ALL || this == PROJECT_FILES;
    }

    boolean includesDependencies() {
        return this == ALL || this == DEPENDENCIES;
    }

    @Override
    public String toString() {
        return label;
    }
}
