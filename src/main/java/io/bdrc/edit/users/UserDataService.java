package io.bdrc.edit.users;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.security.NoSuchAlgorithmException;

import org.apache.jena.query.DatasetFactory;
import org.apache.jena.rdf.model.Model;
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
import io.bdrc.edit.sparql.QueryProcessor;
import io.bdrc.jena.sttl.STriGWriter;
import io.bdrc.libraries.GlobalHelpers;
import io.bdrc.libraries.Prefixes;

public class UserDataService {

    public final static Logger log = LoggerFactory.getLogger(UserDataService.class.getName());
    public static final String gitignore = "# Ignore everything\n" + "*\n" + "# Don't ignore directories, so we can recurse into them\n" + "!*/\n" + "# Don't ignore .gitignore and *.foo files\n" + "!.gitignore\n" + "!*.trig\n" + "";

    public static RevCommit addNewBudaUser(User user) {
        RevCommit rev = null;
        Model[] mod = BudaUser.createBudaUserModels(user);
        log.info("public model for user {} is {}", user, mod[0]);
        log.info("private model for user {} is {}", user, mod[1]);
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
        String dirpath = System.getProperty("user.dir") + "/users/";
        File theDir = new File(dirpath);
        Repository r = null;
        if (!theDir.exists()) {
            r = ensureUserGitRepo();
        }
        FileOutputStream fos = null;
        try {
            String bucket = GlobalHelpers.getTwoLettersBucket(userId);
            createDirIfNotExists(dirpath + bucket + "/");
            fos = new FileOutputStream(dirpath + bucket + "/" + userId + ".trig");
            DatasetGraph dsg = DatasetFactory.create().asDatasetGraph();
            dsg.addGraph(ResourceFactory.createResource(BudaUser.PUBLIC_PFX + userId).asNode(), pub.getGraph());
            dsg.addGraph(ResourceFactory.createResource(BudaUser.PRIVATE_PFX + userId).asNode(), priv.getGraph());
            new STriGWriter().write(fos, dsg, Prefixes.getPrefixMap(), "", GlobalHelpers.createWriterContext());
            if (r == null) {
                r = ensureUserGitRepo();
            }
            Git git = new Git(r);
            if (!git.status().call().isClean()) {
                git.add().addFilepattern(".").call();
                rev = git.commit().setMessage("User " + user.getName() + " was created").call();
            }
            git.close();
            RDFConnectionRemoteBuilder builder = RDFConnectionFuseki.create().destination(EditConfig.getProperty("fusekiAuthData"));
            RDFConnectionFuseki fusConn = ((RDFConnectionFuseki) builder.build());
            QueryProcessor.putModel(fusConn, BudaUser.PUBLIC_PFX + userId, pub);
            QueryProcessor.putModel(fusConn, BudaUser.PRIVATE_PFX + userId, priv);
            fusConn.close();
            // Public graph is pushed to bdrcrw
            builder = RDFConnectionFuseki.create().destination(EditConfig.getProperty("fusekiUrl").replace("query", ""));
            fusConn = ((RDFConnectionFuseki) builder.build());
            QueryProcessor.putModel(fusConn, BudaUser.PUBLIC_PFX + userId, pub);
            fusConn.close();
        } catch (Exception e) {
            log.error("Failed to add new Buda user :" + user.getName(), e);
        }
        return rev;
    }

    public static RevCommit update(String userId, Model pub, Model priv)
            throws IOException, NoSuchAlgorithmException, NoHeadException, NoMessageException, UnmergedPathsException, ConcurrentRefUpdateException, WrongRepositoryStateException, AbortedByHookException, GitAPIException {
        RevCommit rev = null;
        String dirpath = System.getProperty("user.dir") + "/users/";
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
        if (!git.status().call().isClean()) {
            git.add().addFilepattern(".").call();
            rev = git.commit().setMessage("User " + userId + " was updated").call();
        }
        git.close();
        return rev;
    }

    public static Repository ensureUserGitRepo() {
        Repository repository = null;
        String dirpath = System.getProperty("user.dir") + "/users/";
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
