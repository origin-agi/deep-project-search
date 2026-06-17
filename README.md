# Deep Project Search

Deep Project Search is a Java-based IntelliJ IDEA plugin that searches project files and dependency jar entries from a dedicated tool window.

It is intended to complement IDEA's built-in Search Everywhere when dependency files or jar entries are hard to find before they have been opened once.

## What It Does

Deep Project Search adds a **Deep Search** tool window with one search box, scope selection, and a result list.

Supported scopes:

- **All**: search project files and dependency roots.
- **Project Files**: search files inside the current project.
- **Dependencies**: search library class/source roots, including jar entries.

Typical searches:

- Java classes such as `UserService.class`.
- Dependency resources such as `spring.factories`.
- Service loader files under `META-INF/services`.
- XML, properties, YAML, and other files inside jars.
- Current project source and resource files.

## How To Use

1. Open any project in IntelliJ IDEA.
2. Open the **Deep Search** tool window.
3. Enter a keyword.
4. Choose **All**, **Project Files**, or **Dependencies**.
5. Click **Search**.
6. Double-click a result to open it.

## Features

- Searches current project files.
- Searches dependency class and source roots.
- Searches jar entry names and paths.
- Supports case-sensitive or case-insensitive matching.
- Runs searches in a background task.
- Does not override the double-Shift Search Everywhere shortcut.

## 中文说明

Deep Project Search 是一个 Java 编写的 IntelliJ IDEA 插件，用独立工具窗口统一搜索当前项目文件和依赖 jar 内部文件。

它适合补足 IDEA 双击 Shift 搜索在某些依赖 jar 文件上不稳定的问题，比如某个 jar 里的 class/resource 打开过之后才能搜到。

使用方式：

1. 打开任意 IDEA 项目。
2. 打开左侧 **Deep Search** 工具窗口。
3. 输入关键字。
4. 选择搜索范围：**All**、**Project Files** 或 **Dependencies**。
5. 点击 **Search**。
6. 双击结果打开对应文件、jar resource 或 class。

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
