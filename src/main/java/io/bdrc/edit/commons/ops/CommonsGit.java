package io.bdrc.edit.commons.ops;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import org.apache.jena.query.Dataset;
import org.apache.jena.query.DatasetFactory;
import org.apache.jena.query.QuerySolution;
import org.apache.jena.query.ResultSet;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Resource;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.Status;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;
import org.seaborne.patch.RDFPatch;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.bdrc.edit.EditConfig;
import io.bdrc.edit.commons.data.QueryProcessor;
import io.bdrc.edit.controllers.RIDController;
import io.bdrc.edit.helpers.Helpers;
import io.bdrc.edit.helpers.ModelUtils;
import io.bdrc.edit.txn.exceptions.ModuleException;
import io.bdrc.edit.txn.exceptions.VersionConflictException;
import io.bdrc.jena.sttl.STriGWriter;
import io.bdrc.libraries.GlobalHelpers;
import io.bdrc.libraries.Models;

public class CommonsGit {

    public static Logger log = LoggerFactory.getLogger(CommonsGit.class);
    
    public static final class GitInfo {
        public String repoLname = null;
        public String pathInRepo = null;
        public String revId = null;
        // in the case of a new resource ,the dataset doesn't exist and is null
        public Dataset ds = null;
        
        public String toString() {
            return "repoLname: "+repoLname+", pathInRepo: "+pathInRepo+", revId: "+revId;
        }
    }
    
    public final static Map<String,String> prefixToGitLname = new HashMap<>();
    public final static Map<String,String> gitLnameToRepoPath = new HashMap<>();
    public final static Map<String,String> gitLnameToRemoteUrl = new HashMap<>();
    public static UsernamePasswordCredentialsProvider gitCredentialProvider = null;
    
    public static void init() {
        gitLnameToRepoPath.put("GR0100", EditConfig.getProperty("usersGitLocalRoot"));
        prefixToGitLname.put("U", "GR0100");
        final String normalPath = EditConfig.getProperty("gitLocalRoot");
        gitLnameToRepoPath.put("GR0008", normalPath+"works");
        gitLnameToRepoPath.put("GR0015", normalPath+"subscribers");
        gitLnameToRepoPath.put("GR0012", normalPath+"instances");
        gitLnameToRepoPath.put("GR0011", normalPath+"collections");
        gitLnameToRepoPath.put("GR0013", normalPath+"einstances");
        gitLnameToRepoPath.put("GR0003", normalPath+"items");
        gitLnameToRepoPath.put("GR0014", normalPath+"iinstances");
        gitLnameToRepoPath.put("GR0006", normalPath+"persons");
        gitLnameToRepoPath.put("GR0005", normalPath+"places");
        gitLnameToRepoPath.put("GR0010", normalPath+"roles");
        gitLnameToRepoPath.put("GR0004", normalPath+"lineages");
        gitLnameToRepoPath.put("GR0001", normalPath+"corporations");
        gitLnameToRepoPath.put("GR0007", normalPath+"topics");
        gitLnameToRepoPath.put("GR0100", EditConfig.getProperty("usersGitLocalRoot"));
        final String normalBase = EditConfig.getProperty("gitRemoteBase");
        gitLnameToRemoteUrl.put("GR0008", normalBase+"works");
        gitLnameToRemoteUrl.put("GR0015", normalBase+"subscribers");
        gitLnameToRemoteUrl.put("GR0012", normalBase+"instances");
        gitLnameToRemoteUrl.put("GR0011", normalBase+"collections");
        gitLnameToRemoteUrl.put("GR0013", normalBase+"einstances");
        gitLnameToRemoteUrl.put("GR0003", normalBase+"items");
        gitLnameToRemoteUrl.put("GR0014", normalBase+"iinstances");
        gitLnameToRemoteUrl.put("GR0006", normalBase+"persons");
        gitLnameToRemoteUrl.put("GR0005", normalBase+"places");
        gitLnameToRemoteUrl.put("GR0010", normalBase+"roles");
        gitLnameToRemoteUrl.put("GR0004", normalBase+"lineages");
        gitLnameToRemoteUrl.put("GR0001", normalBase+"corporations");
        gitLnameToRemoteUrl.put("GR0007", normalBase+"topics");
        prefixToGitLname.put("WAS", "GR0008");
        prefixToGitLname.put("ITW", "GR0003");
        prefixToGitLname.put("PRA", "GR0015");
        prefixToGitLname.put("WA", "GR0008");
        prefixToGitLname.put("MW", "GR0012");
        prefixToGitLname.put("PR", "GR0011");
        prefixToGitLname.put("IE", "GR0013");
        prefixToGitLname.put("IT", "GR0003");
        prefixToGitLname.put("W", "GR0014");
        prefixToGitLname.put("P", "GR0006");
        prefixToGitLname.put("G", "GR0005");
        prefixToGitLname.put("R", "GR0010");
        prefixToGitLname.put("L", "GR0004");
        prefixToGitLname.put("C", "GR0001");
        prefixToGitLname.put("T", "GR0007");
        if (!EditConfig.dryrunmode)
            gitCredentialProvider = new UsernamePasswordCredentialsProvider(EditConfig.getProperty("gitUser"), EditConfig.getProperty("gitPass"));
    }
    
    public static GitInfo gitInfoFromFuseki(final Resource r) {
        log.info("search graph for ", r, " in Fuseki");
        final String query = "select distinct ?g ?repo ?path { graph ?g { <"+r.getURI()+"> a ?t } . ?adm adm:graphId ?g ; adm:gitPath ?path ; adm:gitRepo ?repo . }";
        ResultSet rs = QueryProcessor.getSelectResultSet(query, null);
        if (!rs.hasNext()) {
            log.info("did not find graph for ", r, " in Fuseki");
            return null;
        }
        final QuerySolution qs = rs.next();
        if (log.isDebugEnabled()) {
            log.debug(qs.toString());
        }
        final Resource graph = qs.getResource("g");
        if (!graph.getURI().startsWith(Models.BDG)) {
            log.error("received unexpected graph URL for ", r, ": ", graph);
            return null;
        }
        final GitInfo res = new GitInfo();
        res.repoLname = qs.getResource("repo").getLocalName();
        res.pathInRepo = qs.getLiteral("path").getString();
        return res;
    }
    
    public static GitInfo gitInfoForResource(final Resource r) throws IOException, ModuleException {
        final String rLname = r.getLocalName();
        // first we guess and check if the file exists
        final GitInfo guessedGitInfo = new GitInfo();
        guessedGitInfo.pathInRepo = Models.getMd5(rLname)+"/"+rLname+".trig";
        final String typePrefix = RIDController.getTypePrefix(r.getLocalName());
        if (typePrefix == null)
            throw new ModuleException("unable to find type for "+r.getLocalName());
        final String repoLname = prefixToGitLname.get(typePrefix);
        if (repoLname == null)
            throw new ModuleException("unable to find repo lname for prefix "+typePrefix);
        log.debug("typeprefix {} gitlname {}", typePrefix, repoLname);
        guessedGitInfo.repoLname = repoLname;
        String guessedPath = gitLnameToRepoPath.get(repoLname)+"/"+guessedGitInfo.pathInRepo;
        if ((new File(guessedPath)).exists()) {
            guessedGitInfo.ds = Helpers.datasetFromTrig(GlobalHelpers.readFileContent(guessedPath));
            log.info("found git file {} for resource {}", guessedPath, r);
            return guessedGitInfo;
        } else {
            log.info("didn't find git file {} for resource {}", guessedPath, r);
        }
        // if not, we try to take the part before the first underscore
        final int underscore_idx = rLname.indexOf('_');
        if (underscore_idx > 0) {
            final String rLname_guess2 = rLname.substring(0, underscore_idx);
            guessedGitInfo.pathInRepo = Models.getMd5(rLname_guess2)+"/"+rLname_guess2+".trig";
            // prefix and thus repoLname don't change in that case
            guessedPath = gitLnameToRepoPath.get(repoLname)+"/"+guessedGitInfo.pathInRepo;
            if ((new File(guessedPath)).exists()) {
                guessedGitInfo.ds = Helpers.datasetFromTrig(GlobalHelpers.readFileContent(guessedPath));
                log.info("found git file %s for resource %s", guessedPath, r);
                return guessedGitInfo;
            }
        }
        // if not we fall back to fuseki and look at the adminData
        
        final GitInfo fromFuseki = gitInfoFromFuseki(r);
        if (fromFuseki != null) {
            guessedPath = gitLnameToRepoPath.get(fromFuseki.repoLname)+"/"+fromFuseki.pathInRepo;
            if ((new File(guessedPath)).exists()) {
                fromFuseki.ds = Helpers.datasetFromTrig(GlobalHelpers.readFileContent(guessedPath));
                log.info("found git file %s for resource %s", guessedPath, r);
            } else {
                log.error("found in Fuseki that ", r, " should be on "+guessedPath+" but it's not!");
            }
            return fromFuseki;
        }
        
        // if not, we just return the guess with a null dataset
        
        return guessedGitInfo;
    }
    
    public static String getCommitMessage(final Model newModel, final Resource r) {
        // TODO: read the log entries, etc.
        return "updating "+r.getLocalName();
    }
    
    public static Dataset createDatasetForNewResource(final Model m, final Resource r) {
        // create graph
        // add adm:graphId in admin data
        final String graphUri = Models.BDG+r.getLocalName();
        Dataset ds = DatasetFactory.create();
        m.add(m.createResource(Models.BDA+r.getLocalName()), m.createProperty(Models.ADM, "graphId"), m.createResource(graphUri));
        ds.addNamedModel(graphUri, m);
        return null;
    }
    
    public static Map<String,Repository> lNameToRepoList = new HashMap<>();
    
    public static synchronized Repository getRepository(final String repoLname) {
        if (lNameToRepoList.containsKey(repoLname)) {
            return lNameToRepoList.get(repoLname);
        }
        final String path = gitLnameToRepoPath.get(repoLname);
        FileRepositoryBuilder builder = new FileRepositoryBuilder();
        File gitDir = new File(path + "/.git");
        File wtDir = new File(path);
        try {
            Repository repository = builder.setGitDir(gitDir).setWorkTree(wtDir)
                    .setMustExist( true )
                    .readEnvironment() // scan environment GIT_* variables
                    .build();
            lNameToRepoList.put(repoLname, repository);
            return repository;
        } catch (IOException e) {
            log.error("cannot parse git repo on ", path, e);
            return null;
        }
    }
    
    public static synchronized boolean pushRelevant(final String repoLname) {
        // TODO: push only every 10mn or even every hour
        return true;
    }
    
    public static String getLastRefOfFile(Git git, String pathInRepo) throws GitAPIException {
        Iterator<RevCommit> commits;
        commits = git.log().addPath(pathInRepo).setMaxCount(1).call().iterator();
        if (!commits.hasNext()) {
            log.info("can't find any revision for ", pathInRepo);
            return null;
        }
        return commits.next().getName();
    }
    
    // commits and pushes, and returns the revision name
    public static synchronized void commitAndPush(final GitInfo gi, final String commitMessage) throws IOException, GitAPIException {
        log.info("commit and push ", gi, commitMessage);
        if (EditConfig.dryrunmode) {
            gi.revId = "drymoderev";
            return;
        }
        // write the file
        final String repoPath = gitLnameToRepoPath.get(gi.repoLname);
        final String filePath = repoPath+"/"+gi.pathInRepo;
        final FileOutputStream fos = new FileOutputStream(filePath);
        log.info("write ", filePath);
        datasetToOutputStream(gi.ds, fos);
        // add and commit the file
        Repository r = getRepository(gi.repoLname);
        Git git = new Git(r);
        RevCommit revR = null;
        try {
            final Status status = git.status().addPath(gi.pathInRepo).call();
            if (status.isClean()) {
                log.debug("file hasn't changed, getting the latest revision of the file");
                // kind of a strange case, in which we extract the latest revision
                gi.revId = getLastRefOfFile(git, gi.pathInRepo);
                git.close();
                return;
            }
            git.add().addFilepattern(gi.pathInRepo).call();
            revR = git.commit().setMessage(commitMessage).call();
            gi.revId = revR.getName();
        } catch (GitAPIException e) {
            log.error("could not commit ", gi.pathInRepo, e);
            git.close();
            throw e;
        }
        // push if necessary
        final String remoteUrl = gitLnameToRemoteUrl.get(gi.repoLname);
        // no remote for some repositories, such as users
        if (remoteUrl != null && pushRelevant(gi.repoLname)) {
            try {
                git.push().setCredentialsProvider(gitCredentialProvider).setRemote(remoteUrl).call();
            } catch (GitAPIException e) {
                log.error("unable to push ", gi.repoLname, " to ", remoteUrl, e);
                // not being able to push is bad but shouldn't be blocking everything
            }
        }
        git.close();       
    }
    
    // This saves the new model in git and returns a Fuseki-ready dataset
    public static synchronized GitInfo saveInGit(final Model newModel, final Resource r, final Resource shape)
            throws IOException, VersionConflictException, GitAPIException, ModuleException {
        final GitInfo gi = gitInfoForResource(r);
        Dataset result = null;
        String graphUri;
        if (gi.ds == null) {
            log.info("resource is new");
            // new resource
            gi.ds = createDatasetForNewResource(newModel, r);
            graphUri = Models.BDG+r.getLocalName();
        } else {
            log.info("resource already exists in git");
            result = gi.ds;
            final Resource graph = ModelUtils.getMainGraph(result);
            log.debug("main graph is ", graph);
            graphUri = graph.getURI();
            // next lines changes the result variable directly
            ModelUtils.mergeModel(gi.ds, graphUri, newModel, r, shape, gi.repoLname);            
        }
        // this writes gi.ds in the relevant file, creates a commit, updates gi.revId and pushes if relevant
        commitAndPush(gi, getCommitMessage(newModel, r));
        return gi;
    }
    
    // This saves the new model in git and returns a Fuseki-ready dataset
    public static synchronized GitInfo saveInGit(final RDFPatch patch, final Resource r, final Resource shape)
            throws IOException, VersionConflictException, GitAPIException, ModuleException {
        final GitInfo gi = gitInfoForResource(r);
        Dataset result = null;
        String graphUri;
        if (gi.ds == null) {
            log.info("resource is new");
            // new resource
            gi.ds = createDatasetForNewResource(ModelFactory.createDefaultModel(), r);
            graphUri = Models.BDG+r.getLocalName();
            ModelUtils.mergeModel(gi.ds, graphUri, patch, r, shape, gi.repoLname);
            // TODO: create admin data for users?
        } else {
            log.info("resource already exists in git");
            result = gi.ds;
            final Resource graph = ModelUtils.getMainGraph(result);
            log.debug("main graph is ", graph);
            graphUri = graph.getURI();
            // next lines changes the result variable directly
            ModelUtils.mergeModel(gi.ds, graphUri, patch, r, shape, gi.repoLname);
        }
        // this writes gi.ds in the relevant file, creates a commit, updates gi.revId and pushes if relevant
        commitAndPush(gi, getCommitMessage(null, r));
        return gi;
    }

    private static void datasetToOutputStream(Dataset ds, OutputStream out) throws IOException {
        new STriGWriter().write(out, ds.asDatasetGraph(), EditConfig.prefix.getPrefixMap(), null, Helpers.createWriterContext());
        out.close();
    }
    
}
