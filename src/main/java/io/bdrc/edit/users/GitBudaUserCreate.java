package io.bdrc.edit.users;

import java.io.File;
import java.io.FileOutputStream;

import org.apache.jena.query.DatasetFactory;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ResourceFactory;
import org.apache.jena.rdfconnection.RDFConnectionFuseki;
import org.apache.jena.rdfconnection.RDFConnectionRemoteBuilder;
import org.apache.jena.sparql.core.DatasetGraph;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.bdrc.edit.EditConfig;
import io.bdrc.edit.EditConstants;
import io.bdrc.edit.helpers.AdminData;
import io.bdrc.edit.helpers.Helpers;
import io.bdrc.jena.sttl.STriGWriter;
import io.bdrc.libraries.GlobalHelpers;
import io.bdrc.libraries.Prefixes;

public class GitBudaUserCreate implements Runnable {

    public final static Logger log = LoggerFactory.getLogger(GitBudaUserCreate.class);
    String userId, userName;
    Model pub, priv;

    public GitBudaUserCreate(String userId, Model pub, Model priv, String userName) {
        this.userId = userId;
        this.pub = pub;
        this.priv = priv;
        this.userName = userName;
    }

    @Override
    public void run() {
        String dirpath = EditConfig.getProperty("usersGitLocalRoot");
        File theDir = new File(dirpath);
        Repository r = null;
        if (!theDir.exists()) {
            r = BudaUser.ensureUserGitRepo();
        }
        FileOutputStream fos = null;
        try {
            String bucket = GlobalHelpers.getTwoLettersBucket(userId);
            AdminData ad = new AdminData(userId, AdminData.USER_RES_TYPE, bucket + "/" + userId + ".trig");
            Model adm = ad.asModel();
            Helpers.createDirIfNotExists(dirpath + bucket + "/");
            fos = new FileOutputStream(dirpath + bucket + "/" + userId + ".trig");
            DatasetGraph dsg = DatasetFactory.create().asDatasetGraph();
            dsg.addGraph(ResourceFactory.createResource(BudaUser.PUBLIC_PFX + userId).asNode(), pub.getGraph());
            dsg.addGraph(ResourceFactory.createResource(BudaUser.PRIVATE_PFX + userId).asNode(), priv.getGraph());
            dsg.addGraph(ResourceFactory.createResource(EditConstants.BDA + userId).asNode(), adm.getGraph());
            new STriGWriter().write(fos, dsg, Prefixes.getPrefixMap(), "", GlobalHelpers.createWriterContext());
            if (r == null) {
                r = BudaUser.ensureUserGitRepo();
            }
            long git1 = System.currentTimeMillis();
            Git git = new Git(r);
            git.add().addFilepattern(".").call();
            long git2 = System.currentTimeMillis();
            log.info("Git add file took {} ms", (git2 - git1));
            // rev =
            git.commit().setMessage("User " + userName + " was created").call();
            long git3 = System.currentTimeMillis();
            log.info("Git commit took {} ms", (git3 - git2));
            git.push()
                    .setCredentialsProvider(
                            new UsernamePasswordCredentialsProvider(EditConfig.getProperty("gitUser"), EditConfig.getProperty("gitPass")))
                    .setRemote(EditConfig.getProperty("usersRemoteGit")).call();
            git.close();
            long git4 = System.currentTimeMillis();
            log.info("Git push took {} ms", (git4 - git3));
            RDFConnectionRemoteBuilder builder = RDFConnectionFuseki.create().destination(EditConfig.getProperty("fusekiAuthData"));
            RDFConnectionFuseki fusConn = ((RDFConnectionFuseki) builder.build());
            Helpers.putModel(fusConn, BudaUser.PUBLIC_PFX + userId, pub);
            Helpers.putModel(fusConn, BudaUser.PRIVATE_PFX + userId, priv);
            fusConn.close();
            // Public graph is pushed to bdrcrw
            builder = RDFConnectionFuseki.create().destination(EditConfig.getProperty("fusekiUrl").replace("query", ""));
            fusConn = ((RDFConnectionFuseki) builder.build());
            Helpers.putModel(fusConn, BudaUser.PUBLIC_PFX + userId, pub);
            // adding adminData graph to public dataset
            Helpers.putModel(fusConn, EditConstants.BDA + userId, adm);
            long fus = System.currentTimeMillis();
            log.info("Updating fuseki dataset after git took {} ms", (fus - git4));
            fusConn.close();
        } catch (Exception e) {
            log.error("Failed to add new Buda user :" + userName, e);
        }

    }

}
