package io.bdrc.edit.helpers;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.concurrent.TimeUnit;

import org.apache.jena.ontology.OntDocumentManager;
import org.apache.jena.ontology.OntModel;
import org.apache.jena.ontology.OntModelSpec;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.errors.AmbiguousObjectException;
import org.eclipse.jgit.errors.IncorrectObjectTypeException;
import org.eclipse.jgit.errors.RevisionSyntaxException;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.RepositoryCache;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.eclipse.jgit.util.FS;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.topbraid.shacl.engine.ShapesGraph;
import org.topbraid.shacl.validation.ValidationEngineConfiguration;

import io.bdrc.edit.commons.ops.CommonsRead;

public class Shapes implements Runnable {
    
    static String GIT_SHAPES_REMOTE_URL = "https://github.com/buda-base/editor-templates.git";
    private static int delayInSeconds = 2;
    static Repository localRepo;
    public static Model fullMod = null;
    public static org.apache.jena.shacl.Shapes fullShapes = null;
    public static final ValidationEngineConfiguration configuration = new ValidationEngineConfiguration().setValidateShapes(true);
    public static ShapesGraph shapesGraph = null;
    public static HashMap<String, Model> modelsBase = new HashMap<>();
    
    final static Logger log = LoggerFactory.getLogger(Shapes.class);
    
    static {
        try {
            commonUpdateRepo(System.getProperty("user.dir") + "/editor-templates/", GIT_SHAPES_REMOTE_URL);
            updateFromRepo();
        } catch (RevisionSyntaxException | IOException | InterruptedException e) {
            log.error("error initializing shapes", e);
        }
    }

    public static void updateFromRepo() {
        try {
            modelsBase = new HashMap<>();
            OntModelSpec oms = new OntModelSpec(OntModelSpec.OWL_MEM);
            OntDocumentManager odm = new OntDocumentManager(System.getProperty("user.dir") + "/editor-templates/ont-policy.rdf");
            odm.setProcessImports(true);
            odm.setCacheModels(false);
            odm.getFileManager().resetCache();
            oms.setDocumentManager(odm);
            Iterator<String> it = odm.listDocuments();
            fullMod = ModelFactory.createDefaultModel();
            while (it.hasNext()) {
                String uri = it.next();
                log.info("OntManagerDoc : {}", uri);
                OntModel om = odm.getOntology(uri, oms);
                fullMod.add(om);
                
            }
            fullShapes = org.apache.jena.shacl.Shapes.parse(fullMod);
            CommonsRead.nodeShapesToProps = new HashMap<>();
            //shapesGraph = ShapesGraphFactory.get().createShapesGraph(shapesModel);
            log.info("Done with OntShapesData initialization ! Uri set is {}", modelsBase.keySet());
        } catch (Exception ex) {
            log.error("Error updating OntShapesData Model", ex);
        }
    }
    
    public static String commonUpdateRepo(String localPath, String remoteUrl)
            throws RevisionSyntaxException, AmbiguousObjectException, IncorrectObjectTypeException, IOException, InterruptedException {
        TimeUnit.SECONDS.sleep(delayInSeconds);
        String commit = null;
        FileRepositoryBuilder builder = new FileRepositoryBuilder();
        File localGit = new File(localPath + "/.git");
        log.info("LOCAL GIT >> {}", localGit);
        File WlocalGit = new File(localPath);
        log.info("WLOCAL GIT >> {}", WlocalGit);
        boolean isGitRepo = RepositoryCache.FileKey.isGitRepository(localGit, FS.DETECTED);
        log.info("IS GIT >> {}", isGitRepo);
        // init local git dir and clone remote repository if not present locally
        if (!isGitRepo) {
            initRepo(localPath, remoteUrl);
        } else {
            try {
                localRepo = builder.setGitDir(localGit).setWorkTree(WlocalGit).readEnvironment() // scan environment GIT_* variables
                        .build();
            } catch (IOException ex) {
                ex.printStackTrace();
                log.error("Git was unable to setup repository at init time " + localGit.getPath() + " directory ", ex.getMessage());
            }
            commit = updateRepo(localRepo);
        }
        return commit;
    }
    
    private static void initRepo(String localPath, String remoteUrl) {
        try {
            log.info("Cloning {} into dir {}", remoteUrl, localPath);
            Git result = Git.cloneRepository().setDirectory(new File(localPath)).setURI(remoteUrl).call();
            result.checkout().setName("master").call();
            result.close();
        } catch (Exception ex) {
            ex.printStackTrace();
            log.error(" Git was unable to pull repository : " + remoteUrl + " directory ", ex.getMessage());
        }
    }

    private static String updateRepo(Repository localRepo)
            throws RevisionSyntaxException, AmbiguousObjectException, IncorrectObjectTypeException, IOException {
        String commitId = null;
        try {
            log.info("UPDATING LOCAL REPO >> {}", localRepo);
            Git git = new Git(localRepo);
            git.pull().call();
            git.close();
            commitId = localRepo.resolve(Constants.HEAD).getName().substring(0, 7);
            log.info("LOCAL REPO >> {} was updated with commit {}", localRepo, commitId);
        } catch (Exception ex) {
            log.error(" Git was unable to pull in directory {}, message: {}", localRepo, ex.getMessage());
        }
        return commitId;
    }

    @Override
    public void run() {
        try {
            commonUpdateRepo(System.getProperty("user.dir") + "/editor-templates/", GIT_SHAPES_REMOTE_URL);
        } catch (RevisionSyntaxException | IOException | InterruptedException e) {
            log.error("couldn't update repo", e);
        }
        updateFromRepo();
    }
    
}
