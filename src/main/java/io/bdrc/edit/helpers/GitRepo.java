package io.bdrc.edit.helpers;

public class GitRepo {

    public String fullResId;
    public String gitRepoName;
    public String gitUrl;
    public String repoResType;

    public GitRepo(String fullResId, String gitRepoName, String gitUrl) {
        super();
        this.fullResId = fullResId;
        this.gitRepoName = gitRepoName;
        this.gitUrl = gitUrl;
        this.repoResType = gitRepoName.substring(0, gitRepoName.length() - 1).toLowerCase();
    }

    public String getFullResId() {
        return fullResId;
    }

    public String getGitRepoName() {
        return gitRepoName;
    }

    public String getGitUrl() {
        return gitUrl;
    }

    public String getRepoResType() {
        return repoResType;
    }

    @Override
    public String toString() {
        return "GitRepo [fullResId=" + fullResId + ", gitRepoName=" + gitRepoName + ", gitUrl=" + gitUrl + ", repoResType=" + repoResType + "]";
    }

}
