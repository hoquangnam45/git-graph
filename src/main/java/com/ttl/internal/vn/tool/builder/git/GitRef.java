package com.ttl.internal.vn.tool.builder.git;

import java.util.Optional;

import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;

public class GitRef {
    private static final String REF_NAME_SEPERATOR = "/";
    private final Ref rawRef;
    private String[] refTokens;
    private String name;
    private String commitHash;

    public GitRef(Ref ref) {
        this.rawRef = ref;
        this.name = ref.getName();
        this.refTokens = name.split(REF_NAME_SEPERATOR);
        this.commitHash = Optional.of(ref).map(Ref::getObjectId).map(ObjectId::name).orElse(null);
    }

    public Ref getRawRef() {
        return rawRef;
    }

    // Example: refs/heads/master or refs/remotes/origin/master or refs/tags/0.0.1
    public String getName() {
        return name;
    }

    // Remove 'refs' from normal name
    // Example: origin/master or heads/master or ...;
    public String getShortName() {
        String[] tokensWithoutRef = new String[refTokens.length - 1];
        System.arraycopy(refTokens, 1, tokensWithoutRef, 0, refTokens.length - 1);
        return String.join(REF_NAME_SEPERATOR, tokensWithoutRef);
    }

    // Example: origin
    public String getRemoteName() {
        if (!isRemote()) {
            return null;
        }
        return refTokens[2];
    }

    public boolean isRemote() {
        return refTokens[1].equalsIgnoreCase("remotes");
    }

    public boolean isLocal() {
        return refTokens[1].equalsIgnoreCase("heads");
    }

    public boolean isTag() {
        return refTokens[1].equalsIgnoreCase("tags");
    }

    public boolean isHead() {
        return name.equals("HEAD");
    }

    public boolean isBranch() {
        return !isTag();
    }

    public String getCommitHash() {
        return commitHash;
    }

    public String getShortCommitHash() {
        return commitHash.substring(0, 7);
    }

    // Example: master
    public String getShortestName() {
        return refTokens[refTokens.length - 1];
    }

    @Override
    public String toString() {
        if (isHead()) {
            return name;
        }
        return getShortName();
    }
}
