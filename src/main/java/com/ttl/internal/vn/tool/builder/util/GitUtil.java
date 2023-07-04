package com.ttl.internal.vn.tool.builder.util;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Future;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.commons.lang3.StringUtils;
import org.eclipse.jgit.api.CloneCommand;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.ListBranchCommand.ListMode;
import org.eclipse.jgit.api.TransportConfigCallback;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.diff.DiffFormatter;
import org.eclipse.jgit.errors.IncorrectObjectTypeException;
import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.errors.NotSupportedException;
import org.eclipse.jgit.errors.RevisionSyntaxException;
import org.eclipse.jgit.errors.TransportException;
import org.eclipse.jgit.lib.AnyObjectId;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.ProgressMonitor;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.TextProgressMonitor;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.transport.CredentialsProvider;
import org.eclipse.jgit.transport.Transport;
import org.eclipse.jgit.transport.TransportHttp;
import org.eclipse.jgit.transport.URIish;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;
import org.eclipse.jgit.treewalk.AbstractTreeIterator;
import org.eclipse.jgit.treewalk.CanonicalTreeParser;
import org.eclipse.jgit.treewalk.FileTreeIterator;
import org.eclipse.jgit.util.io.DisabledOutputStream;

import com.ttl.internal.vn.tool.builder.git.GitCommit;
import com.ttl.internal.vn.tool.builder.git.GitRef;
import com.ttl.internal.vn.tool.builder.git.GitWalk;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

public class GitUtil implements AutoCloseable {
    private final Git git;
    private static final String ENCODING_FORMAT = "UTF-8";

    public GitUtil(Git git) {
        this.git = git;
    }

    public Git getGit() {
        return git;
    }

    public Repository getRepository() {
        return git.getRepository();
    }

    public AnyObjectId resolve(String rev) throws RevisionSyntaxException, IOException {
        return git.getRepository().resolve(rev);
    }

    public RevCommit stashChange() throws GitAPIException {
        return git.stashCreate().setIncludeUntracked(true).call();
    }

    public RevWalk getRevWalk() {
        return new RevWalk(git.getRepository());
    }

    public void checkout(RevCommit commit) throws GitAPIException {
        git.checkout().setName(commit.getName()).call();
    }

    public void checkout(String refName) throws GitAPIException {
        git.checkout().setName(refName).call();
    }

    public void checkout(GitCommit gitCommit) throws GitAPIException {
        git.checkout().setName(gitCommit.getHash()).call();
    }

    public RevCommit checkoutAndStash(String target) throws RevisionSyntaxException, IOException, GitAPIException {
        try (RevWalk revWalk = getRevWalk()) {
            RevCommit headRevCommit = revWalk.parseCommit(resolve("HEAD"));
            RevCommit targetRevCommit = revWalk.parseCommit(resolve(target));
            RevCommit newStashedCommit = stashChange();
            if (!headRevCommit.equals(targetRevCommit)) {
                // Checkout the target commit
                checkout(target); // Keep HEAD name ref
            }
            return newStashedCommit;
        }
    }

    public void checkoutAndStashApply(String target) throws GitAPIException, RevisionSyntaxException, MissingObjectException, IncorrectObjectTypeException, IOException {
        RevCommit newStashedCommit = null;
        try (RevWalk revWalk = getRevWalk()) {
            RevCommit headRevCommit = revWalk.parseCommit(resolve("HEAD"));
            RevCommit targetRevCommit = revWalk.parseCommit(resolve(target));
            newStashedCommit = stashChange();
            if (!headRevCommit.equals(targetRevCommit)) {
                // Checkout the target commit
                checkout(target); // Keep HEAD name ref
            }
        } finally {
            if (newStashedCommit != null) {
                stashApply(newStashedCommit);
            }
        }
    }

    public void stashApply(RevCommit stashedCommit) throws GitAPIException {
        if (stashedCommit != null) {
            git.stashApply().setStashRef(stashedCommit.getName()).setRestoreUntracked(true)
                    .setRestoreIndex(true)
                    .ignoreRepositoryState(false).call();
        }
    }

    public GitRef findRef(String revString) throws IOException {
        return new GitRef(git.getRepository().findRef(revString));
    }

    public GitRef getHeadRef() throws IOException {
        return findRef("HEAD");
    }

    public void fetch(String username, String password, ProgressMonitor progressMonitor) throws GitAPIException {
        git.fetch()
            .setCredentialsProvider(new UsernamePasswordCredentialsProvider(username, password))
            .setProgressMonitor(progressMonitor)
            .call();
    }

    public void fetch(String username, String password) throws GitAPIException {
        fetch(username, password, new TextProgressMonitor(new PrintWriter(System.out)));
    }

    public List<DiffEntry> getDiff(String baseRef, String targetRef) throws IOException {
        try (
                var diffFormatter = new DiffFormatter(DisabledOutputStream.INSTANCE);
                var revWalk = new RevWalk(git.getRepository());) {
            diffFormatter.setRepository(git.getRepository());
            diffFormatter.setDetectRenames(true);
            var baseCommit = revWalk.parseCommit(resolve(baseRef));
            var targetCommit = revWalk.parseCommit(resolve(targetRef));
            return diffFormatter.scan(baseCommit, targetCommit);
        }
    }
    
    public List<DiffEntry> getDiffWd(String baseRef) throws IOException {
        try (
                var diffFormatter = new DiffFormatter(DisabledOutputStream.INSTANCE);
                var revWalk = new RevWalk(git.getRepository());
                ObjectReader reader = git.getRepository().newObjectReader()) {
            AbstractTreeIterator newTree = new FileTreeIterator(git.getRepository());
            diffFormatter.setRepository(git.getRepository());
            diffFormatter.setDetectRenames(true);
            var baseCommit = revWalk.parseCommit(resolve(baseRef));
            CanonicalTreeParser oldTree = new CanonicalTreeParser();
            oldTree.reset(reader, baseCommit.getTree());
            return diffFormatter.scan(oldTree, newTree);
        }
    }

    public GitCommit fromHash(String hash) throws IOException {
        try (var revWalk = new RevWalk(git.getRepository())) {
            var objectId = ObjectId.fromString(hash);
            var revCommit = revWalk.parseCommit(objectId);
            return comprehendCommit(revCommit);
        }
    }

    public GitCommit comprehendCommit(RevCommit commit) throws IOException {
        PersonIdent author = commit.getAuthorIdent();
        PersonIdent committer = commit.getCommitterIdent();
        List<String> parentHashs = Stream.of(commit.getParents()).map(it -> it.getId().getName())
                .collect(Collectors.toList());
        List<GitRef> refs = git.getRepository().getAllRefsByPeeledObjectId().getOrDefault(commit.getId(), Set.<Ref>of())
                .stream().map(GitRef::new).collect(Collectors.toList());
        
        return GitCommit.builder()
                .hash(commit.getId().getName())
                .message(commit.getFullMessage())
                .author(author)
                .committer(committer)
                .parentHashs(parentHashs)
                .refs(refs)
                .authorTime(author.getWhen())
                .commitTime(committer.getWhen())
                .build();
    }

    public List<String> getBranches(boolean remoteOnly) throws GitAPIException {
        return getBranchRefs(remoteOnly)
                .stream()
                .map(Ref::getName)
                .map(it -> it.split("/"))
                .map(it -> it[it.length - 1])
                .collect(Collectors.toList());
    }

    public List<Ref> getBranchRefs(boolean remoteOnly) throws GitAPIException {
        return git.branchList().setListMode(remoteOnly ? ListMode.REMOTE : ListMode.ALL).call();
    }

    public static Git cloneGitRepo(String uri, File targetDir, String username, String password)
            throws GitAPIException {
        return cloneGitRepo(uri, targetDir, username, password, new TextProgressMonitor(new PrintWriter(System.out)));
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

    public static String encodeCredentialEntry(String username, String password, String gitUrl) throws URISyntaxException, UnsupportedEncodingException {
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

    public static Git cloneGitRepo(String uri, File targetDir, String username, String password,
                                            ProgressMonitor progressMonitor)
            throws GitAPIException {
        CloneCommand command = Git.cloneRepository().setURI(uri)
                .setProgressMonitor(progressMonitor)
                .setDirectory(targetDir);
        if (StringUtils.isNotBlank(username) && StringUtils.isNotBlank(password)) {
            command = command.setCredentialsProvider(new UsernamePasswordCredentialsProvider(username, password));
        }
        return command.call();
    }

    public static Git openLocalRepo(File gitFolder) throws IOException {
        return Git.open(gitFolder);
    }

    @Override
    public void close() throws IOException {
        git.close();
    }

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

    public void applyPatchFile(File patchFile) throws IOException, GitAPIException {
        try (InputStream is = new FileInputStream(patchFile)) {
            git.apply().setPatch(is).call();
        }
    }
}