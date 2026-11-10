package com.example.alerting.support;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * A tiny architecture checker that reads Java source files as text, with no library.
 *
 * <p>It stands in for a tool such as ArchUnit. It parses the package, the imports and the class name of
 * every source file, groups packages into <em>modules</em> and reports rule violations as readable
 * messages. An empty list means the rule holds. The scanner itself is tested in {@code SourceScannerTest}
 * so that a rule cannot pass just because the scanner is broken.
 *
 * <p>A module is the first package below the base package, for example {@code event}. Packages listed in
 * {@code modulesWithSubmodules} (here {@code channel}) are split one level further, so
 * {@code channel.email} is a module of its own.
 */
public final class SourceScanner {

    /** The module of classes that live directly in the base package. */
    public static final String ROOT_MODULE = "(root)";

    private static final Pattern PACKAGE = Pattern.compile("(?m)^package\\s+([\\w.]+);");
    private static final Pattern IMPORT = Pattern.compile("(?m)^import\\s+(?:static\\s+)?([\\w.]+?)(\\.\\*)?;");
    private static final Pattern ENTITY = Pattern.compile("(?m)^@Entity\\b");
    private static final Pattern BLOCK_COMMENT = Pattern.compile("(?s)/\\*.*?\\*/");
    private static final Pattern LINE_COMMENT = Pattern.compile("//[^\\n]*");

    /**
     * One parsed source file.
     *
     * @param path where the file was read from
     * @param module the module the file's package belongs to
     * @param packageName the declared package
     * @param className the file name without {@code .java}
     * @param imports every imported name; wildcard imports end in {@code .*}
     * @param code the source text with all comments removed
     * @param entity whether the class is annotated with {@code @Entity}
     */
    public record SourceFile(Path path, String module, String packageName, String className, List<String> imports,
                             String code, boolean entity) {

        /**
         * The fully qualified class name.
         *
         * @return package and class name joined by a dot
         */
        String qualifiedName() {
            return packageName + "." + className;
        }
    }

    private final String basePackage;
    private final Set<String> modulesWithSubmodules;

    /**
     * Creates a scanner.
     *
     * @param basePackage the application's base package, for example {@code com.example.alerting}
     * @param modulesWithSubmodules top-level packages whose sub-packages count as separate modules
     */
    public SourceScanner(String basePackage, Set<String> modulesWithSubmodules) {
        this.basePackage = basePackage;
        this.modulesWithSubmodules = Set.copyOf(modulesWithSubmodules);
    }

    /**
     * Reads every Java source file below a directory, except {@code package-info.java}.
     *
     * @param root the source root, for example {@code src/main/java}
     * @return the parsed files, sorted by path
     * @throws IOException if a file cannot be read
     */
    public List<SourceFile> scan(Path root) throws IOException {
        List<Path> paths;
        try (Stream<Path> walk = Files.walk(root)) {
            paths = walk.filter(path -> path.toString().endsWith(".java"))
                    .filter(path -> !path.getFileName().toString().equals("package-info.java"))
                    .sorted()
                    .toList();
        }
        List<SourceFile> files = new ArrayList<>();
        for (Path path : paths) {
            files.add(parse(path));
        }
        return files;
    }

    private SourceFile parse(Path path) throws IOException {
        String content = Files.readString(path, StandardCharsets.UTF_8);
        Matcher packageMatcher = PACKAGE.matcher(content);
        if (!packageMatcher.find()) {
            throw new IllegalStateException("No package declaration in " + path);
        }
        String packageName = packageMatcher.group(1);
        List<String> imports = new ArrayList<>();
        Matcher importMatcher = IMPORT.matcher(content);
        while (importMatcher.find()) {
            imports.add(importMatcher.group(1) + (importMatcher.group(2) != null ? ".*" : ""));
        }
        String fileName = path.getFileName().toString();
        String className = fileName.substring(0, fileName.length() - ".java".length());
        String code = LINE_COMMENT.matcher(BLOCK_COMMENT.matcher(content).replaceAll("")).replaceAll("");
        return new SourceFile(path, moduleOf(packageName), packageName, className, List.copyOf(imports), code,
                ENTITY.matcher(content).find());
    }

    /**
     * Finds the module a class or package belongs to.
     *
     * @param qualifiedName a fully qualified class name, a package name, or either followed by {@code .*}
     * @return the module name, {@link #ROOT_MODULE} for the base package itself, or {@code null} for names
     *         outside the application
     */
    public String moduleOf(String qualifiedName) {
        String name = qualifiedName.endsWith(".*")
                ? qualifiedName.substring(0, qualifiedName.length() - 2)
                : qualifiedName;
        String prefix = basePackage + ".";
        if (!name.startsWith(prefix)) {
            return name.equals(basePackage) ? ROOT_MODULE : null;
        }
        List<String> packageSegments = new ArrayList<>();
        for (String segment : name.substring(prefix.length()).split("\\.")) {
            if (!segment.isEmpty() && Character.isLowerCase(segment.charAt(0))) {
                packageSegments.add(segment);
            } else {
                break;
            }
        }
        if (packageSegments.isEmpty()) {
            return ROOT_MODULE;
        }
        String first = packageSegments.get(0);
        if (modulesWithSubmodules.contains(first) && packageSegments.size() >= 2) {
            return first + "." + packageSegments.get(1);
        }
        return first;
    }

    /**
     * Checks that every module only imports the modules it is allowed to depend on.
     *
     * @param files the parsed files
     * @param allowed for each module, the other modules it may import; a module missing from the map is
     *        itself a violation, so new modules have to be registered on purpose
     * @return one message per violation
     */
    public List<String> moduleDependencyViolations(List<SourceFile> files, Map<String, Set<String>> allowed) {
        List<String> violations = new ArrayList<>();
        for (SourceFile file : files) {
            if (ROOT_MODULE.equals(file.module())) {
                continue;
            }
            Set<String> allowedModules = allowed.get(file.module());
            if (allowedModules == null) {
                violations.add(file.path() + ": module '" + file.module() + "' is not registered in the dependency rules");
                continue;
            }
            for (String imported : file.imports()) {
                String module = moduleOf(imported);
                if (module == null || module.equals(file.module()) || ROOT_MODULE.equals(module)) {
                    continue;
                }
                if (!allowedModules.contains(module)) {
                    violations.add(file.path() + ": '" + file.module() + "' must not depend on '" + module
                            + "' (import " + imported + ")");
                }
            }
        }
        return violations;
    }

    /**
     * Checks that selected classes import nothing from the given prefixes. Used to keep pure business
     * logic free of framework, persistence and I/O types.
     *
     * @param files the parsed files
     * @param isPure selects the classes that must stay pure
     * @param forbiddenPrefixes import prefixes that pure classes must not use
     * @return one message per violation
     */
    public List<String> pureClassViolations(List<SourceFile> files, Predicate<SourceFile> isPure,
                                            List<String> forbiddenPrefixes) {
        List<String> violations = new ArrayList<>();
        for (SourceFile file : files) {
            if (!isPure.test(file)) {
                continue;
            }
            for (String imported : file.imports()) {
                for (String prefix : forbiddenPrefixes) {
                    if (imported.startsWith(prefix)) {
                        violations.add(file.path() + ": pure class " + file.className() + " must not import " + imported);
                    }
                }
            }
        }
        return violations;
    }

    /**
     * Checks that the web layer never imports an entity, so entities are not returned from controllers.
     *
     * @param files the parsed files
     * @param webModule the name of the web module
     * @return one message per violation
     */
    public List<String> entityLeaks(List<SourceFile> files, String webModule) {
        Set<String> entityNames = new HashSet<>();
        Set<String> entityPackages = new HashSet<>();
        for (SourceFile file : files) {
            if (file.entity()) {
                entityNames.add(file.qualifiedName());
                entityPackages.add(file.packageName());
            }
        }
        List<String> violations = new ArrayList<>();
        for (SourceFile file : files) {
            if (!webModule.equals(file.module())) {
                continue;
            }
            for (String imported : file.imports()) {
                boolean leaks = entityNames.contains(imported)
                        || (imported.endsWith(".*") && entityPackages.contains(imported.substring(0, imported.length() - 2)));
                if (leaks) {
                    violations.add(file.path() + ": the web layer must not import the entity " + imported);
                }
            }
        }
        return violations;
    }

    /**
     * Checks that source code does not use certain APIs, ignoring comments.
     *
     * @param files the parsed files
     * @param forbiddenSnippets text that must not appear in code, for example {@code Instant.now()}
     * @param exemptClasses simple class names that are allowed to use them
     * @return one message per violation
     */
    public List<String> forbiddenApiUsages(List<SourceFile> files, List<String> forbiddenSnippets,
                                           Set<String> exemptClasses) {
        List<String> violations = new ArrayList<>();
        for (SourceFile file : files) {
            if (exemptClasses.contains(file.className())) {
                continue;
            }
            for (String snippet : forbiddenSnippets) {
                if (file.code().contains(snippet)) {
                    violations.add(file.path() + ": must not use " + snippet);
                }
            }
        }
        return violations;
    }
}
