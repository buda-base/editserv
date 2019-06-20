package io.bdrc.edit.helpers;

import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.NodeIterator;
import org.apache.jena.rdf.model.ResourceFactory;

import io.bdrc.edit.EditConfig;
import io.bdrc.edit.sparql.QueryProcessor;

public class ResourceGitInfo {

    public static String ADM_PREFIX = "http://purl.bdrc.io/ontology/admin/";
    public static String BDA_PREFIX = "http://purl.bdrc.io/admindata/";
    public static String GIT_PATH = "gitPath";
    public static String GIT_REPO = "gitRepo";
    public static String GIT_REVISION = "gitRevision";

    public GitRepo gitRepo;
    public String gitPath;
    public String gitRevision;
    public String resourceType;

    public ResourceGitInfo(String resourceType, String gitPath, String gitRevision) {
        super();
        this.resourceType = resourceType;
        this.gitRepo = GitRepositories.getRepo(resourceType);
        this.gitPath = gitPath;
        this.gitRevision = gitRevision;
    }

    public ResourceGitInfo(String resId, String resourceType) {
        this.resourceType = resourceType;
        this.gitRepo = GitRepositories.getRepo(resourceType);
        Model adm = QueryProcessor.describeModel(BDA_PREFIX + resId);
        NodeIterator ni = adm.listObjectsOfProperty(ResourceFactory.createProperty(ADM_PREFIX + GIT_PATH));
        this.gitPath = ni.next().asLiteral().getString();
        ni = adm.listObjectsOfProperty(ResourceFactory.createProperty(ADM_PREFIX + GIT_REVISION));
        this.gitRevision = ni.next().asLiteral().getString();
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

    @Override
    public String toString() {
        return "ResourceGitInfo [gitRepo=" + gitRepo + ", gitPath=" + gitPath + ", gitRevision=" + gitRevision + ", resourceType=" + resourceType + "]";
    }

    public static void main(String[] args) {
        EditConfig.init();
        System.out.println(new ResourceGitInfo("W22084", "work"));
    }

}
