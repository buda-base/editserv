package io.bdrc.edit.helpers;

import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.NodeIterator;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.ResourceFactory;
import org.apache.jena.vocabulary.RDF;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.bdrc.edit.EditConfig;
import io.bdrc.edit.EditConstants;
import io.bdrc.edit.sparql.QueryProcessor;
import io.bdrc.libraries.Prefixes;

public class AdminData {

    public static String USER_RES_TYPE = "user";

    public GitRepo gitRepo;
    public String resId;
    public String gitPath;
    public String resourceType;

    public final static Logger log = LoggerFactory.getLogger(AdminData.class.getName());

    public AdminData(String resId, String resourceType, String gitPath) {
        this.resId = resId;
        this.resourceType = resourceType;
        if (resourceType.equals(USER_RES_TYPE)) {
            this.gitRepo = GitRepositories.getRepo(resourceType);
        }
        this.gitPath = gitPath;
    }

    public AdminData(String resId, String resourceType) {
        this.resId = resId;
        this.resourceType = resourceType;
        if (resourceType.equals(USER_RES_TYPE)) {
            this.gitRepo = GitRepositories.getRepo(resourceType);
        }
        Model adm = QueryProcessor.describeModel(EditConstants.BDA + resId, null);
        NodeIterator ni = adm.listObjectsOfProperty(EditConstants.GIT_PATH);
        if (ni.hasNext()) {
            this.gitPath = ni.next().asLiteral().getString();
        }
    }

    public Model asModel() {
        log.info("GIT REPO = {}", gitRepo);
        Model m = ModelFactory.createDefaultModel();
        Resource r = ResourceFactory.createResource(EditConstants.BDA + resId);
        m.add(ResourceFactory.createStatement(r, RDF.type, EditConstants.ADMIN_DATA));
        if (gitRepo != null) {
            m.add(ResourceFactory.createStatement(r, EditConstants.GIT_REPO, ResourceFactory.createResource(gitRepo.getFullResId())));
        }
        if (gitPath != null) {
            m.add(ResourceFactory.createStatement(r, EditConstants.GIT_PATH, ResourceFactory.createPlainLiteral(gitPath)));
        }
        m.add(ResourceFactory.createStatement(r, EditConstants.ADMIN_GRAPH_ID, ResourceFactory.createResource(EditConstants.BDG + resId)));
        m.add(ResourceFactory.createStatement(r, EditConstants.ADMIN_ABOUT, ResourceFactory.createResource(EditConstants.BDR + resId)));
        m.add(ResourceFactory.createStatement(r, EditConstants.ADMIN_STATUS, EditConstants.STATUS_PROV));
        m.setNsPrefixes(Prefixes.getPrefixMapping());
        return m;
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

    @Override
    public String toString() {
        return "AdminData [gitRepo=" + gitRepo + ", resId=" + resId + ", gitPath=" + gitPath + ", resourceType=" + resourceType + "]";
    }

    public static void main(String[] args) {
        EditConfig.init();
        Model m = new AdminData("U1669274875", "user").asModel();
        m.write(System.out, "TURTLE");
    }

}
