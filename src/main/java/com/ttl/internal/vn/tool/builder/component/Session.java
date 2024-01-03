package com.ttl.internal.vn.tool.builder.component;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.ttl.internal.vn.tool.builder.cli.CliBuildTool;
import com.ttl.internal.vn.tool.builder.util.SwingGraphicUtil;
import org.apache.commons.lang3.StringUtils;

import com.ttl.internal.vn.tool.builder.git.GitCommit;
import com.ttl.internal.vn.tool.builder.util.GitUtil;
import com.ttl.internal.vn.tool.builder.util.GitUtil.CredentialEntry;

import lombok.Getter;
import lombok.Setter;
import org.eclipse.jgit.diff.DiffEntry;

@Getter
@Setter
public class Session implements AutoCloseable {
    public static final String USE_WORKING_DIRECTORY_CHANGED = "USE_WORKING_DIRECTORY_CHANGED";
    private static Session instance = new Session();
    private String gitUsername;
    private String gitPassword;
    private File clonedFolder;
    private File artifactFolder;
    private boolean useGitCredential;
    private GitUtil gitUtil;
    private final File dotGitCredentials;
    private final File xdgGitCredentials;
    private Set<CredentialEntry> inMemoryCredentialEntries;
    private GitCommit baseCommit;
    private GitCommit targetCommit;
    private Boolean useWorkingDirectory;
    private String entryFilter;

    private Map<String, List<Consumer<?>>> listeners = new HashMap<>();

    public Session() {
        File homeFolder = new File(System.getProperty("user.home"));
        this.dotGitCredentials = new File(homeFolder, ".git-credentials");
        this.xdgGitCredentials = Paths.get(homeFolder.getAbsolutePath(), ".git", "credentails").toFile();
        this.inMemoryCredentialEntries = new HashSet<>();
    }

    public boolean getUseWorkingDirectory() {
        return Optional.ofNullable(useWorkingDirectory).orElse(true);
    }

    public void setUseWorkingDirectory(boolean useWorkingDirectory) {
        this.useWorkingDirectory = useWorkingDirectory;
        fireEvent(USE_WORKING_DIRECTORY_CHANGED, useWorkingDirectory);
    }

    public void setGitUtil(GitUtil gitUtil) {
        if (this.gitUtil != null) {
            throw new UnsupportedOperationException(
                    "Create new session for new git repo, this session is already binded to a repo");
        }
        this.gitUtil = gitUtil;
    }

    public String getGitRepoURI(String remoteName) {
        if (gitUtil == null) {
            return null;
        }
        return gitUtil.getRepository().getConfig().getString("remote", remoteName, "url");
    }

    public File getGitWorkingDir() {
        if (gitUtil == null) {
            return null;
        }
        return gitUtil.getRepository().getDirectory().getParentFile();
    }

    public void setCredentialEntry(CredentialEntry credentialEntry) throws URISyntaxException, IOException {
        CredentialEntry normalizedCredentialEntry = credentialEntry.normalize();
        inMemoryCredentialEntries.add(normalizedCredentialEntry);
        if (useGitCredential) {
            storeCredentialEntry(normalizedCredentialEntry.getUsername(), normalizedCredentialEntry.getPassword(),
                    normalizedCredentialEntry.getUrl());
        }
    }

    public Set<CredentialEntry> loadCredentials(boolean fileOnly) throws IOException, URISyntaxException {
        return Stream
                .of(fileOnly ? new HashSet<CredentialEntry>() : inMemoryCredentialEntries, loadCredential(dotGitCredentials),
                        loadCredential(xdgGitCredentials))
                .flatMap(Set::stream)
                .collect(Collectors.toSet());
    }

    public List<CredentialEntry> scanCredential(String gitUrl) throws URISyntaxException, IOException {
        List<CredentialEntry> credentials = new ArrayList<>();
        for (CredentialEntry entry : loadCredentials(false)) {
            if (new URI(gitUrl).getHost().equals(new URI(entry.getUrl()).getHost())) {
                credentials.add(entry);
            }
        }
        return credentials;
    }

    private Set<CredentialEntry> loadCredential(File credentialFile) throws IOException, URISyntaxException {
        Set<CredentialEntry> credentials = new HashSet<>();
        if (credentialFile.exists() && credentialFile.isFile()) {
            try (BufferedReader reader = new BufferedReader(new FileReader(credentialFile))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (StringUtils.isNotBlank(line)) {
                        CredentialEntry credential = GitUtil.parseCredentialEntry(line);
                        if (credential != null) {
                            credentials.add(credential);
                        }
                    }
                }
            }
        }
        return credentials;
    }

    public CompletableFuture<List<DiffEntry>> getDiff() throws IOException {
        return SwingGraphicUtil.supply(() -> {
            try {
                return CliBuildTool.getDiff(
                        gitUtil,
                        baseCommit.getHash(),
                        Optional.ofNullable(targetCommit).map(GitCommit::getHash).orElse(null),
                        entryFilter,
                        useWorkingDirectory);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
    }

    public void setEntryFilter(String filter) {
        this.entryFilter = Optional.ofNullable(filter)
                .filter(StringUtils::isNotBlank)
                .orElse(null);
    }

    public void storeCredentialEntry(String username, String password, String url)
            throws IOException, URISyntaxException {
        Set<CredentialEntry> credentials = loadCredentials(true);
        CredentialEntry newEntry = CredentialEntry.builder()
                .username(username)
                .password(password)
                .url(url)
                .build()
                .normalize();
        if (!credentials.contains(newEntry)) {
            String urlEncodedWithUsernameAndPassword = GitUtil.encodeCredentialEntry(newEntry.getUsername(),
                    newEntry.getPassword(), newEntry.getUrl());
            if (!dotGitCredentials.exists()) {
                dotGitCredentials.createNewFile();
            }
            try (BufferedWriter writer = new BufferedWriter(new FileWriter(dotGitCredentials, true))) {
                writer.append(urlEncodedWithUsernameAndPassword);
            }
        }
    }

    public static Session getInstance() {
        return instance;
    }

    @Override
    public void close() throws Exception {
        gitUtil.close();
    }

    public <T> void addListener(String event, Consumer<T> listener) {
        listeners.computeIfAbsent(event, s -> new ArrayList<>()).add(listener);
    }

    @SuppressWarnings("unchecked")
    public <T> void fireEvent(String event, T eventContext) {
        listeners.getOrDefault(event, new ArrayList<>()).forEach(l -> ((Consumer<T>) l).accept(eventContext));
    }
}
