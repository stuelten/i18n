package de.sty.i18n.plugin;

import de.sty.i18n.generator.I18nGenerator;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import picocli.CommandLine;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

@Mojo(name = "generate", defaultPhase = LifecyclePhase.GENERATE_SOURCES, threadSafe = true)
public class I18nGeneratorMojo extends AbstractMojo {

    @Parameter(defaultValue = "${project}", readonly = true, required = true)
    private MavenProject project;

    /**
     * Directory where generated sources will be written.
     */
    @Parameter(property = "i18n.outputDirectory", defaultValue = "${project.build.directory}/generated-sources/i18n")
    private File outputDirectory;

    /**
     * Glob pattern for properties to include, relative to each resource directory.
     */
    @Parameter(property = "i18n.includes", defaultValue = "**/*.properties")
    private String includes;

    /**
     * Class name to generate per properties file. Default: I18nMessages
     */
    @Parameter(property = "i18n.className", defaultValue = "I18nMessages")
    private String className;

    /**
     * If set, forces the package name for all generated classes. If empty, the package is derived
     * from the resource path, e.g. src/main/resources/com/acme/app/i18n.properties -> com.acme.app
     */
    @Parameter(property = "i18n.packageName")
    private String packageName;

    /**
     * If true, only process base bundles (files without locale suffix like _en, _de before .properties)
     */
    @Parameter(property = "i18n.baseOnly", defaultValue = "true")
    private boolean baseOnly;

    /**
     * Generate Android-style R class with integer IDs (R.string.keyName) and helper methods getString(...)
     */
    @Parameter(property = "i18n.android", defaultValue = "false")
    private boolean android;

    private static final Pattern LOCALE_SUFFIX = Pattern.compile("_([a-zA-Z]{2,})(_[A-Za-z0-9]+)*\\.properties$");

    @Override
    public void execute() throws MojoExecutionException {
        try {
            Files.createDirectories(outputDirectory.toPath());
        } catch (IOException e) {
            throw new MojoExecutionException("Failed to create output directory: " + outputDirectory, e);
        }

        List<File> resourceDirs = new ArrayList<>();
        project.getResources().forEach(r -> resourceDirs.add(new File(r.getDirectory())));

        int generated = 0;
        for (File resDir : resourceDirs) {
            if (!resDir.isDirectory()) continue;
            List<Path> files = scan(resDir.toPath(), includes);
            for (Path file : files) {
                String name = file.getFileName().toString();
                if (baseOnly && LOCALE_SUFFIX.matcher(name).find()) {
                    continue; // skip locale-specific bundles
                }
                String pkg = derivePackageName(resDir.toPath(), file);
                if (packageName != null && !packageName.isBlank()) {
                    pkg = packageName;
                }
                Path pkgDir = outputDirectory.toPath().resolve(pkg.replace('.', File.separatorChar));
                Path out = pkgDir.resolve(className + ".java");

                getLog().info("Generating I18n class for " + file + " -> " + out + " (package " + pkg + ")");
                try {
                    Files.createDirectories(pkgDir);
                } catch (IOException e) {
                    throw new MojoExecutionException("Failed to create package directory: " + pkgDir, e);
                }

                CommandLine cmd = new CommandLine(new I18nGenerator());
                List<String> args = new ArrayList<>();
                args.add(file.toAbsolutePath().toString());
                args.add("-o"); args.add(out.toAbsolutePath().toString());
                args.add("-p"); args.add(pkg);
                args.add("-c"); args.add(className);
                if (android) {
                    args.add("--android");
                }
                int exit = cmd.execute(args.toArray(new String[0]));
                if (exit != 0) {
                    throw new MojoExecutionException("i18n generation failed for " + file + " with exit code " + exit);
                }
                generated++;
            }
        }

        if (generated > 0) {
            project.addCompileSourceRoot(outputDirectory.getAbsolutePath());
            getLog().info("Added generated sources directory: " + outputDirectory);
        } else {
            getLog().info("No i18n properties found matching pattern '" + includes + "'.");
        }
    }

    private static List<Path> scan(Path root, String glob) throws MojoExecutionException {
        List<Path> result = new ArrayList<>();
        try {
            Files.walk(root)
                 .filter(p -> Files.isRegularFile(p))
                 .filter(p -> matchesGlob(root, p, glob))
                 .forEach(result::add);
        } catch (IOException e) {
            throw new MojoExecutionException("Failed to scan resources in " + root, e);
        }
        return result;
    }

    private static boolean matchesGlob(Path root, Path file, String glob) {
        Path rel = root.relativize(file);
        return root.getFileSystem().getPathMatcher("glob:" + glob).matches(rel);
    }

    private static String derivePackageName(Path resRoot, Path file) {
        Path rel = resRoot.relativize(file).getParent();
        if (rel == null) return "";
        StringBuilder pkg = new StringBuilder();
        for (Path part : rel) {
            if (pkg.length() > 0) pkg.append('.');
            String s = Objects.toString(part.getFileName());
            // sanitize identifier parts
            s = s.replace('-', '_');
            pkg.append(s);
        }
        return pkg.toString();
    }
}
