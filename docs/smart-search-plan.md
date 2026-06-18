# Deep Project Search 0.1.6: Smart Search Plan

## Product Principle

Deep Project Search should stay small and focused. Smart Search adds query power without adding more buttons, dropdowns, checkboxes, or an advanced filter panel.

The default user flow remains:

1. Type a keyword.
2. Press Enter.
3. Open the result.

## Supported Query Syntax

- `password`: normal keyword search. Space-separated terms must all match.
- `"spring boot"`: phrase search. The quoted text must match as one phrase.
- `*.properties`: wildcard search for file names and paths.
- `*Controller.class`: wildcard search for class or path names.
- `/BeanFactory.*/`: regex search for file names, paths, and text content.
- `project: password`: search only project files and project text content.
- `dep: spring.factories`: search only dependency paths, resources, and text content.

## Defaults And Compatibility

- Queries without `project:` or `dep:` continue to use the visible `All / Project Files / Dependencies` scope dropdown.
- The `Dependencies` scope is IDE-aware: in IntelliJ IDEA it includes library roots and jar entries; in WebStorm and frontend projects it also includes dependency directories such as `node_modules`.
- Invalid regex queries do not show errors or interrupt the user. They fall back to normal text search.
- Wildcard queries intentionally match file names and paths only, not file content.
- Smart Search is backward-compatible with normal keyword search.

## Test Scenarios

- `password` continues to match project and dependency content.
- `"Plaintext password"` matches that exact phrase.
- `*.properties` matches files such as `signing.properties` and `application.properties`.
- `/.*Controller\\.class/` matches class paths.
- An invalid regex such as `/BeanFactory[/` does not fail the search task.
- `project: password` does not return dependency results.
- `dep: spring.factories` does not return project file results.

## Maintenance Notes

- Keep Smart Search syntax discoverable through README and plugin description examples.
- Prefer adding future query behavior to the input syntax instead of adding visible controls.
- Do not add more scope dropdown values unless the product direction changes away from the small-and-focused model.
