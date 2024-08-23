package com.ttl.internal.vn.tool.builder.cli;

import com.ttl.internal.vn.tool.builder.git.GitRef;
import com.ttl.internal.vn.tool.builder.task.*;
import com.ttl.internal.vn.tool.builder.util.GitUtil;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.maven.model.Model;
import org.apache.maven.model.building.*;
import org.apache.maven.model.io.xpp3.MavenXpp3Reader;
import org.apache.maven.shared.invoker.*;
import org.codehaus.plexus.util.xml.pull.XmlPullParserException;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.errors.RevisionSyntaxException;
import org.eclipse.jgit.revwalk.RevCommit;

import java.io.*;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.Map.Entry;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.Function;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.ZipOutputStream;

public class CliBuildTool implements AutoCloseable {
    private final boolean clone;
    private final String repoURI;
    private final String username;
    private final String password;
    private final String baseRef;
    private final String targetRef;
    private final String entryFilter;
    private final File patchFile;
    private final File javaHome;
    private final File m2SettingXml;
    private final boolean updateSnapshot;
    private final boolean shouldBuildConfigJar;
    private final boolean shouldBuildReleasePackage;
    private final boolean shouldBuildPatch;
    private final List<String> configPrefixes;
    private final List<String> databaseChangePrefixes;
    private final boolean useWorkingDirectory;
    private final File artifactFolder;

    private final File targetFolder;
    private final File buildFolder;

    private final File projectFolder;
    private final Function<List<String>, Map<String, ArtifactInfo>> getArtifactInfo;

    private GitUtil gitUtil;

    // NOTE: Testing-purpose only
    public static void main(String[] args) throws IOException, GitAPIException, MavenInvocationException,
            ClassNotFoundException, ModelBuildingException, XmlPullParserException, InterruptedException {
        String username = System.getenv("GIT_USERNAME");
        String password = System.getenv("GIT_PASSWORD");
        String repo = System.getenv("GIT_REPO");
        String baseRef = "d957b6cced461684b2a0c9b57105970f5a76ca91";
        String targetRef = "ee4c4eeaa125be05902448ae3f2a7ea7f9aa2c30";
        File artifactFolder = new File("/tmp/buildArtifact");
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
                null,
                artifactFolder,
                true,
                false,
                true,
                false,
                false,
                null,
                null,
                null,
                null,
                m2SettingXml,
                DefaultCliGetArtifactInfo.getArtifactInfo(true))) {
            //
            cliBuildTool.startBuildEnvironment();
            cliBuildTool.build(false).start();
        }
    }

    // NOTE: When useWorkingDirectory is enabled, it will build from baseRef to
    // working dir without doing any checkout or patching or copy project pom, only
    // use this to build a test artifact
    public CliBuildTool(
            boolean clone,
            String repoURI,
            File projectFolder,
            String username,
            String password,
            String baseRef,
            String targetRef,
            String entryFilter,
            File artifactFolder,
            boolean updateSnapshot,
            boolean shouldBuildConfigJar,
            boolean shouldBuildReleasePackage,
            boolean shouldBuildPatch,
            boolean useWorkingDirectory,
            String configPrefixes,
            String databaseChangePrefixes,
            File javaHome,
            File patchFile,
            File m2SettingXml,
            Function<List<String>, Map<String, ArtifactInfo>> getArtifactInfo) throws ModelBuildingException {
        //
        this.entryFilter = entryFilter;
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
        this.m2SettingXml = m2SettingXml;
        this.useWorkingDirectory = useWorkingDirectory;
        this.configPrefixes = Optional.ofNullable(configPrefixes)
                .map(it -> it.split(","))
                .map(Arrays::asList)
                .map(it -> it.stream().map(String::trim).collect(Collectors.toList()))
                .orElse(Arrays.asList("config", "config_company"));
        this.databaseChangePrefixes = Optional.ofNullable(databaseChangePrefixes)
                .map(it -> it.split(","))
                .map(Arrays::asList)
                .map(it -> it.stream().map(String::trim).collect(Collectors.toList()))
                .orElse(Collections.singletonList("DatabaseChange"));

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

    public static List<DiffEntry> getDiff(GitUtil gitUtil, String baseRef, String targetRef, String entryFilter, boolean useWorkingDirectory) throws IOException {
        List<DiffEntry> diffEntries;
        if (useWorkingDirectory) {
            diffEntries = gitUtil.getDiffWd(baseRef);
        } else {
            diffEntries = gitUtil.getDiff(baseRef, targetRef);
        }
        return Optional
                .ofNullable(entryFilter).map(Pattern::compile).map(pattern -> diffEntries.stream()
                        .filter(entry -> !pattern.matcher(entry.toString()).find()).collect(Collectors.toList()))
                .orElse(diffEntries);
    }

    public List<DiffEntry> getDiff() throws IOException {
        return getDiff(gitUtil, baseRef, targetRef, entryFilter, useWorkingDirectory);
    }

    @Getter
    @Setter
    public static class CliBuildToolBuildContext {
        private String previousHEAD;
        private List<String> leafModuleRelativePaths;
        private List<DiffEntry> diffEntries;
        private Map<String, List<DiffEntry>> moduleToDeletedEntriesMap;
        private Map<String, List<DiffEntry>> moduleToChangedEntriesMap;
        private Map<String, List<String>> classpathMap;
        private Map<String, List<String>> depedendOnOtherModuleMap;
        private Map<String, List<String>> depedendOnModuleMap;
        private List<String> freeChangedModules;
        private List<String> freeDeletedModules;
        private Map<String, ArtifactInfo> artifactInfoModuleMap;
    }

    public BuildTask build(boolean fetch) throws IOException, GitAPIException, MavenInvocationException,
            ClassNotFoundException, ModelBuildingException, XmlPullParserException, InterruptedException {
        BuildTask buildTask = new BuildTask() {
            @Override
            public void cleanup() throws GitAPIException, IOException {
                if (!useWorkingDirectory) {
                    restoreGitWorkingDirectory(((CliBuildToolBuildContext) getBuildCtx()).getPreviousHEAD());
                }
            }
        };

        buildTask.setBuildCtx(new CliBuildToolBuildContext());
        if (fetch) {
            GitFetchTask gitFetchTask = new GitFetchTask(gitUtil, username, password);
            gitFetchTask.setProgressMonitor(new GitCloneProgressMonitor(gitFetchTask, new PrintWriter(System.out)));
            buildTask.addSubTask(new DiscreteTask("Fetch latest update") {
                @Override
                public boolean start() throws GitAPIException {
                    inProgress = true;
                    gitFetchTask.start();
                    return true;
                }

                @Override
                public int totalWork() {
                    int totalWork = gitFetchTask.totalWork();
                    return Math.max(totalWork, 0);
                }

                @Override
                public int doneWork() {
                    return gitFetchTask.doneWork();
                }
            });
        }

        buildTask.addSubTask(new DiscreteTask("Clean-up previous build artifacts") {
            @Override
            public boolean start() throws IOException {
                inProgress = true;
                FileUtils.deleteDirectory(targetFolder);
                FileUtils.deleteDirectory(buildFolder);
                return true;
            }
        });

        buildTask.addSubTask(new DiscreteTask("Saving previous git HEAD") {
            @Override
            public boolean start() throws Exception {
                inProgress = true;
                CliBuildToolBuildContext buildCtx = (CliBuildToolBuildContext) buildTask.getBuildCtx();
                buildCtx.setPreviousHEAD(Optional.of(gitUtil.findRef("HEAD")).map(GitRef::getRawRef).map(ref -> {
                    if (ref.isSymbolic()) {
                        return ref.getLeaf().getName();
                    } else {
                        return ref.getObjectId().getName();
                    }
                }).orElseThrow(() -> new UnsupportedOperationException("should not happen with normal git repo")));
                return true;
            }
        });

        // NOTE: If useWorkingDirectory is enabled it should not perform any checkout or
        // apply git patch
        if (!useWorkingDirectory) {
            buildTask.addSubTask(new DiscreteTask("Checking out " + targetRef) {
                @Override
                public boolean start() throws Exception {
                    inProgress = true;
                    checkout(targetRef);
                    return true;
                }
            });
            if (patchFile != null) {
                buildTask.addSubTask(new DiscreteTask("Apply git patch") {
                    @Override
                    public boolean start() throws Exception {
                        inProgress = true;
                        gitUtil.applyPatchFile(patchFile);
                        return true;
                    }
                });
            }
        }

        buildTask.addSubTask(new DiscreteTask("Get all leaf maven submodule relative paths and their output folders") {
            @Override
            public boolean start() throws Exception {
                inProgress = true;
                Deque<String> modulesStack = new ArrayDeque<>();
                modulesStack.add(".");
                List<String> leafModuleRelativePaths = new ArrayList<>();
                while (!modulesStack.isEmpty()) {
                    String moduleRelativePath = modulesStack.pop();
                    File moduleFolder = new File(projectFolder, moduleRelativePath);
                    File modulePom = new File(moduleFolder, "pom.xml");
                    Model mavenModel = buildMavenProjectModel(modulePom);
                    if (mavenModel.getModules().isEmpty()) {
                        String normalizeModuleRelativePath = relativize(moduleFolder, projectFolder).toString();
                        leafModuleRelativePaths.add(normalizeModuleRelativePath);
                    } else {
                        modulesStack.addAll(mavenModel.getModules().stream().map(submodule -> Paths.get(moduleRelativePath, submodule)).map(Path::toString).collect(Collectors.toList()));
                    }
                }
                CliBuildToolBuildContext buildCtx = (CliBuildToolBuildContext) buildTask.getBuildCtx();
                buildCtx.setLeafModuleRelativePaths(leafModuleRelativePaths);
                return true;
            }
        });

        buildTask.addSubTask(new DiscreteTask("Calculate the diff") {
            @Override
            public boolean start() throws Exception {
                inProgress = true;
                ((CliBuildToolBuildContext) buildTask.getBuildCtx()).setDiffEntries(getDiff());
                return true;
            }
        });

        // module relative path -> diffs (group by type)
        buildTask.addSubTask(new DiscreteTask("Calculate submodules, changed submodules, submodules that have deleted entry") {
            @Override
            public boolean start() {
                inProgress = true;
                CliBuildToolBuildContext buildCtx = (CliBuildToolBuildContext) buildTask.getBuildCtx();
                Map<String, List<DiffEntry>> moduleToDeletedEntriesMap = new HashMap<>();
                buildCtx.setModuleToDeletedEntriesMap(moduleToDeletedEntriesMap);
                Map<String, List<DiffEntry>> moduleToChangedEntriesMap = new HashMap<>();
                buildCtx.setModuleToChangedEntriesMap(moduleToChangedEntriesMap);
                List<String> leafModuleRelativePaths = buildCtx.getLeafModuleRelativePaths();
                for (DiffEntry diffEntry : buildCtx.getDiffEntries()) {
                    switch (diffEntry.getChangeType()) {
                        case DELETE: {
                            Optional.ofNullable(getModuleFromRelativePath(projectFolder.getAbsolutePath(), diffEntry.getOldPath(), leafModuleRelativePaths))
                                    .ifPresent(moduleRelativePath ->  moduleToDeletedEntriesMap.computeIfAbsent(moduleRelativePath, k -> new ArrayList<>()).add(diffEntry));
                            break;
                        }
                        case MODIFY:
                        case COPY:
                        case ADD: {
                            Optional.ofNullable(getModuleFromRelativePath(projectFolder.getAbsolutePath(), diffEntry.getNewPath(), leafModuleRelativePaths))
                                    .ifPresent(moduleRelativePath ->  moduleToChangedEntriesMap.computeIfAbsent(moduleRelativePath, k -> new ArrayList<>()).add(diffEntry));
                            break;
                        }
                        case RENAME: {
                            Optional.ofNullable(getModuleFromRelativePath(projectFolder.getAbsolutePath(), diffEntry.getOldPath(), leafModuleRelativePaths))
                                    .ifPresent(deletedModuleRelativePath ->  moduleToDeletedEntriesMap.computeIfAbsent(deletedModuleRelativePath, k -> new ArrayList<>()).add(diffEntry));
                            Optional.ofNullable(getModuleFromRelativePath(projectFolder.getAbsolutePath(), diffEntry.getNewPath(), leafModuleRelativePaths))
                                    .ifPresent(modifyModuleRelativePath ->  moduleToChangedEntriesMap.computeIfAbsent(modifyModuleRelativePath, k -> new ArrayList<>()).add(diffEntry));
                            break;
                        }
                        default:
                            throw new UnsupportedOperationException("not possible to enter here");
                    }
                }
                return true;
            }
        });

        buildTask.addSubTask(new DiscreteTask("Calculate classpath of the project") {
            private int totalWork;
            private int doneWork;
            private double percentage;

            @Override
            public int totalWork() {
                return totalWork;
            }

            @Override
            public int doneWork() {
                return doneWork;
            }

            @Override
            public double percentage() {
                return percentage;
            }

            @Override
            public synchronized boolean start() throws InterruptedException {
                CliBuildToolBuildContext buildCtx = (CliBuildToolBuildContext) buildTask.getBuildCtx();
                Map<String, List<String>> classpathMap = new HashMap<>();
                buildCtx.setClasspathMap(classpathMap);

                MavenTask mavenTask = calculateClasspaths(new File(projectFolder, "pom.xml"), m2SettingXml, buildCtx.getLeafModuleRelativePaths());
                mavenTask.subscribe(new DefaultSubscriber<Task>() {
                    @SuppressWarnings("unchecked")
                    @Override
                    public void onComplete() {
                        Map<String, List<String>> classpaths = (Map<String, List<String>>) mavenTask.getResult();
                        classpathMap.putAll(classpaths);
                    }

                    @Override
                    public void onNext(Task task) {
                        explainTask = task.explainTask();
                        totalWork = task.totalWork();
                        doneWork = task.doneWork();
                        percentage = task.percentage();
                        Optional.ofNullable(subscriber).ifPresent(it -> it.onNext(task));
                    }

                    @Override
                    public void onError(Throwable throwable) {
                        Optional.ofNullable(subscriber).ifPresent(it -> it.onError(throwable));
                    }
                });
                ExecutorService executorService = Executors.newSingleThreadExecutor();
                Future<Boolean> classpathMapFuture = executorService.submit(mavenTask::start);
                while (!classpathMapFuture.isDone()) {
                    if (isCancelled()) {
                        classpathMapFuture.cancel(true);
                        mavenTask.cancel();
                    } else {
                        wait(100);
                    }
                }
                try {
                    if (!isCancelled()) {
                        boolean success = classpathMapFuture.get();
                        if (success) {
                            mavenTask.done();
                            return true;
                        }
                    }
                    return false;
                } catch (Throwable ex1) {
                    mavenTask.stopExceptionally(ex1);
                    return false;
                }
            }
        });


        buildTask.addSubTask(new DiscreteTask("Calculate free modules") {
            @Override
            public boolean start() {
                inProgress = true;
                CliBuildToolBuildContext buildCtx = (CliBuildToolBuildContext) buildTask.getBuildCtx();
                Map<String, List<String>> depedendOnOtherModuleMap = new HashMap<>();
                Map<String, List<String>> depedendOnModuleMap = new HashMap<>();
                List<String> freeChangedModules = new ArrayList<>();
                List<String> freeDeletedModules = new ArrayList<>();
                List<String> leafModuleRelativePaths = buildCtx.getLeafModuleRelativePaths();
                Map<String, List<DiffEntry>> moduleToDeletedEntriesMap = buildCtx.getModuleToDeletedEntriesMap();
                Map<String, List<DiffEntry>> moduleToChangedEntriesMap = buildCtx.getModuleToChangedEntriesMap();

                buildCtx.setDepedendOnModuleMap(depedendOnModuleMap);
                buildCtx.setDepedendOnOtherModuleMap(depedendOnOtherModuleMap);
                buildCtx.setFreeChangedModules(freeChangedModules);
                buildCtx.setFreeDeletedModules(freeDeletedModules);
                // Calculate dependent module map: module relative path -> depend on module relative paths (not include self)
                for (String leafModuleRelativePath : leafModuleRelativePaths) {
                    List<String> moduleClasspaths = buildCtx.getClasspathMap().get(leafModuleRelativePath).stream()
                            .map(Paths::get)
                            .filter(classpath -> classpath.startsWith(projectFolder.getAbsolutePath()))
                            .map(path -> relativize(path.toFile(), projectFolder))
                            .map(Path::toString)
                            .map(classpath -> getModuleFromRelativePath(projectFolder.getAbsolutePath(), classpath, leafModuleRelativePaths))
                            .collect(Collectors.toList());
                    depedendOnOtherModuleMap.put(leafModuleRelativePath, moduleClasspaths);
                }
                // Calculate depedent module map: module relative path -> depend on module relative paths (including self)
                depedendOnModuleMap.putAll(depedendOnOtherModuleMap.entrySet().stream()
                        .collect(Collectors.toMap(Entry::getKey, it -> Stream.of(Collections.singletonList(it.getKey()), it.getValue())
                                .flatMap(List::stream).collect(Collectors.toList()))));

                // Calculate free module relative paths
                Set<String> aggregatedDependOnModuleRelativePaths = depedendOnOtherModuleMap.entrySet().stream()
                        .flatMap(it -> it.getValue().stream()).collect(Collectors.toSet());
                List<String> freeModuleRelativePaths = leafModuleRelativePaths.stream().filter(it -> !aggregatedDependOnModuleRelativePaths.contains(it))
                        .collect(Collectors.toList());

                // Filter out only free modules that have submodules that have deleted files
                freeDeletedModules.addAll(freeModuleRelativePaths.stream().filter(moduleRelativePath -> {
                    List<String> dependOnModuleRelativePaths = depedendOnModuleMap.get(moduleRelativePath);
                    return dependOnModuleRelativePaths.stream().anyMatch(moduleToDeletedEntriesMap::containsKey);
                }).collect(Collectors.toList()));

                // Filter out only free modules that have submodules that changed
                freeChangedModules.addAll(freeModuleRelativePaths.stream().filter(module -> {
                    List<String> dependOnModules = depedendOnModuleMap.get(module);
                    return dependOnModules.stream().anyMatch(moduleToChangedEntriesMap::containsKey);
                }).collect(Collectors.toList()));
                return true;
            }
        });

        buildTask.addSubTask(new DiscreteTask("Add maven build target folder to classpath of each free changed module") {

            @Override
            public boolean start() {
                inProgress = true;
                CliBuildToolBuildContext buildCtx = (CliBuildToolBuildContext) buildTask.getBuildCtx();

                buildCtx.getFreeChangedModules().forEach(freeChangedModuleRelativePath -> {
                    File mavenOutputTargetDir = Paths.get(projectFolder.getAbsolutePath(), freeChangedModuleRelativePath, "target", "classes").toFile();
                    List<String> moduleClasspaths = buildCtx.getClasspathMap().get(freeChangedModuleRelativePath);
                    List<String> addedModuleClasspaths = Stream.of(Stream.of(mavenOutputTargetDir), moduleClasspaths.stream().map(File::new))
                            .flatMap(st -> st)
                            .filter(File::exists)
                            .map(File::getAbsolutePath)
                            .collect(Collectors.toList());
                    buildCtx.getClasspathMap().put(freeChangedModuleRelativePath, addedModuleClasspaths);
                });
                return true;
            }
        });

        buildTask.addSubTask(new DiscreteTask("Get artifact info for each free module") {
            @Override
            public boolean start() {
                inProgress = true;
                CliBuildToolBuildContext buildCtx = (CliBuildToolBuildContext) buildTask.getBuildCtx();
                buildCtx.setArtifactInfoModuleMap(getArtifactInfo.apply(buildCtx.getFreeChangedModules()));
                return true;
            }
        });

        buildTask.addSubTask(new DiscreteTask("Building artifact") {
            @Override
            public boolean start() throws Exception {
                inProgress = true;
                CliBuildToolBuildContext buildCtx = (CliBuildToolBuildContext) buildTask.getBuildCtx();

                // NOTE: Write deleted entry note.txt
                if (buildCtx.getModuleToDeletedEntriesMap().size() > 0) {
                    noteDeletedFiles(buildCtx.getFreeDeletedModules(), buildCtx.getDepedendOnModuleMap(), buildCtx.getModuleToDeletedEntriesMap());
                }

                if (shouldBuildPatch) {
                    buildPatch(buildCtx.getFreeChangedModules(), buildCtx.getDepedendOnModuleMap(), buildCtx.getModuleToChangedEntriesMap(), buildCtx.getClasspathMap(),
                            buildCtx.getArtifactInfoModuleMap());
                }

                if (shouldBuildConfigJar) {
                    buildConfig(buildCtx.getFreeChangedModules(), buildCtx.getDepedendOnModuleMap(), buildCtx.getModuleToChangedEntriesMap(), buildCtx.getArtifactInfoModuleMap(),
                            configPrefixes);
                }

                if (shouldBuildReleasePackage) {
                    buildReleasePackage(buildCtx.getFreeChangedModules(), buildCtx.getDepedendOnModuleMap(), buildCtx.getModuleToChangedEntriesMap(),
                            buildCtx.getArtifactInfoModuleMap(),
                            databaseChangePrefixes);
                }
                return true;
            }
        });
        return buildTask;
    }

    private String getModuleFromRelativePath(String rootPath, String checkedPath, List<String> leafModuleRelativePaths) {
        String normalizedCheckedPath = Paths.get(rootPath, checkedPath).normalize().toAbsolutePath().toString();
        for (String leafModuleRelativePath : leafModuleRelativePaths) {
            String normalizedLeafModuleRelativePath = Paths.get(rootPath, leafModuleRelativePath).normalize().toAbsolutePath().toString();
            if (normalizedCheckedPath.startsWith(normalizedLeafModuleRelativePath)) {
                return leafModuleRelativePath;
            }
        }
        return null;
    }

    private void noteDeletedFiles(List<String> freeModuleContainDeletedFiles,
                                  Map<String, List<String>> dependOnModuleMap, Map<String, List<DiffEntry>> moduleToDeletedEntriesMap)
            throws IOException {
        for (String module : freeModuleContainDeletedFiles) {
            File deletedNoteFile = getDeletedNoteFile(module);
            if (!deletedNoteFile.exists()) {
                deletedNoteFile.getParentFile().mkdirs();
                deletedNoteFile.createNewFile();
            }
            try (
                    Writer writer = new FileWriter(deletedNoteFile);
                    BufferedWriter bw = new BufferedWriter(writer);) {
                List<String> dependOnModules = dependOnModuleMap.get(module);
                for (String dependOnModule : dependOnModules) {
                    List<String> deletedEntries = moduleToDeletedEntriesMap.getOrDefault(dependOnModule, new ArrayList<>()).stream().map(DiffEntry::getOldPath).collect(Collectors.toList());
                    for (String entry : deletedEntries) {
                        bw.write(entry);
                        bw.newLine();
                    }
                }
            }
        }
    }

    private Model buildMavenProjectModel(File pomFile) throws ModelBuildingException, IOException, XmlPullParserException {
        MavenXpp3Reader pomReader = new MavenXpp3Reader();
        Model simpleModel = pomReader.read(new FileInputStream(pomFile));
        if (!Optional.ofNullable(simpleModel.getModules()).orElseGet(ArrayList::new).isEmpty()) {
            ModelBuilder modelBuilder = new DefaultModelBuilderFactory().newInstance();
            ModelBuildingRequest buildingRequest = new DefaultModelBuildingRequest();
            buildingRequest.setPomFile(pomFile);
            ModelBuildingResult buildingResult = modelBuilder.build(buildingRequest);
            return buildingResult.getEffectiveModel();
        } else {
            return simpleModel;
        }
    }

    private static Path relativize(File child, File parent) {
        return parent.toPath().toAbsolutePath().normalize().relativize(child.toPath().toAbsolutePath().normalize());
    }

    private RevCommit checkout(String target) throws RevisionSyntaxException, IOException, GitAPIException {
        return gitUtil.checkoutAndStash(target);
    }

    // package -> changed class files
    private Map<String, List<File>> filterCompiledClassesInChangedModule(URLClassLoader classLoader,
                                                                         Map<String, List<DiffEntry>> changedModuleToEntriesMap, String module) throws IOException, ClassNotFoundException {
        Map<String, List<File>> packageToChangedClassFilesMap = new HashMap<>();
        Map<String, List<Class<?>>> packageToChangedClassesMap = new HashMap<>();
        if (!changedModuleToEntriesMap.containsKey(module)) {
            return packageToChangedClassFilesMap;
        }
        List<File> changedJavaFilesInModule = changedModuleToEntriesMap.get(module).stream()
                .map(DiffEntry::getNewPath)
                .map(it -> new File(projectFolder, it))
                .filter(CliBuildTool::isJavaFile)
                .collect(Collectors.toList());
        for (File javaFile : changedJavaFilesInModule) {
            String packageName = getJavaPackage(javaFile);
            String className = getClassName(javaFile);
            List<Class<?>> changedJavaClasses = new ArrayList<>();
            Class<?> clazz = classLoader.loadClass(packageName + "." + className);

            Deque<Class<?>> stack = new ArrayDeque<>();
            stack.add(clazz);

            while (!stack.isEmpty()) {
                Class<?> c = stack.pop();
                changedJavaClasses.add(c);
                stack.addAll(Arrays.asList(c.getDeclaredClasses()));
            }

            List<File> classFiles = packageToChangedClassFilesMap.computeIfAbsent(packageName, k -> new ArrayList<>());
            packageToChangedClassesMap.computeIfAbsent(packageName, k -> new ArrayList<>()).addAll(changedJavaClasses);
            for (Class<?> c : changedJavaClasses) {
                File classpath = new File(c.getProtectionDomain().getCodeSource().getLocation().getFile());
                File classFile = new File(classpath, javaNameToPath(c.getName()) + ".class");
                if (classFile.isFile()) {
                    classFiles.add(classFile);
                }
            }
        }

        // NOTE: Quite a hacky approach to collect all anonymous classes, required maven
        // clean for reproducible result
        Map<String, List<File>> packageToChangedAnonymousClassMap = packageToChangedClassesMap.entrySet().stream()
                .collect(Collectors.toMap(Entry::getKey, it -> {
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
                                while (enclosingClass.getEnclosingClass() != null) {
                                    enclosingClass = enclosingClass.getEnclosingClass();
                                }
                                return changedJavaClassSet.contains(enclosingClass);
                            })
                            .map(c -> {
                                File classpath = new File(
                                        c.getProtectionDomain().getCodeSource().getLocation().getFile());
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

    private void buildConfig(List<String> freeModules, Map<String, List<String>> dependentOnModuleMap,
                             Map<String, List<DiffEntry>> changedModuleToEntriesMap, Map<String, ArtifactInfo> artifactInfoModuleMap,
                             List<String> configPrefixes)
            throws IOException {
        for (String module : freeModules) {
            File buildConfigFolder = getBuildConfigFolder(module);
            buildConfigFolder.mkdirs();

            ArtifactInfo artifactInfo = artifactInfoModuleMap.get(module);
            List<String> dependOnModules = dependentOnModuleMap.get(module);

            Map<String, List<File>> changedConfigFilesModuleMap = dependOnModules.stream()
                    .collect(Collectors.toMap(dependOnModule -> dependOnModule, dependOnModule -> {
                        File moduleFolder = new File(projectFolder, dependOnModule);
                        return changedModuleToEntriesMap.getOrDefault(dependOnModule, new ArrayList<>()).stream()
                                .map(DiffEntry::getNewPath)
                                .map(it -> new File(projectFolder, it))
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
                List<File> changedConfigFiles = changedConfigFilesModuleMap.getOrDefault(dependOnModule, new ArrayList<>());
                for (File changedFile : changedConfigFiles) {
                    Path relativePathToModule = relativize(changedFile, moduleFolder);
                    File destinationFile = new File(buildConfigFolder,
                            relativePathToModule.subpath(1, relativePathToModule.getNameCount()).toString());
                    FileUtils.copyFile(changedFile, destinationFile);
                }
            }

            if (buildConfigFolder.listFiles() != null && buildConfigFolder.listFiles().length > 0) {
                if (artifactInfoModuleMap.get(module).isConfigCompress()) {
                    // Create a jar
                    createJarFile(getTargetConfigJarFile(module, artifactInfo), buildConfigFolder, null);
                } else {
                    // NOTE: I shouldn't use getTargetConfigJarFile, but I'm too lazy anyway
                    FileUtils.copyDirectory(buildConfigFolder,
                            getTargetConfigJarFile(module, artifactInfo).getParentFile());
                }
            }
        }
    }

    private void buildReleasePackage(List<String> freeModules, Map<String, List<String>> dependentOnModuleMap,
                                     Map<String, List<DiffEntry>> changedModuleToEntriesMap, Map<String, ArtifactInfo> artifactInfoModuleMap,
                                     List<String> databaseChangePrefixes)
            throws IOException {

        for (String module : freeModules) {
            File buildReleasePackageFolder = getBuildReleaseFolder(module);
            buildReleasePackageFolder.mkdirs();

            ArtifactInfo artifactInfo = artifactInfoModuleMap.get(module);
            File targetPatchJarFile = getTargetPatchJarFile(module, artifactInfo);

            File changedReleaseConfigFolder = new File(buildReleasePackageFolder, "config");
            File changedDatabaseFolder = new File(buildReleasePackageFolder, "DatabaseChange");
            File libFolder = new File(buildReleasePackageFolder, "Lib");

            File targetConfigJarFolder = getTargetConfigJarFile(module, artifactInfo).getParentFile();

            // Copy config changes
            if (targetConfigJarFolder.listFiles() != null && targetConfigJarFolder.listFiles().length > 0) {
                FileUtils.copyDirectory(targetConfigJarFolder, changedReleaseConfigFolder);
            }

            // Copy patch to lib folder
            if (targetPatchJarFile.isFile()) {
                FileUtils.copyFileToDirectory(targetPatchJarFile, libFolder);
            }

            // Copy database changes
            List<String> dependOnModules = dependentOnModuleMap.get(module);
            Map<String, List<File>> changedDatabaseModuleMap = dependOnModules.stream()
                    .collect(Collectors.toMap(dependOnModule -> dependOnModule, dependOnModule -> {
                        File moduleFolder = new File(projectFolder, dependOnModule);
                        return changedModuleToEntriesMap.getOrDefault(dependOnModule, new ArrayList<>()).stream()
                                .map(DiffEntry::getNewPath)
                                .map(it -> new File(projectFolder, it))
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

            if (targetConfigJarFolder.exists()) {
                FileUtils.deleteDirectory(targetConfigJarFolder);
            }
            if (targetPatchJarFile.getParentFile().exists()) {
                FileUtils.deleteDirectory(targetPatchJarFile.getParentFile());
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

    private void buildPatch(List<String> freeModuleRelativePaths, Map<String, List<String>> dependentOnModuleMap,
                            Map<String, List<DiffEntry>> changedModuleToEntriesMap, Map<String, List<String>> classpathMap,
                            Map<String, ArtifactInfo> artifactInfoModuleMap)
            throws ClassNotFoundException, IOException, MavenInvocationException, ModelBuildingException {
        for (String moduleRelativePath : freeModuleRelativePaths) {
            File buildPatchFolder = getBuildPatchFolder(moduleRelativePath);
            buildPatchFolder.mkdirs();

            ArtifactInfo artifactInfo = artifactInfoModuleMap.get(moduleRelativePath);
            List<String> classpaths = new ArrayList<>(classpathMap.get(moduleRelativePath));
            List<URL> urls = new ArrayList<>();
            for (String classpath : classpaths) {
                urls.add(new File(classpath).toURI().toURL());
            }

            List<String> dependOnModules = dependentOnModuleMap.get(moduleRelativePath);

            try (URLClassLoader loader = new URLClassLoader(urls.toArray(new URL[]{}))) {
                for (String dependOnModule : dependOnModules) {
                    // Copy compiled classes to classes folder
                    for (Map.Entry<String, List<File>> entry : filterCompiledClassesInChangedModule(loader,
                            changedModuleToEntriesMap, dependOnModule).entrySet()) {
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
                Manifest manifest = new Manifest();
                manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
                createJarFile(getTargetPatchJarFile(moduleRelativePath, artifactInfo), buildPatchFolder, manifest);
            }
        }
    }

    private File getDeletedNoteFile(String module) {
        return Paths.get(targetFolder.getAbsolutePath(), module, "notes", "deleted_changes").toFile();
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
    private MavenTask calculateClasspaths(File pomFile, File m2SettingsXml, List<String> leafModuleRelativePaths) {
        String classpathFileName = "classpath";
        Invoker invoker = new DefaultInvoker();
        InvocationRequest request = new DefaultInvocationRequest();
        request.setPomFile(pomFile);
        List<String> goals = new ArrayList<>();
        goals.addAll(Arrays.asList("clean", "compile", "dependency:tree", "dependency:build-classpath"));
        request.setGoals(goals);
        request.setBatchMode(true);
        request.setOffline(false);
        request.setUserSettingsFile(m2SettingsXml);
        request.setUpdateSnapshots(updateSnapshot);
        request.setJavaHome(javaHome);
        request.setMavenOpts("-Dmdep.outputFile=" + classpathFileName);
        request.setShowVersion(true);
        request.setShowErrors(true);
        MavenTask mavenTask = new MavenTask() {
            @Override
            public boolean start() throws Exception {
                try {
                    inProgress = true;
                    InvocationResult result = invoker.execute(request);
                    if (result.getExitCode() != 0) {
                        throw new UnsupportedOperationException(
                                "Can't calculate classpaths, please check your m2 settings or pom file and make sure maven can compile the project successfully");
                    }
                    Map<String, List<String>> ret = new HashMap<>();
                    for (String module : leafModuleRelativePaths) {
                        File classpathFile = Paths.get(projectFolder.getAbsolutePath(), module, classpathFileName).toFile();
                        try (
                                Reader reader = new FileReader(classpathFile);
                                BufferedReader br = new BufferedReader(reader)) {
                            // Handler classpath seperator for windows also
                            ret.put(module, Arrays.asList(br.readLine().split("[:;]")));
                        }
                    }
                    this.result = ret;
                    return true;
                } finally {
                    Deque<File> classpathFileStack = leafModuleRelativePaths.stream().map(moduleRelativePath -> Paths.get(projectFolder.getAbsolutePath(), moduleRelativePath, classpathFileName)).map(Path::toFile).collect(Collectors.toCollection(ArrayDeque::new));
                    Set<File> classpathFiles = new HashSet<>();
                    // Traverse up from leaf module classpath files and delete all parent classpath file up to project folder
                    while (classpathFileStack.size() > 0) {
                        File classpathFile = classpathFileStack.pop();
                        File moduleFolder = classpathFile.getParentFile();
                        classpathFiles.add(classpathFile);
                        if (moduleFolder.equals(projectFolder)) {
                            break;
                        }
                        File parentModuleFolder = moduleFolder.getParentFile();
                        classpathFileStack.add(new File(parentModuleFolder, classpathFileName));
                    }
                    classpathFiles.forEach(File::delete);
                }
            }
        };

        request.setOutputHandler(mavenTask.getOutputHandler());
        return mavenTask;
    }

    public static void createJarFile(File jarFile, File sourceFolder, Manifest manifest) throws IOException {
        if (!jarFile.exists()) {
            jarFile.getParentFile().mkdirs();
            jarFile.createNewFile();
        }
        try (
                OutputStream os = new FileOutputStream(jarFile);
                JarOutputStream jos = manifest == null ? new JarOutputStream(os) : new JarOutputStream(os, manifest)) {
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

    private void restoreGitWorkingDirectory(String previousHEAD) throws GitAPIException, RevisionSyntaxException, IOException {
        // Recheckout the previous head
        if (previousHEAD != null) {
            checkout(previousHEAD);
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

    @Override
    public void close() throws GitAPIException, RevisionSyntaxException, IOException {
        gitUtil.close();
    }

    @Builder
    @Getter
    @AllArgsConstructor
    public static class ArtifactInfo {
        private String configName;
        private String patchName;
        private String releasePackageName;
        private boolean configCompress; // NOTE: Only used by certain type of build
    }

    public static class DefaultCliGetArtifactInfo {
        public static Function<List<String>, Map<String, ArtifactInfo>> getArtifactInfo(boolean interactive) {
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
                                    .patchName(
                                            StringUtils.isBlank(patchJarFileName) ? defaultPatchName : patchJarFileName)
                                    .releasePackageName(
                                            StringUtils.isBlank(releasePackageZipFileName) ? "ReleasePackage.zip"
                                                    : releasePackageZipFileName)
                                    .configCompress(!isServer)
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
                                .configCompress(!isServer)
                                .build();
                    }));
                }
            };
        }
    }
}
