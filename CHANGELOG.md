<!-- Keep a Changelog guide -> https://keepachangelog.com -->

# Deep Project Search Changelog

## [Unreleased]

### Fixed

- Replaced manual `getChildren()` recursion with `VirtualFileVisitor` to eliminate excessive VFS notifications that trigger IDE performance warnings.
- Replaced full-file `loadText()` with line-by-line streaming in text content search, reducing memory usage.
- Pre-compiled whitespace regex to avoid recompiling `Pattern` on every preview build.
- Batch-updated list model and used `StringBuilder` in the result renderer to reduce EDT string allocations.
- Added missing cancellation checks inside recursive file traversal loops.

## [0.2.0]

### Added

- Added platform-level compatibility so the plugin can support WebStorm and other JetBrains IDEs.
- Added frontend dependency directory search for `node_modules` while keeping existing IntelliJ IDEA library and jar search behavior.

## [0.1.7]

### Fixed

- Replaced deprecated project base directory API usage to keep Plugin Verifier reports clean.

## [0.1.6]

### Added

- Added Smart Search syntax for phrases, wildcards, regex queries, and lightweight scope prefixes.
- Added `docs/smart-search-plan.md` to document the product direction and supported query behavior.

## [0.1.5]

### Added

- Search text content inside dependency source/resource roots and jar entries.
- Added recent search history in the keyword field.
- Added quick actions to open the selected result, copy its path, and clear results.

## [0.1.4]

### Added

- Added result previews for project file content matches.
- Open project file content matches directly at the matched line and column.

## [0.1.3]

### Fixed

- Search project file contents, including local ignored files under the project directory.

## [0.1.2]

### Added

- Added Enter key support for searching from the keyword field and opening selected results.

## [0.1.1]

### Fixed

- Made the keyword input field visible in narrow tool windows.

## [0.1.0]

### Added

- Added the Java-based Deep Search tool window.
- Added project file search.
- Added dependency class/source root search, including jar entries.
- Added scope selection for all files, project files, or dependencies.
- Added background search execution and double-click result opening.
