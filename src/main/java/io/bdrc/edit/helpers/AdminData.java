package io.bdrc.edit.helpers;

import java.security.NoSuchAlgorithmException;

import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.ResourceFactory;
import org.apache.jena.vocabulary.RDF;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.bdrc.edit.EditConfig;
import io.bdrc.edit.EditConstants;
import io.bdrc.edit.commons.ops.CommonsGit;
import io.bdrc.edit.controllers.RIDController;
import io.bdrc.libraries.GlobalHelpers;

public class AdminData {

    public static String USER_RES_TYPE = "user";

    public String resLname;
    public String gitPath;
    public String typePrefix;
    public String repoPath;
    public String uri;

    public final static Logger log = LoggerFactory.getLogger(AdminData.class.getName());

    public AdminData(String resLname, String gitPath) {
        this.uri = "http://purl.bdrc.io/admindata/" + resLname;
        this.resLname = resLname;
        this.typePrefix = RIDController.getTypePrefix(resLname);
        this.gitPath = gitPath;
        this.repoPath = CommonsGit.prefixToRepoPath.get(this.typePrefix);
    }

    public AdminData(String resLname) {
        this.uri = "http://purl.bdrc.io/admindata/" + resLname;
        this.resLname = resLname;
        this.typePrefix = RIDController.getTypePrefix(resLname);
        this.gitPath = GlobalHelpers.getTwoLettersBucket(resLname) + "/" + resLname + ".trig";
        this.repoPath = CommonsGit.prefixToRepoPath.get(this.typePrefix);
    }

    public Model asModel() {
        Model m = ModelFactory.createDefaultModel();
        Resource r = ResourceFactory.createResource(EditConstants.BDA + resLname);
        m.add(ResourceFactory.createStatement(r, RDF.type, EditConstants.ADMIN_DATA));
        if (gitPath != null) {
            m.add(ResourceFactory.createStatement(r, EditConstants.GIT_PATH, ResourceFactory.createPlainLiteral(gitPath)));
        }
        m.add(ResourceFactory.createStatement(r, EditConstants.ADMIN_GRAPH_ID, ResourceFactory.createResource(EditConstants.BDG + resLname)));
        m.add(ResourceFactory.createStatement(r, EditConstants.ADMIN_ABOUT, ResourceFactory.createResource(EditConstants.BDR + resLname)));
        m.add(ResourceFactory.createStatement(r, EditConstants.ADMIN_STATUS, EditConstants.STATUS_PROV));
        m.setNsPrefixes(EditConfig.prefix.getPrefixMapping());
        return m;
    }

    public String getGitPath() {
        return gitPath;
    }

    public String getUri() {
        return uri;
    }

    @Override
    public String toString() {
        return "AdminData [resId=" + resLname + ", gitPath=" + gitPath + "]";
    }

    public static void main(String[] args) throws Exception {
        EditConfig.init();
        Model m = new AdminData("U1669274875", "user").asModel();
        m.write(System.out, "TURTLE");
    }

}
