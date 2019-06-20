package io.bdrc.edit.helpers;

public class ResourceGitInfo {

    public static String ADM_PREFIX = "http://purl.bdrc.io/ontology/admin/";
    public static String GIT_INFO = "GitInfo";
    public static String GIT_PATH = "gitPath";
    public static String GIT_REPO = "gitRepo";
    public static String GIT_REVISION = "gitRevision";

    public String gitInfo;
    public GitRepo gitRepo;
    public String gitPath;
    public String gitRevision;

    public ResourceGitInfo(String gitInfo, GitRepo gitRepo, String gitPath, String gitRevision) {
        super();
        this.gitInfo = gitInfo;
        this.gitRepo = gitRepo;
        this.gitPath = gitPath;
        this.gitRevision = gitRevision;
    }

    public String getGitInfo() {
        return gitInfo;
    }

    public void setGitInfo(String gitInfo) {
        this.gitInfo = gitInfo;
    }

    public GitRepo getGitRepo() {
        return gitRepo;
    }

    public void setGitRepo(GitRepo gitRepo) {
        this.gitRepo = gitRepo;
    }

    public String getGitPath() {
        return gitPath;
    }

    public void setGitPath(String gitPath) {
        this.gitPath = gitPath;
    }

    public String getGitRevision() {
        return gitRevision;
    }

    public void setGitRevision(String gitRevision) {
        this.gitRevision = gitRevision;
    }

}
