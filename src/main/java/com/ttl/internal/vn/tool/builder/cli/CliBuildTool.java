package com.ttl.internal.vn.tool.builder.cli;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.Reader;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Scanner;
import java.util.Set;
import java.util.Stack;
import java.util.function.Function;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.ZipOutputStream;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.maven.model.Model;
import org.apache.maven.model.building.DefaultModelBuilderFactory;
import org.apache.maven.model.building.DefaultModelBuildingRequest;
import org.apache.maven.model.building.ModelBuilder;
import org.apache.maven.model.building.ModelBuildingException;
import org.apache.maven.model.building.ModelBuildingRequest;
import org.apache.maven.model.building.ModelBuildingResult;
import org.apache.maven.shared.invoker.DefaultInvocationRequest;
import org.apache.maven.shared.invoker.DefaultInvoker;
import org.apache.maven.shared.invoker.InvocationRequest;
import org.apache.maven.shared.invoker.InvocationResult;
import org.apache.maven.shared.invoker.Invoker;
import org.apache.maven.shared.invoker.MavenInvocationException;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.diff.DiffEntry.ChangeType;
import org.eclipse.jgit.diff.DiffEntry.Side;
import org.eclipse.jgit.errors.RevisionSyntaxException;
import org.eclipse.jgit.lib.TextProgressMonitor;
import org.eclipse.jgit.revwalk.RevCommit;

import com.ttl.internal.vn.tool.builder.git.GitRef;
import com.ttl.internal.vn.tool.builder.util.GitUtil;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

public class CliBuildTool implements AutoCloseable {
    private final boolean clone;
    private final String repoURI;
    private final String username;
    private final String password;
    private final String baseRef;
    private final String targetRef;
    private final File projectPom;
    private final File patchFile;
    private final File javaHome;
    private final File m2SettingXml;
    private final boolean updateSnapshot;
    private final boolean shouldBuildConfigJar;
    private final boolean shouldBuildReleasePackage;
    private final boolean shouldBuildPatch;
    private final List<String> configPrefixes;
    private final List<String> databaseChangePrefixes;

    private final File artifactFolder;

    private final File targetFolder;
    private final File buildFolder;

    private final File projectFolder;
    private final Function<List<String>, Map<String, ArtifactInfo>> getArtifactInfo;

    private GitUtil gitUtil;
    private String previousHEAD;

    // NOTE: Testing-purpose only
    public static void main(String[] args) throws IOException, GitAPIException, MavenInvocationException,
            ClassNotFoundException, ModelBuildingException {
        String username = System.getenv("GIT_USERNAME");
        String password = System.getenv("GIT_PASSWORD");
        String repo = System.getenv("GIT_REPO");
        String baseRef = "d957b6cced461684b2a0c9b57105970f5a76ca91";
        String targetRef = "ee4c4eeaa125be05902448ae3f2a7ea7f9aa2c30";
        File artifactFolder = new File("/tmp/buildArtifact");
        File projectPom = new File(System.getenv("PROJECT_POM"));
        File m2SettingXml = new File(System.getProperty("user.home") + "/.m2/settings_hk.xml");
        File projectFolder = new File("/tmp/buildSrc");
        try (CliBuildTool cliBuildTool = new CliBuildTool(
                false,
                repo,
                projectFolder,
                username,
                password,
                baseRef,
                targetRef,
                artifactFolder,
                true,
                false,
                true,
                false,
                null,
                null,
                null,
                projectPom,
                null,
                m2SettingXml,
                DefaultCliGetArtifactInfo.getArtifactInfo(true))) {
            //
            cliBuildTool.startBuildEnvironment();
            cliBuildTool.build(false);
        }
    }

    public CliBuildTool(
            boolean clone,
            String repoURI,
            File projectFolder,
            String username,
            String password,
            String baseRef,
            String targetRef,
            File artifactFolder,
            boolean updateSnapshot,
            boolean shouldBuildConfigJar,
            boolean shouldBuildReleasePackage,
            boolean shouldBuildPatch,
            String configPrefixes,
            String databaseChangePrefixes,
            File projectPom,
            File javaHome,
            File patchFile,
            File m2SettingXml,
            Function<List<String>, Map<String, ArtifactInfo>> getArtifactInfo) throws ModelBuildingException {
        //
        this.javaHome = javaHome;
        this.clone = clone;
        this.repoURI = repoURI;
        this.patchFile = patchFile;
        this.projectFolder = projectFolder;
        this.username = username;
        this.password = password;
        this.baseRef = baseRef;
        this.targetRef = targetRef;
        this.updateSnapshot = updateSnapshot;
        this.shouldBuildPatch = shouldBuildReleasePackage || shouldBuildPatch;
        this.artifactFolder = artifactFolder;
        this.shouldBuildConfigJar = shouldBuildReleasePackage || shouldBuildConfigJar;
        this.shouldBuildReleasePackage = shouldBuildReleasePackage;
        this.getArtifactInfo = getArtifactInfo;
        this.projectPom = Optional.ofNullable(projectPom).orElse(new File(this.projectFolder, "pom.xml"));
        this.m2SettingXml = m2SettingXml;
        this.configPrefixes = Optional.ofNullable(configPrefixes)
                .map(it -> it.split(","))
                .map(Arrays::asList)
                .map(it -> it.stream().map(String::trim).collect(Collectors.toList()))
                .orElse(List.of("config", "config_company"));
        this.databaseChangePrefixes = Optional.ofNullable(databaseChangePrefixes)
                .map(it -> it.split(","))
                .map(Arrays::asList)
                .map(it -> it.stream().map(String::trim).collect(Collectors.toList()))
                .orElse(List.of("DatabaseChange"));

        this.targetFolder = new File(artifactFolder, "target");
        this.buildFolder = new File(artifactFolder, "build");
    }

    public void startBuildEnvironment() throws IOException, GitAPIException {
        Git git;
        if (clone) {
            git = GitUtil.cloneGitRepo(repoURI, projectFolder, username, password);
        } else {
            git = GitUtil.openLocalRepo(projectFolder);
        }
        this.gitUtil = new GitUtil(git);
        if (!artifactFolder.exists()) {
            artifactFolder.mkdirs();
        }
    }

    public void build(boolean fetch) throws IOException, GitAPIException, MavenInvocationException,
            ClassNotFoundException, ModelBuildingException {
        try {
            if (fetch) {
                // Fetch latest update
                gitUtil.fetch(username, password);
            }

            // Clean-up previous build artifacts
            FileUtils.deleteDirectory(targetFolder);
            FileUtils.deleteDirectory(buildFolder);

            this.previousHEAD = Optional.of(gitUtil.findRef("HEAD")).map(GitRef::getRawRef).map(ref -> {
                if (ref.isSymbolic()) {
                    return ref.getLeaf().getName();
                } else {
                    return ref.getObjectId().getName();
                }
            }).orElseThrow(() -> new UnsupportedOperationException("should not happen with normal git repo"));

            checkout(targetRef);

            if (patchFile != null) {
                gitUtil.applyPatchFile(patchFile);
            }

            // Copy the target pom.xml to the project folder
            File copyTargetPomFile = new File(projectFolder, projectPom.getName());
            if (!copyTargetPomFile.equals(projectPom)) {
                FileUtils.copyFileToDirectory(projectPom, projectFolder);
            }

            Model mavenModelProject = buildMavenProjectModel(new File(projectFolder, projectPom.getName()));

            // Calculate the diff between commits
            List<DiffEntry> diffEntries = gitUtil.getDiff(baseRef, targetRef);

            // Calculate submodules, changed submodules
            List<String> modules = mavenModelProject.getModules();
            Map<String, List<File>> changedModuleFileMap = diffEntries.stream()
                    .filter(entry -> entry.getChangeType() != ChangeType.DELETE)
                    .map(entry -> new File(projectFolder, entry.getPath(Side.NEW)))
                    .collect(Collectors.toMap(it -> relativize(it, projectFolder).getName(0).toString(), it -> {
                        List<File> changedFile = new ArrayList<>();
                        changedFile.add(it);
                        return changedFile;
                    }, (acc, val) -> {
                        acc.addAll(val);
                        return acc;
                    }));

            // Build the classpaths of the project
            Map<String, List<String>> classpathMap = calculateClasspaths(mavenModelProject.getPomFile(), m2SettingXml,
                    modules);
            Map<String, File> moduleToModuleFolderMap = modules.stream()
                    .collect(Collectors.toMap(module -> module, module -> new File(projectFolder, module)));
            Map<String, File> moduleTargetFolderMap = modules.stream()
                    .collect(Collectors.toMap(module -> module, module -> Paths
                            .get(moduleToModuleFolderMap.get(module).getAbsolutePath(), "target", "classes").toFile()));

            // Calculate depedent module map: module -> depend on modules
            Map<String, List<String>> depedendOnModuleMap = modules.stream()
                    .collect(Collectors.toMap(module -> module, module -> {
                        Set<File> moduleClasspaths = classpathMap.get(module).stream().map(File::new)
                                .collect(Collectors.toSet());
                        return moduleTargetFolderMap.entrySet().stream()
                                .filter(it -> moduleClasspaths.contains(it.getValue())).map(Entry::getKey)
                                .collect(Collectors.toList());
                    }));

            // Calculate free modules
            Set<String> aggregratedDependOnModules = depedendOnModuleMap.entrySet().stream()
                    .flatMap(it -> it.getValue().stream()).collect(Collectors.toSet());
            List<String> freeModules = modules.stream().filter(it -> !aggregratedDependOnModules.contains(it))
                    .collect(Collectors.toList());

            // Filter out only free modules that have submodules that changed
            List<String> freeChangedModules = freeModules.stream().filter(module -> {
                List<String> dependOnModules = depedendOnModuleMap.get(module);
                return dependOnModules.stream().anyMatch(changedModuleFileMap::containsKey);
            }).collect(Collectors.toList());

            // Get artifact info for each free module
            Map<String, ArtifactInfo> artifactInfoModuleMap = getArtifactInfo.apply(freeChangedModules);

            if (shouldBuildPatch) {
                buildPatch(freeChangedModules, depedendOnModuleMap, changedModuleFileMap, classpathMap,
                        artifactInfoModuleMap,
                        moduleTargetFolderMap);
            }

            if (shouldBuildConfigJar) {
                buildConfigJar(freeChangedModules, depedendOnModuleMap, changedModuleFileMap, artifactInfoModuleMap,
                        configPrefixes);
            }

            if (shouldBuildReleasePackage) {
                buildReleasePackage(freeChangedModules, depedendOnModuleMap, changedModuleFileMap,
                        artifactInfoModuleMap,
                        databaseChangePrefixes);
            }
        } finally {
            cleanupBuildEnvironment();
        }
    }

    private Model buildMavenProjectModel(File pomFile) throws ModelBuildingException {
        ModelBuilder modelBuilder = new DefaultModelBuilderFactory().newInstance();
        ModelBuildingRequest buildingRequest = new DefaultModelBuildingRequest();
        buildingRequest.setPomFile(pomFile);
        ModelBuildingResult buildingResult = modelBuilder.build(buildingRequest);
        return buildingResult.getEffectiveModel();
    }

    private static Path relativize(File child, File parent) {
        return parent.toPath().toAbsolutePath().relativize(child.toPath().toAbsolutePath());
    }

    private RevCommit checkout(String target) throws RevisionSyntaxException, IOException, GitAPIException {
        return gitUtil.checkoutAndStash(target);
    }

    // package -> changed class files
    private Map<String, List<File>> filterCompiledClassesInChangedModule(URLClassLoader classLoader,
            Map<String, List<File>> changedModuleMap, String module) throws IOException, ClassNotFoundException {
        Map<String, List<File>> packageToChangedClassFilesMap = new HashMap<>();
        Map<String, List<Class<?>>> packageToChangedClassesMap = new HashMap<>();
        if (!changedModuleMap.containsKey(module)) {
            return packageToChangedClassFilesMap;
        }
        List<File> changedJavaFilesInModule = changedModuleMap.get(module).stream()
                .filter(CliBuildTool::isJavaFile)
                .collect(Collectors.toList());
        for (File javaFile : changedJavaFilesInModule) {
            String packageName = getJavaPackage(javaFile);
            String className = getClassName(javaFile);
            List<Class<?>> changedJavaClasses = new ArrayList<>();
            Class<?> clazz = classLoader.loadClass(packageName + "." + className);
            
            Stack<Class<?>> stack = new Stack<>();
            stack.add(clazz);
            
            while(!stack.empty()) {
                Class<?> c = stack.pop();
                changedJavaClasses.add(c);
                stack.addAll(List.of(c.getDeclaredClasses()));
            }
            
            List<File> classFiles = packageToChangedClassFilesMap.compute(packageName, (k, v) -> v == null ? new ArrayList<>() : v);
            packageToChangedClassesMap.put(packageName, changedJavaClasses);
            for (Class<?> c : changedJavaClasses) {
                File classpath = new File(c.getProtectionDomain().getCodeSource().getLocation().getFile());
                File classFile = new File(classpath, javaNameToPath(c.getName()) + ".class");
                if (classFile.isFile()) {
                    Path classRelativePath = relativize(classFile, projectFolder);
                    String classModule = classRelativePath.getName(0).toString();
                    if (classModule.trim().equals(module.trim())) {
                        classFiles.add(classFile);
                    }
                }
            }
        }

        // NOTE: Quite a hacky approach to collect all anonymous classes, required maven clean for reproducible result
        Map<String, List<File>> packageToChangedAnonymousClassMap = packageToChangedClassesMap.entrySet().stream().collect(Collectors.toMap(Entry::getKey, it -> {
            String packageName = it.getKey();
            Set<Class<?>> changedJavaClassSet = new HashSet<>(it.getValue());
            Set<String> classNameSet = changedJavaClassSet.stream()
                    .map(Class::getName)
                    .collect(Collectors.toSet());
            return packageToChangedClassFilesMap.get(packageName).stream()
                    .map(File::getParentFile)
                    .distinct()
                    .map(File::listFiles)
                    .flatMap(Stream::of)
                    .filter(File::isFile)
                    .map(File::getName)
                    .filter(iit -> iit.endsWith(".class"))
                    .map(iit -> iit.substring(0, iit.length() - ".class".length()))
                    .map(iit -> packageName + "." + iit)
                    .filter(iit -> !classNameSet.contains(iit))
                    .map(iit -> {
                        try {
                            return classLoader.loadClass(iit);
                        } catch (ClassNotFoundException e) {
                            throw new RuntimeException(e);
                        }
                    })
                    .filter(Class::isAnonymousClass)
                    .filter(c -> {
                        Class<?> enclosingClass = c;
                        while(enclosingClass.getEnclosingClass() != null) {
                            enclosingClass = enclosingClass.getEnclosingClass();
                        }
                        return changedJavaClassSet.contains(enclosingClass);
                    })
                    .map(c -> {
                        File classpath = new File(c.getProtectionDomain().getCodeSource().getLocation().getFile());
                        return new File(classpath, javaNameToPath(c.getName()) + ".class");
                    })
                    .collect(Collectors.toList());
        }));
        
        // Merge 2 map
        return Stream.of(packageToChangedClassFilesMap, packageToChangedAnonymousClassMap)
                .map(Map::entrySet)
                .flatMap(Set::stream)
                .collect(Collectors.toMap(
                        Entry::getKey, 
                        Entry::getValue, 
                        (val1, val2) -> Stream.of(val1, val2).flatMap(List::stream).collect(Collectors.toList())));
    }

    private void buildConfigJar(List<String> freeModules, Map<String, List<String>> dependentOnModuleMap,
            Map<String, List<File>> changedModuleFileMap, Map<String, ArtifactInfo> artifactInfoModuleMap,
            List<String> configPrefixes)
            throws IOException {
        for (String module : freeModules) {
            File buildConfigFolder = getBuildConfigFolder(module);
            buildConfigFolder.mkdirs();

            ArtifactInfo artifactInfo = artifactInfoModuleMap.get(module);
            List<String> dependOnModules = new ArrayList<>(dependentOnModuleMap.get(module));
            dependOnModules.add(module);

            Map<String, List<File>> changedConfigFilesModuleMap = dependOnModules.stream()
                    .collect(Collectors.toMap(dependOnModule -> dependOnModule, dependOnModule -> {
                        File moduleFolder = new File(projectFolder, dependOnModule);
                        return changedModuleFileMap.getOrDefault(dependOnModule, List.of()).stream()
                                .filter(changedFile -> {
                                    Path relativePathToModule = relativize(changedFile, moduleFolder);
                                    return configPrefixes.stream().anyMatch(relativePathToModule::startsWith);
                                })
                                .collect(Collectors.toList());
                    }));

            // Copy config files starting from lowest priority module to highest priority
            // module
            // Highest priority module will override config from lower priority module, so
            // make sure that config changed between module does not override each other
            for (String dependOnModule : dependOnModules) {
                File moduleFolder = new File(projectFolder, dependOnModule);
                // Start copying
                List<File> changedConfigFiles = changedConfigFilesModuleMap.getOrDefault(dependOnModule, List.of());
                for (File changedFile : changedConfigFiles) {
                    Path relativePathToModule = relativize(changedFile, moduleFolder);
                    File destinationFile = new File(buildConfigFolder,
                            relativePathToModule.subpath(1, relativePathToModule.getNameCount()).toString());
                    FileUtils.copyFile(changedFile, destinationFile);
                }
            }

            if (buildConfigFolder.listFiles() != null && buildConfigFolder.listFiles().length > 0) {
                // Create a jar
                createJarFile(getTargetConfigJarFile(module, artifactInfo), buildConfigFolder);
            }
        }
    }

    private void buildReleasePackage(List<String> freeModules, Map<String, List<String>> dependentOnModuleMap,
            Map<String, List<File>> changedModuleFileMap, Map<String, ArtifactInfo> artifactInfoModuleMap,
            List<String> databaseChangePrefixes)
            throws IOException {

        for (String module : freeModules) {
            File buildReleasePackageFolder = getBuildReleaseFolder(module);
            buildReleasePackageFolder.mkdirs();

            File buildConfigFolder = getBuildConfigFolder(module);
            ArtifactInfo artifactInfo = artifactInfoModuleMap.get(module);
            File patchJarFile = getTargetPatchJarFile(module, artifactInfo);

            File changedReleaseConfigFolder = new File(buildReleasePackageFolder, "config");
            File changedDatabaseFolder = new File(buildReleasePackageFolder, "DatabaseChange");
            File libFolder = new File(buildReleasePackageFolder, "Lib");

            // Copy config changes
            if (buildConfigFolder.listFiles() != null && buildConfigFolder.listFiles().length > 0) {
                FileUtils.copyDirectory(buildConfigFolder, changedReleaseConfigFolder);
            }

            // Copy patch to lib folder
            if (patchJarFile.isFile()) {
                FileUtils.copyFileToDirectory(patchJarFile, libFolder);
            }

            // Copy database changes
            List<String> dependOnModules = new ArrayList<>(dependentOnModuleMap.get(module));
            dependOnModules.add(module);
            Map<String, List<File>> changedDatabaseModuleMap = dependOnModules.stream()
                    .collect(Collectors.toMap(dependOnModule -> dependOnModule, dependOnModule -> {
                        File moduleFolder = new File(projectFolder, dependOnModule);
                        return changedModuleFileMap.getOrDefault(dependOnModule, List.of()).stream()
                                .filter(changedFile -> {
                                    Path relativePathToModule = relativize(changedFile, moduleFolder);
                                    return databaseChangePrefixes.stream().anyMatch(relativePathToModule::startsWith);
                                })
                                .collect(Collectors.toList());
                    }));
            for (String dependOnModule : dependOnModules) {
                File moduleFolder = new File(projectFolder, dependOnModule);
                // Start copying
                List<File> changedDatabaseFiles = changedDatabaseModuleMap.get(dependOnModule);
                for (File changedFile : changedDatabaseFiles) {
                    Path relativePathToModule = relativize(changedFile, moduleFolder);
                    File destinationFile = new File(changedDatabaseFolder,
                            relativePathToModule.subpath(1, relativePathToModule.getNameCount()).toString());
                    FileUtils.copyFile(changedFile, destinationFile);
                }
            }

            if (changedDatabaseFolder.listFiles() == null || changedDatabaseFolder.listFiles().length == 0) {
                FileUtils.deleteDirectory(changedDatabaseFolder);
            }
            if (changedReleaseConfigFolder.listFiles() == null || changedReleaseConfigFolder.listFiles().length == 0) {
                FileUtils.deleteDirectory(changedReleaseConfigFolder);
            }
            if (libFolder.listFiles() == null || libFolder.listFiles().length == 0) {
                FileUtils.deleteDirectory(libFolder);
            }
            if (buildReleasePackageFolder.listFiles() != null && buildReleasePackageFolder.listFiles().length > 0) {
                // Create package release zip files
                createZipFile(getTargetReleasePackageZipFile(module, artifactInfo), buildReleasePackageFolder);
            }
        }
    }

    private String getClassName(File javaFile) {
        if (!isJavaFile(javaFile)) {
            return null;
        }
        String fileName = javaFile.getName();
        return fileName.substring(0, fileName.length() - ".java".length()).trim();
    }

    private Path javaNameToPath(String className) {
        return Paths.get("", className.split("\\."));
    }

    private static boolean haveFileExtension(File file, String extension) {
        extension = extension.trim();
        extension = !extension.startsWith(".") ? "." + extension : extension;
        return file.exists() && file.isFile() && file.getName().toLowerCase().endsWith(extension);
    }

    private static boolean isJavaFile(File file) {
        return haveFileExtension(file, ".java");
    }

    private void buildPatch(List<String> freeModules, Map<String, List<String>> dependentOnModuleMap,
            Map<String, List<File>> changedModuleFileMap, Map<String, List<String>> classpathMap,
            Map<String, ArtifactInfo> artifactInfoModuleMap, Map<String, File> moduleTargetFolderMap)
            throws ClassNotFoundException, IOException, MavenInvocationException, ModelBuildingException {
        for (String module : freeModules) {
            File buildPatchFolder = getBuildPatchFolder(module);
            buildPatchFolder.mkdirs();

            ArtifactInfo artifactInfo = artifactInfoModuleMap.get(module);
            List<String> classpaths = new ArrayList<>(classpathMap.get(module));
            classpaths.add(moduleTargetFolderMap.get(module).getAbsolutePath());
            List<URL> urls = new ArrayList<>();
            for (String classpath : classpaths) {
                urls.add(new File(classpath).toURI().toURL());
            }

            List<String> dependOnModules = new ArrayList<>(dependentOnModuleMap.get(module));
            dependOnModules.add(module);

            try (URLClassLoader loader = new URLClassLoader(urls.toArray(new URL[] {}))) {
                for (String dependOnModule : dependOnModules) {
                    // Copy compiled classes to classes folder
                    for (Map.Entry<String, List<File>> entry : filterCompiledClassesInChangedModule(loader,
                            changedModuleFileMap, dependOnModule).entrySet()) {
                        for (File classFile : entry.getValue()) {
                            File targetFile = Paths.get(buildPatchFolder.getAbsolutePath(),
                                    javaNameToPath(entry.getKey()).toString()).toFile();
                            FileUtils.copyFileToDirectory(classFile, targetFile);
                        }
                    }
                }
            }

            if (buildPatchFolder.listFiles() != null && buildPatchFolder.listFiles().length > 0) {
                // Create a jar
                createJarFile(getTargetPatchJarFile(module, artifactInfo), buildPatchFolder);
            }
        }
    }

    private File getTargetPatchJarFile(String module, ArtifactInfo info) {
        return Paths.get(targetFolder.getAbsolutePath(), module, "patch", info.getPatchName()).toFile();
    }

    private File getTargetConfigJarFile(String module, ArtifactInfo info) {
        String configJarTrimmed = Optional.ofNullable(info.getConfigName()).map(String::trim).orElse(null);
        return StringUtils.isNotBlank(configJarTrimmed)
                ? Paths.get(targetFolder.getAbsolutePath(), module, "config", configJarTrimmed).toFile()
                : Paths.get(targetFolder.getAbsolutePath(), module, "config", "config.jar").toFile();
    }

    private File getTargetReleasePackageZipFile(String module, ArtifactInfo info) {
        String releasePackageTrimmed = Optional.ofNullable(info.getReleasePackageName()).map(String::trim).orElse(null);
        return StringUtils.isNotBlank(releasePackageTrimmed)
                ? Paths.get(targetFolder.getAbsolutePath(), module, "release", releasePackageTrimmed).toFile()
                : Paths.get(targetFolder.getAbsolutePath(), module, "release", "release.zip").toFile();
    }

    private File getBuildPatchFolder(String module) {
        return Paths.get(buildFolder.getAbsolutePath(), module, "patch").toFile();
    }

    private File getBuildConfigFolder(String module) {
        return Paths.get(buildFolder.getAbsolutePath(), module, "config").toFile();
    }

    private File getBuildReleaseFolder(String module) {
        return Paths.get(buildFolder.getAbsolutePath(), module, "release").toFile();
    }

    // module -> classpaths
    private Map<String, List<String>> calculateClasspaths(File pomFile, File m2SettingsXml, List<String> modules)
            throws MavenInvocationException, IOException {
        String classpathFileName = "classpath";
        try {
            Invoker invoker = new DefaultInvoker();
            InvocationRequest request = new DefaultInvocationRequest();
            request.setPomFile(pomFile);
            List<String> goals = new ArrayList<>();
            goals.addAll(List.of("clean", "compile", "dependency:tree", "dependency:build-classpath"));
            request.setGoals(goals);
            request.setBatchMode(true);
            request.setOffline(false);
            request.setUserSettingsFile(m2SettingsXml);
            request.setUpdateSnapshots(updateSnapshot);
            request.setJavaHome(javaHome);
            request.setMavenOpts("-Dmdep.outputFile=" + classpathFileName);
            InvocationResult result = invoker.execute(request);
            if (result.getExitCode() != 0) {
                throw new UnsupportedOperationException(
                        "Can't calculate classpaths, please check your m2 settings or pom file and make sure maven can compile the project successfully");
            }
            Map<String, List<String>> ret = new HashMap<>();
            for (String module : modules) {
                File classpathFile = Paths.get(projectFolder.getAbsolutePath(), module, classpathFileName).toFile();
                try (
                        Reader reader = new FileReader(classpathFile);
                        BufferedReader br = new BufferedReader(reader);) {
                    // Handler classpath seperator for windows also
                    ret.put(module, Arrays.asList(br.readLine().split("[:;]")));
                }
            }
            return ret;
        } finally {
            // Clean-up autogenerated classpath files
            for (String module : modules) {
                File classpathFile = Paths.get(projectFolder.getAbsolutePath(), module, classpathFileName).toFile();
                classpathFile.delete();
            }
            File classpathFile = new File(projectFolder, classpathFileName);
            classpathFile.delete();
        }

    }

    public static void createJarFile(File jarFile, File sourceFolder) throws IOException {
        Manifest manifest = new Manifest();
        manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
        if (!jarFile.exists()) {
            jarFile.getParentFile().mkdirs();
            jarFile.createNewFile();
        }
        try (
                OutputStream os = new FileOutputStream(jarFile);
                JarOutputStream jos = new JarOutputStream(os, manifest)) {
            for (File f : sourceFolder.listFiles()) {
                addToJarFile(jos, sourceFolder, relativize(f, sourceFolder));
            }
        }
    }

    public static void createZipFile(File zipFile, File sourceFolder) throws FileNotFoundException, IOException {
        if (!zipFile.exists()) {
            zipFile.getParentFile().mkdirs();
            zipFile.createNewFile();
        }
        try (
                OutputStream os = new FileOutputStream(zipFile);
                ZipOutputStream zos = new ZipOutputStream(os)) {
            for (File f : sourceFolder.listFiles()) {
                addToZipFile(zos, sourceFolder, relativize(f, sourceFolder));
            }
        }
    }

    private static void addToJarFile(JarOutputStream jos, File workingDir, Path relativePath) throws IOException {
        File file = new File(workingDir, relativePath.toString());
        if (file.isFile()) {
            byte[] buffer = new byte[1024];
            jos.putNextEntry(new JarEntry(relativePath.toString()));
            try (InputStream is = new FileInputStream(file)) {
                int lengthRead;
                while ((lengthRead = is.read(buffer)) > 0) {
                    jos.write(buffer, 0, lengthRead);
                }
            }
            jos.closeEntry();
        } else if (file.isDirectory()) {
            for (File f : file.listFiles()) {
                addToJarFile(jos, workingDir, relativize(f, workingDir));
            }
        }
    }

    private static void addToZipFile(ZipOutputStream zos, File workingDir, Path relativePath) throws IOException {
        File file = new File(workingDir, relativePath.toString());
        if (file.isFile()) {
            byte[] buffer = new byte[1024];
            zos.putNextEntry(new JarEntry(relativePath.toString()));
            try (InputStream is = new FileInputStream(file)) {
                int lengthRead;
                while ((lengthRead = is.read(buffer)) > 0) {
                    zos.write(buffer, 0, lengthRead);
                }
            }
            zos.closeEntry();
        } else if (file.isDirectory()) {
            for (File f : file.listFiles()) {
                addToZipFile(zos, workingDir, relativize(f, workingDir));
            }
        }
    }

    private void restoreGitWorkingDirectory() throws GitAPIException, RevisionSyntaxException, IOException {
        // Recheckout the previous head
        if (previousHEAD != null) {
            checkout(previousHEAD);
        }
        if (previousHEAD != null) {
            previousHEAD = null;
        }
    }

    private void cleanupProjectPom() {
        File copiedProjectPom = new File(projectFolder, projectPom.getName());
        // Clean-up project pom if it's copied from outside of the project folder
        if (!copiedProjectPom.equals(projectPom)) {
            copiedProjectPom.delete();
        }
    }

    public static String getJavaPackage(File javaFile) throws IOException {
        if (!isJavaFile(javaFile)) {
            return null;
        }
        try (
                Reader reader = new FileReader(javaFile);
                BufferedReader br = new BufferedReader(reader)) {
            String line;
            while ((line = br.readLine()) != null) {
                line = line.trim();
                if (line.toLowerCase().startsWith("package ") && line.endsWith(";")) {
                    return line.substring("package ".length(), line.length() - 1).trim();
                }
            }
            return null;
        }
    }

    public void cleanupBuildEnvironment() throws GitAPIException, RevisionSyntaxException, IOException {
        cleanupProjectPom();
        restoreGitWorkingDirectory();
    }

    @Override
    public void close() throws GitAPIException, RevisionSyntaxException, IOException {
        cleanupBuildEnvironment();
        gitUtil.close();
    }

    @Builder
    @Getter
    @AllArgsConstructor
    public static class ArtifactInfo {
        private String configName;
        private String patchName;
        private String releasePackageName;
    }

    public static class DefaultCliGetArtifactInfo {
        public static Function<List<String>, Map<String,ArtifactInfo>> getArtifactInfo(boolean interactive) {
            return modules -> {
                Map<String, ArtifactInfo> info;
                if (interactive) {
                    try (Scanner in = new Scanner(System.in)) {
                        info = new HashMap<>();
                        for (String module : modules) {
                            System.out.println("Artifact info: " + module);
                            boolean isServer = module.toLowerCase().contains("server");
                            String defaultPatchName = isServer ? "SPatch.jar" : "Patch.jar";
                            System.out.print("Patch jar file name[" + defaultPatchName + "]: ");
                            String patchJarFileName = in.nextLine();
                            System.out.print("Config jar file name[Config.jar]: ");
                            String configFileName = in.nextLine();
                            System.out.print("Release package zip file name[ReleasePackage.zip]: ");
                            String releasePackageZipFileName = in.nextLine();

                            info.put(module, ArtifactInfo.builder()
                                    .configName(StringUtils.isBlank(configFileName) ? "Config.jar" : configFileName)
                                    .patchName(StringUtils.isBlank(patchJarFileName) ?  defaultPatchName : patchJarFileName)
                                    .releasePackageName(StringUtils.isBlank(releasePackageZipFileName) ? "ReleasePackage.zip" : releasePackageZipFileName)
                                    .build());
                        }
                    }
                    return info;
                } else {
                    return modules.stream().collect(Collectors.toMap(it -> it, it -> {
                        String moduleName = it.toLowerCase();
                        boolean isServer = moduleName.contains("server");
                        return ArtifactInfo.builder()
                                    .configName("Config.jar")
                                    .patchName(isServer ? "SPatch.jar" : "Patch.jar")
                                    .releasePackageName("ReleasePackage.zip")
                                    .build();
                    }));
                }
            };
        }
    }
}
