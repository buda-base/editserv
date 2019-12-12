package io.bdrc.edit.users;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.security.NoSuchAlgorithmException;
import java.util.Calendar;

import org.apache.jena.query.DatasetFactory;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.ResIterator;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.ResourceFactory;
import org.apache.jena.rdfconnection.RDFConnectionFuseki;
import org.apache.jena.rdfconnection.RDFConnectionRemoteBuilder;
import org.apache.jena.sparql.core.DatasetGraph;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.AbortedByHookException;
import org.eclipse.jgit.api.errors.ConcurrentRefUpdateException;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.NoHeadException;
import org.eclipse.jgit.api.errors.NoMessageException;
import org.eclipse.jgit.api.errors.UnmergedPathsException;
import org.eclipse.jgit.api.errors.WrongRepositoryStateException;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.bdrc.auth.model.User;
import io.bdrc.edit.EditConfig;
import io.bdrc.edit.EditConstants;
import io.bdrc.edit.helpers.AdminData;
import io.bdrc.edit.helpers.Helpers;
import io.bdrc.edit.helpers.UserDataUpdate;
import io.bdrc.jena.sttl.STriGWriter;
import io.bdrc.libraries.GlobalHelpers;
import io.bdrc.libraries.Prefixes;

public class UserDataService {

    public final static Logger log = LoggerFactory.getLogger(UserDataService.class.getName());
    public static final String gitignore = "# Ignore everything\n" + "*\n" + "# Don't ignore directories, so we can recurse into them\n" + "!*/\n" + "# Don't ignore .gitignore and *.foo files\n" + "!.gitignore\n" + "!*.trig\n" + "";

    public static String PUB_SCOPE = "public";
    public static String PRIV_SCOPE = "private";

    public static RevCommit addNewBudaUser(User user) {
        RevCommit rev = null;
        Model[] mod = BudaUser.createBudaUserModels(user);
        Model pub = mod[0];
        Model priv = mod[1];
        String userId = null;
        ResIterator rit = priv.listSubjectsWithProperty(ResourceFactory.createProperty(BudaUser.BDOU_PFX + "hasUserProfile"));
        if (rit.hasNext()) {
            Resource r = rit.next();
            userId = r.getLocalName();
        } else {
            log.error("Invalid user model for {}", user);
            return null;
        }
        String dirpath = EditConfig.getProperty("usersGitLocalRoot");
        File theDir = new File(dirpath);
        Repository r = null;
        if (!theDir.exists()) {
            r = ensureUserGitRepo();
        }
        FileOutputStream fos = null;
        try {
            String bucket = GlobalHelpers.getTwoLettersBucket(userId);
            AdminData ad = new AdminData(userId, AdminData.USER_RES_TYPE, bucket + "/" + userId + ".trig");
            Model adm = ad.asModel();
            createDirIfNotExists(dirpath + bucket + "/");
            fos = new FileOutputStream(dirpath + bucket + "/" + userId + ".trig");
            DatasetGraph dsg = DatasetFactory.create().asDatasetGraph();
            dsg.addGraph(ResourceFactory.createResource(BudaUser.PUBLIC_PFX + userId).asNode(), pub.getGraph());
            dsg.addGraph(ResourceFactory.createResource(BudaUser.PRIVATE_PFX + userId).asNode(), priv.getGraph());
            dsg.addGraph(ResourceFactory.createResource(EditConstants.BDA + userId).asNode(), adm.getGraph());
            new STriGWriter().write(fos, dsg, Prefixes.getPrefixMap(), "", GlobalHelpers.createWriterContext());
            if (r == null) {
                r = ensureUserGitRepo();
            }
            Git git = new Git(r);
            git.add().addFilepattern(".").call();
            rev = git.commit().setMessage("User " + user.getName() + " was created").call();
            git.close();
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
            fusConn.close();
        } catch (Exception e) {
            log.error("Failed to add new Buda user :" + user.getName(), e);
        }
        return rev;
    }

    public static RevCommit update(UserDataUpdate data)
            throws IOException, NoSuchAlgorithmException, NoHeadException, NoMessageException, UnmergedPathsException, ConcurrentRefUpdateException, WrongRepositoryStateException, AbortedByHookException, GitAPIException {
        RevCommit rev = null;
        String dirpath = EditConfig.getProperty("usersGitLocalRoot");
        String bucket = GlobalHelpers.getTwoLettersBucket(data.getUserId());
        createDirIfNotExists(dirpath + bucket + "/");
        FileOutputStream fos = new FileOutputStream(dirpath + bucket + "/" + data.getUserId() + ".trig");
        DatasetGraph dsg = data.getDatasetGraph();
        ModelFactory.createModelForGraph(dsg.getUnionGraph()).write(System.out, "TURTLE");
        new STriGWriter().write(fos, dsg, Prefixes.getPrefixMap(), "", GlobalHelpers.createWriterContext());
        Repository r = null;
        if (r == null) {
            r = ensureUserGitRepo();
        }
        Git git = new Git(r);
        if (!git.status().call().isClean()) {
            git.add().addFilepattern(".").call();
            rev = git.commit().setMessage("User " + data.getUserId() + " was updated" + Calendar.getInstance().getTime()).call();
            data.setGitRevisionInfo(rev.getName());
        }
        git.close();
        return rev;
    }

    public static RevCommit update(String userId, Model pub, Model priv)
            throws IOException, NoSuchAlgorithmException, NoHeadException, NoMessageException, UnmergedPathsException, ConcurrentRefUpdateException, WrongRepositoryStateException, AbortedByHookException, GitAPIException {
        RevCommit rev = null;
        String dirpath = EditConfig.getProperty("usersGitLocalRoot");
        String bucket = GlobalHelpers.getTwoLettersBucket(userId);
        createDirIfNotExists(dirpath + bucket + "/");
        FileOutputStream fos = new FileOutputStream(dirpath + bucket + "/" + userId + ".trig");
        DatasetGraph dsg = DatasetFactory.create().asDatasetGraph();
        dsg.addGraph(ResourceFactory.createResource(BudaUser.PUBLIC_PFX + userId).asNode(), pub.getGraph());
        dsg.addGraph(ResourceFactory.createResource(BudaUser.PRIVATE_PFX + userId).asNode(), priv.getGraph());
        new STriGWriter().write(fos, dsg, Prefixes.getPrefixMap(), "", GlobalHelpers.createWriterContext());
        Repository r = null;
        if (r == null) {
            r = ensureUserGitRepo();
        }
        Git git = new Git(r);
        git.add().addFilepattern(".").call();
        rev = git.commit().setMessage("User " + userId + " was updated").call();
        git.close();
        return rev;
    }

    public static Repository ensureUserGitRepo() {
        Repository repository = null;
        String dirpath = EditConfig.getProperty("usersGitLocalRoot");
        createDirIfNotExists(dirpath);
        FileRepositoryBuilder builder = new FileRepositoryBuilder();
        File gitDir = new File(dirpath + "/.git");
        File wtDir = new File(dirpath);
        try {
            repository = builder.setGitDir(gitDir).setWorkTree(wtDir).readEnvironment() // scan environment GIT_* variables
                    .build();
            if (!repository.getObjectDatabase().exists()) {
                log.info("create git repository in {}", dirpath);
                repository.create();
                PrintWriter out = new PrintWriter(dirpath + ".gitignore");
                out.println(gitignore);
                out.close();
            }
        } catch (IOException e) {
            log.error("Could not get git repository: ", e);
        }
        return repository;
    }

    public static void createDirIfNotExists(String dir) {
        File theDir = new File(dir);
        if (!theDir.exists()) {
            try {
                theDir.mkdir();
            } catch (SecurityException se) {
                log.error("Could not create " + dir, se);
            }
        }
    }

}