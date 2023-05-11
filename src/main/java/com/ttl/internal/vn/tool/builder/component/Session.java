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

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import lombok.Synchronized;

@Getter(onMethod_={@Synchronized}) 
@Setter(onMethod_={@Synchronized}) 
public class Session implements AutoCloseable {
    @Getter
    @AllArgsConstructor
    @Builder
    public static class CredentialEntry {
        private String username;
        private String password;
        private String url;

        public CredentialEntry normalize() throws UnsupportedEncodingException, URISyntaxException {
            String encodedURIWithUsernameAndPassword = encodeCredentialEntry(username, password, url);
            return parseCredentialEntry(encodedURIWithUsernameAndPassword);
        }

        @Override
        public int hashCode() {
            final int prime = 31;
            int result = 1;
            result = prime * result + ((username == null) ? 0 : username.hashCode());
            result = prime * result + ((password == null) ? 0 : password.hashCode());
            result = prime * result + ((url == null) ? 0 : url.hashCode());
            return result;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj)
                return true;
            if (obj == null)
                return false;
            if (getClass() != obj.getClass())
                return false;
            CredentialEntry other = (CredentialEntry) obj;
            if (username == null) {
                if (other.username != null)
                    return false;
            } else if (!username.equals(other.username))
                return false;
            if (password == null) {
                if (other.password != null)
                    return false;
            } else if (!password.equals(other.password))
                return false;
            if (url == null) {
                if (other.url != null)
                    return false;
            } else if (!url.equals(other.url))
                return false;
            return true;
        }

    }

    public static class GitLoginException extends Exception {
        public GitLoginException(Throwable cause) {
            super(cause);
        }
    }


    private static volatile Session INSTANCE = new Session();
    private final Object $lock; 
    private static final String ENCODING_FORMAT = "UTF-8";
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
            storeCredentialEntry(normalizedCredentialEntry.username, normalizedCredentialEntry.password, normalizedCredentialEntry.url);
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
                        CredentialEntry credential = parseCredentialEntry(line);
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
            String urlEncodedWithUsernameAndPassword = encodeCredentialEntry(newEntry.getUsername(), newEntry.getPassword(), newEntry.getUrl());
            if (!dotGitCredentials.exists()) {
                dotGitCredentials.createNewFile();
            }
            try (BufferedWriter writer = new BufferedWriter(new FileWriter(dotGitCredentials, true))) {
                writer.append(urlEncodedWithUsernameAndPassword);
            }
        }
    }

    public static String getRepo(String gitUrl) throws URISyntaxException {
        URI uri = new URI(gitUrl);
        String path = uri.getPath();
        String lastPath = path.substring(path.lastIndexOf("/") + 1);
        if (!lastPath.toLowerCase().endsWith(".git")) {
            return "";
        }
        return lastPath.substring(0, lastPath.length() - ".git".length());
    }

    private static String encodeCredentialEntry(String username, String password, String gitUrl) throws URISyntaxException, UnsupportedEncodingException {
        URI uri = new URI(gitUrl);
        String encodedUsername = URLEncoder.encode(username, ENCODING_FORMAT);
        String encodedPassword = URLEncoder.encode(password, ENCODING_FORMAT);
        URI sanitizedUri = new URI(uri.getScheme(), encodedUsername + ":" + encodedPassword, uri.getHost(), uri.getPort(), uri.getPath(), uri.getQuery(), uri.getFragment());
        return sanitizedUri.toASCIIString();
    }

    public static void checkLogin(String username, String password, String url) throws URISyntaxException, NotSupportedException, GitLoginException {
        try {
            CredentialsProvider credentialsProvider = new UsernamePasswordCredentialsProvider(username, password);
            URIish uri = new URIish(url);

            // Create a TransportHttp object
            try (TransportHttp transport = (TransportHttp) Transport.open(uri)) {
                // Set the credentials provider
                transport.setCredentialsProvider(credentialsProvider);
                transport.openFetch();
            }
        } catch (TransportException e) {
            throw new GitLoginException(e);
        }
    }

    public static CredentialEntry parseCredentialEntry(String entry) throws UnsupportedEncodingException, URISyntaxException {
        URI uri = new URI(entry);
        String userInfo = uri.getUserInfo();

        if (userInfo != null) {
            int separatorIndex = userInfo.indexOf(':');
            if (separatorIndex != -1) {
                String encodedUsername = userInfo.substring(0, separatorIndex);
                String encodedPassword = userInfo.substring(separatorIndex + 1);

                String username = URLDecoder.decode(encodedUsername, ENCODING_FORMAT);
                String password = URLDecoder.decode(encodedPassword, ENCODING_FORMAT);
                String url = new URI(uri.getScheme(), null, uri.getHost(), uri.getPort(), uri.getPath(), uri.getQuery(), uri.getFragment()).toASCIIString();
                return CredentialEntry.builder()
                    .password(password)
                    .username(username)
                    .url(url)
                    .build();
            }
        }
        return null;
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
