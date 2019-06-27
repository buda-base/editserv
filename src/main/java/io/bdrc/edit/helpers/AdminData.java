package io.bdrc.edit.helpers;

import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.NodeIterator;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.ResourceFactory;
import org.apache.jena.vocabulary.RDF;

import io.bdrc.edit.EditConfig;
import io.bdrc.edit.EditConstants;
import io.bdrc.edit.sparql.Prefixes;
import io.bdrc.edit.sparql.QueryProcessor;

public class AdminData {

    public static Resource ADMIN_DATA = ResourceFactory.createResource("http://purl.bdrc.io/ontology/admin/AdminData");
    public static Resource STATUS_PROV = ResourceFactory.createResource("http://purl.bdrc.io/admindata/StatusProvisional");
    public static Property GIT_REPO = ResourceFactory.createProperty(EditConstants.ADM + "gitRepo");
    public static Property GIT_PATH = ResourceFactory.createProperty(EditConstants.ADM + "gitPath");
    public static Property GIT_REVISION = ResourceFactory.createProperty(EditConstants.ADM + "gitRevision");
    public static Property ADMIN_ABOUT = ResourceFactory.createProperty(EditConstants.ADM + "adminAbout");
    public static Property ADMIN_STATUS = ResourceFactory.createProperty(EditConstants.ADM + "status");
    public static Property ADMIN_GRAPH_ID = ResourceFactory.createProperty(EditConstants.ADM + "graphId");

    public GitRepo gitRepo;
    public String resId;
    public String gitPath;
    public String gitRevision;
    public String resourceType;

    public AdminData(String resId, String resourceType, String gitPath, String gitRevision) {
        this.resId = resId;
        this.resourceType = resourceType;
        this.gitRepo = GitRepositories.getRepo(resourceType);
        this.gitPath = gitPath;
        this.gitRevision = gitRevision;
    }

    public AdminData(String resId, String resourceType) {
        this.resId = resId;
        this.resourceType = resourceType;
        this.gitRepo = GitRepositories.getRepo(resourceType);
        Model adm = QueryProcessor.describeModel(EditConstants.BDA + resId);
        NodeIterator ni = adm.listObjectsOfProperty(GIT_PATH);
        this.gitPath = ni.next().asLiteral().getString();
        ni = adm.listObjectsOfProperty(GIT_REVISION);
        this.gitRevision = ni.next().asLiteral().getString();
    }

    public Model asModel() {
        Model m = ModelFactory.createDefaultModel();
        Resource r = ResourceFactory.createResource(EditConstants.BDA + resId);
        m.add(ResourceFactory.createStatement(r, RDF.type, ADMIN_DATA));
        m.add(ResourceFactory.createStatement(r, GIT_REPO, ResourceFactory.createResource(gitRepo.getFullResId())));
        m.add(ResourceFactory.createStatement(r, GIT_PATH, ResourceFactory.createPlainLiteral(gitPath)));
        m.add(ResourceFactory.createStatement(r, GIT_REVISION, ResourceFactory.createPlainLiteral(gitRevision)));
        m.add(ResourceFactory.createStatement(r, ADMIN_GRAPH_ID, ResourceFactory.createResource(EditConstants.BDG + resId)));
        m.add(ResourceFactory.createStatement(r, ADMIN_ABOUT, ResourceFactory.createResource(EditConstants.BDR + resId)));
        m.add(ResourceFactory.createStatement(r, ADMIN_STATUS, STATUS_PROV));
        m.setNsPrefixes(Prefixes.getPrefixMapping());
        return m;
    }

    public GitRepo getGitRepo() {
        return gitRepo;
    }

    public void setGitRepo(GitRepo gitRepo) {
        this.gitRepo = gitRepo;
    }

    public String getResId() {
        return resId;
    }

    public void setResId(String id) {
        this.resId = id;
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
        return "AdminData [gitRepo=" + gitRepo + ", resId=" + resId + ", gitPath=" + gitPath + ", gitRevision=" + gitRevision + ", resourceType=" + resourceType + "]";
    }

    public static void main(String[] args) {
        EditConfig.init();
        Model m = new AdminData("P1524", "person").asModel();
        m.write(System.out, "TURTLE");
    }

}
