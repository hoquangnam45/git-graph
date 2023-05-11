package com.ttl.internal.vn.tool.builder.git;

import java.util.Date;
import java.util.List;

import org.eclipse.jgit.lib.PersonIdent;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class GitCommit {
    private String hash;
    private String message;
    private PersonIdent committer;
    private PersonIdent author;
    private Date commitTime;
    private Date authorTime;
    private List<String> parentHashs;
    private List<GitRef> refs;

    public String getShortHash() {
        return hash.substring(0, 7);
    }

    public boolean isMergeCommit() {
        return parentHashs.size() > 1;
    }
}
