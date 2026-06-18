# Deep Project Search

Deep Project Search is a Java-based JetBrains IDE plugin that searches project files, Java dependency jar entries, and frontend dependency package files from a dedicated tool window.

It is intended to complement the IDE's built-in Search Everywhere when dependency files, jar entries, or package files are hard to find before they have been opened once.

## What It Does

Deep Project Search adds a **Deep Search** tool window with one search box, scope selection, and a result list.

Smart Search keeps the UI small while supporting phrases, wildcards, regex queries, and lightweight scope prefixes from the same keyword field.

Supported scopes:

- **All**: search project files and dependency roots.
- **Project Files**: search files inside the current project.
- **Dependencies**: search Java library class/source/resource roots, jar entries, and frontend dependency directories such as `node_modules`.

Typical searches:

- Java classes such as `UserService.class`.
- Dependency resources such as `spring.factories`.
- Service loader files under `META-INF/services`.
- XML, properties, YAML, and other files inside jars.
- Frontend package files inside `node_modules`.
- Current project source and resource files.
- Text content inside dependency resources and sources.

Smart Search examples:

- `password`: normal keyword search.
- `"spring boot"`: phrase search.
- `*.properties`: wildcard file name/path search.
- `/BeanFactory.*/`: regex search.
- `project: password`: search only project files.
- `dep: spring.factories`: search only dependencies.

The Smart Search product rules are documented in [docs/smart-search-plan.md](docs/smart-search-plan.md).

## How To Use

1. Open any project in IntelliJ IDEA, WebStorm, or another compatible JetBrains IDE.
2. Open the **Deep Search** tool window.
3. Enter a keyword.
4. Choose **All**, **Project Files**, or **Dependencies**.
5. Click **Search**, or press **Enter** while the keyword field is focused.
6. Double-click a result, or press **Enter** on the selected result, to open it.

The keyword field keeps recent searches, so you can rerun previous queries from the dropdown.

For project and dependency text content matches, results show a short preview line and open directly at the matched line and column when possible.

Quick actions:

- **Open**: open the selected result.
- **Copy Path**: copy the selected file, package file, or jar entry path.
- **Clear**: clear the current result list.

## Features

- Searches current project files.
- Searches project text file contents.
- Searches Java dependency class and source roots.
- Searches dependency text resources and source content where available.
- Searches jar entry names, paths, and text content.
- Searches frontend dependency package files under `node_modules`.
- Supports Smart Search phrases, wildcards, regex queries, and lightweight scope prefixes.
- Shows a preview line for project and dependency content matches.
- Opens text content matches at the matched line and column when possible.
- Keeps recent search history.
- Provides quick actions for opening results, copying paths, and clearing results.
- Supports case-sensitive or case-insensitive matching.
- Runs searches in a background task.
- Does not override the double-Shift Search Everywhere shortcut.

## 中文说明

Deep Project Search 是一个 Java 编写的 JetBrains IDE 插件，用独立工具窗口统一搜索当前项目文件、Java 依赖 jar 内部文件，以及前端项目里的依赖包文件。

它适合补足 IDE 双击 Shift 搜索在某些依赖文件上不稳定的问题，比如某个 jar 里的 class/resource 或 `node_modules` 里的包文件打开过之后才能搜到。

Smart Search 让插件保持简单：仍然只有一个关键字输入框，但可以自动识别短语、通配符、正则和轻量范围前缀。

使用方式：

1. 打开任意 IntelliJ IDEA、WebStorm 或其他兼容的 JetBrains IDE 项目。
2. 打开左侧 **Deep Search** 工具窗口。
3. 输入关键字。
4. 选择搜索范围：**All**、**Project Files** 或 **Dependencies**。
5. 点击 **Search**，或者在输入框中直接按 **Enter**。
6. 双击结果，或者选中结果后按 **Enter**，打开对应文件、前端依赖包文件、jar resource 或 class。

输入框会保存最近搜索记录，可以从下拉列表里快速重复搜索。

如果命中的是项目或依赖里的文本内容，结果会显示一行预览，并在可行时直接跳转到命中行和命中列。

快捷操作：

- **Open**：打开选中的结果。
- **Copy Path**：复制选中文件、前端依赖包文件或 jar entry 路径。
- **Clear**：清空当前结果列表。

Smart Search 示例：

- `password`：普通关键字搜索。
- `"spring boot"`：完整短语搜索。
- `*.properties`：按文件名或路径通配符搜索。
- `/BeanFactory.*/`：正则搜索。
- `project: password`：只搜索项目文件。
- `dep: spring.factories`：只搜索依赖。

## Local Development

Run the plugin in a sandbox IDE:

```bash
JAVA_HOME="/Applications/IntelliJ IDEA.app/Contents/jbr/Contents/Home" ./gradlew runIde
```

Build a local ZIP:

```bash
JAVA_HOME="/Applications/IntelliJ IDEA.app/Contents/jbr/Contents/Home" ./gradlew buildPlugin
```

Sign the ZIP with the local learning certificate configured in `build.gradle.kts`:

```bash
JAVA_HOME="/Applications/IntelliJ IDEA.app/Contents/jbr/Contents/Home" ./gradlew signPlugin
```
