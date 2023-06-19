package com.ttl.internal.vn.tool.builder.component;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.Map.Entry;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.commons.lang3.tuple.Triple;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.errors.NotSupportedException;
import org.eclipse.jgit.errors.TransportException;
import org.eclipse.jgit.transport.CredentialsProvider;
import org.eclipse.jgit.transport.RemoteSession;
import org.eclipse.jgit.transport.Transport;
import org.eclipse.jgit.transport.TransportHttp;
import org.eclipse.jgit.transport.URIish;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;

import com.ttl.internal.vn.tool.builder.git.GitCommit;
import com.ttl.internal.vn.tool.builder.util.GitUtil;
import com.ttl.internal.vn.tool.builder.util.GitUtil.CredentialEntry;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import lombok.Synchronized;

@Getter(onMethod_={@Synchronized}) 
@Setter(onMethod_={@Synchronized}) 
public class Session implements AutoCloseable {
    private static volatile Session INSTANCE = new Session();
    private final Object $lock; 
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
    
    public Session() {
        $lock = new ReentrantLock();
        File homeFolder = new File(System.getProperty("user.home"));
        this.dotGitCredentials = new File(homeFolder, ".git-credentials");
        this.xdgGitCredentials = Paths.get(homeFolder.getAbsolutePath(), ".git", "credentails").toFile();
        this.inMemoryCredentialEntries = new HashSet<>();
    }

    public void setGitUtil(GitUtil gitUtil) {
        if (this.gitUtil != null) {
            throw new UnsupportedOperationException("Create new session for new git repo, this session is already binded to a repo");
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
            storeCredentialEntry(normalizedCredentialEntry.getUsername(), normalizedCredentialEntry.getPassword(), normalizedCredentialEntry.getUrl());
        }
    }

    public Set<CredentialEntry> loadCredentials(boolean fileOnly) throws IOException, URISyntaxException {
        return Stream.of(fileOnly ? Set.<CredentialEntry>of() : inMemoryCredentialEntries, loadCredential(dotGitCredentials), loadCredential(xdgGitCredentials))
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

    public void storeCredentialEntry(String username, String password, String url) throws IOException, URISyntaxException {
        Set<CredentialEntry> credentials = loadCredentials(true);
        CredentialEntry newEntry = CredentialEntry.builder()
            .username(username)
            .password(password)
            .url(url)
            .build()
            .normalize();
        if (!credentials.contains(newEntry)) {
            String urlEncodedWithUsernameAndPassword = GitUtil.encodeCredentialEntry(newEntry.getUsername(), newEntry.getPassword(), newEntry.getUrl());
            if (!dotGitCredentials.exists()) {
                dotGitCredentials.createNewFile();
            }
            try (BufferedWriter writer = new BufferedWriter(new FileWriter(dotGitCredentials, true))) {
                writer.append(urlEncodedWithUsernameAndPassword);
            }
        }
    }

    public static Session getInstance() {
        if (INSTANCE == null) {
            synchronized(Session.class) {
                if (INSTANCE == null) {
                    INSTANCE = new Session();
                }
            }
        }
        return INSTANCE;
    }

    @Override
    public void close() throws Exception {
        gitUtil.close();
    }
}
