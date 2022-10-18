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
import org.apache.jena.rdf.model.Resource;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.Status;
import org.eclipse.jgit.api.TransportConfigCallback;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.eclipse.jgit.transport.SshTransport;
import org.eclipse.jgit.transport.Transport;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;
import org.eclipse.jgit.transport.sshd.JGitKeyCache;
import org.eclipse.jgit.transport.sshd.SshdSessionFactory;
import org.eclipse.jgit.transport.sshd.SshdSessionFactoryBuilder;
import org.eclipse.jgit.util.FS;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.bdrc.edit.EditConfig;
import io.bdrc.edit.commons.data.FusekiWriteHelpers;
import io.bdrc.edit.commons.data.QueryProcessor;
import io.bdrc.edit.controllers.RIDController;
import io.bdrc.edit.helpers.Helpers;
import io.bdrc.edit.helpers.ModelUtils;
import io.bdrc.edit.txn.exceptions.EditException;
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
    
    public static void init() {
        gitLnameToRepoPath.put("GR0100", EditConfig.getProperty("usersGitLocalRoot"));
        prefixToGitLname.put("U", "GR0100");
        final String normalPath = EditConfig.getProperty("gitLocalRoot");
        final String localSuffix = EditConfig.getProperty("gitLocalSuffix", "");
        gitLnameToRepoPath.put("GR0008", normalPath+"works"+localSuffix);
        gitLnameToRepoPath.put("GR0015", normalPath+"subscribers"+localSuffix);
        gitLnameToRepoPath.put("GR0012", normalPath+"instances"+localSuffix);
        gitLnameToRepoPath.put("GR0011", normalPath+"collections"+localSuffix);
        gitLnameToRepoPath.put("GR0013", normalPath+"einstances"+localSuffix);
        gitLnameToRepoPath.put("GR0003", normalPath+"items"+localSuffix);
        gitLnameToRepoPath.put("GR0014", normalPath+"iinstances"+localSuffix);
        gitLnameToRepoPath.put("GR0006", normalPath+"persons"+localSuffix);
        gitLnameToRepoPath.put("GR0005", normalPath+"places"+localSuffix);
        gitLnameToRepoPath.put("GR0010", normalPath+"roles"+localSuffix);
        gitLnameToRepoPath.put("GR0004", normalPath+"lineages"+localSuffix);
        gitLnameToRepoPath.put("GR0001", normalPath+"corporations"+localSuffix);
        gitLnameToRepoPath.put("GR0007", normalPath+"topics"+localSuffix);
        gitLnameToRepoPath.put("GR0016", normalPath+"outlines"+localSuffix);
        gitLnameToRepoPath.put("GR0100", normalPath+"users"+localSuffix);
        final String normalBase = EditConfig.getProperty("gitRemoteBase");
        final String remoteSuffix = EditConfig.getProperty("gitRemoteSuffix", "");
        gitLnameToRemoteUrl.put("GR0008", normalBase+"works"+remoteSuffix);
        gitLnameToRemoteUrl.put("GR0015", normalBase+"subscribers"+remoteSuffix);
        gitLnameToRemoteUrl.put("GR0012", normalBase+"instances"+remoteSuffix);
        gitLnameToRemoteUrl.put("GR0011", normalBase+"collections"+remoteSuffix);
        gitLnameToRemoteUrl.put("GR0013", normalBase+"einstances"+remoteSuffix);
        gitLnameToRemoteUrl.put("GR0003", normalBase+"items"+remoteSuffix);
        gitLnameToRemoteUrl.put("GR0014", normalBase+"iinstances"+remoteSuffix);
        gitLnameToRemoteUrl.put("GR0006", normalBase+"persons"+remoteSuffix);
        gitLnameToRemoteUrl.put("GR0005", normalBase+"places"+remoteSuffix);
        gitLnameToRemoteUrl.put("GR0010", normalBase+"roles"+remoteSuffix);
        gitLnameToRemoteUrl.put("GR0004", normalBase+"lineages"+remoteSuffix);
        gitLnameToRemoteUrl.put("GR0001", normalBase+"corporations"+remoteSuffix);
        gitLnameToRemoteUrl.put("GR0007", normalBase+"topics"+remoteSuffix);
        gitLnameToRemoteUrl.put("GR0016", normalBase+"outlines"+remoteSuffix);
        gitLnameToRemoteUrl.put("GR0100", normalBase+"users");
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
        prefixToGitLname.put("O", "GR0016");
        prefixToGitLname.put("U", "GR0100");
    }
    
    public static GitInfo gitInfoFromFuseki(final Resource r) {
        log.info("search graph for ", r, " in Fuseki");
        final String query = "select distinct ?g ?repo ?path { graph ?g { <"+r.getURI()+"> a ?t } . ?adm adm:graphId ?g ; adm:gitPath ?path ; adm:gitRepo ?repo . }";
        ResultSet rs = QueryProcessor.getSelectResultSet(query, FusekiWriteHelpers.FusekiSparqlEndpoint);
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
    
    public static boolean resourceExists(final String rLname) throws EditException {
        final String pathInRepo = Models.getMd5(rLname)+"/"+rLname+".trig";
        final String typePrefix = RIDController.getTypePrefix(rLname);
        if (typePrefix == null)
            throw new EditException("unable to find type for "+rLname);
        final String repoLname = prefixToGitLname.get(typePrefix);
        if (repoLname == null)
            throw new EditException("unable to find repo lname for prefix "+typePrefix);
        log.debug("typeprefix {} gitlname {}", typePrefix, repoLname);
        String guessedPath = gitLnameToRepoPath.get(repoLname)+"/"+pathInRepo;
        final boolean res = (new File(guessedPath)).exists();
        log.info("id {} exists in git? {}", rLname, res);
        return res;
    }
    
    // since it uses only the local name, it works for bdr: and bdg: resources
    // (and bdu:, etc.)
    public static GitInfo gitInfoForResource(final Resource r, final boolean gitOnly) throws IOException, EditException {
        final String rLname = r.getLocalName();
        // first we guess and check if the file exists
        final GitInfo guessedGitInfo = new GitInfo();
        guessedGitInfo.pathInRepo = Models.getMd5(rLname)+"/"+rLname+".trig";
        final String typePrefix = RIDController.getTypePrefix(rLname);
        if (typePrefix == null)
            throw new EditException("unable to find type for "+rLname);
        final String repoLname = prefixToGitLname.get(typePrefix);
        if (repoLname == null)
            throw new EditException("unable to find repo lname for prefix "+typePrefix);
        log.debug("typeprefix {} gitlname {}", typePrefix, repoLname);
        guessedGitInfo.repoLname = repoLname;
        String guessedPath = gitLnameToRepoPath.get(repoLname)+"/"+guessedGitInfo.pathInRepo;
        if ((new File(guessedPath)).exists()) {
            guessedGitInfo.ds = Helpers.datasetFromTrig(GlobalHelpers.readFileContent(guessedPath));
            fillLastRev(guessedGitInfo);
            log.info("found git file {} for resource {}", guessedPath, r);
            return guessedGitInfo;
        } else {
            log.info("didn't find git file {} for resource {}", guessedPath, r);
        }
        final int underscore_idx = rLname.indexOf('_');
        if (gitOnly && underscore_idx == -1)
            return guessedGitInfo;
        // if not, we try to take the part before the first underscore
        if (underscore_idx > 0) {
            final String rLname_guess2 = rLname.substring(0, underscore_idx);
            guessedGitInfo.pathInRepo = Models.getMd5(rLname_guess2)+"/"+rLname_guess2+".trig";
            // prefix and thus repoLname don't change in that case
            guessedPath = gitLnameToRepoPath.get(repoLname)+"/"+guessedGitInfo.pathInRepo;
            if ((new File(guessedPath)).exists()) {
                guessedGitInfo.ds = Helpers.datasetFromTrig(GlobalHelpers.readFileContent(guessedPath));
                fillLastRev(guessedGitInfo);
                log.info("found git file %s for resource %s", guessedPath, r);
                return guessedGitInfo;
            }
        }
        
        if (gitOnly)
            return guessedGitInfo;
        
        // if not we fall back to fuseki and look at the adminData
        
        final GitInfo fromFuseki = gitInfoFromFuseki(r);
        if (fromFuseki != null) {
            guessedPath = gitLnameToRepoPath.get(fromFuseki.repoLname)+"/"+fromFuseki.pathInRepo;
            if ((new File(guessedPath)).exists()) {
                fromFuseki.ds = Helpers.datasetFromTrig(GlobalHelpers.readFileContent(guessedPath));
                fillLastRev(fromFuseki);
                log.info("found git file %s for resource %s", guessedPath, r);
            } else {
                log.error("found in Fuseki that ", r, " should be on "+guessedPath+" but it's not!");
            }
            return fromFuseki;
        }
        
        // if not, we just return the guess with a null dataset
        
        return guessedGitInfo;
    }
    
    public static String getCommitMessage(final Resource r, final String[] changeMessage, final Resource user) {
        String res = "";
        if (user != null)
            res = "["+user.getLocalName()+"]";
        return res+"["+r.getLocalName()+"] "+changeMessage[0];
    }
    
    public static Dataset createDatasetForNewResource(final Model m, final Resource r) {
        // create graph
        // add adm:graphId in admin data
        final String graphUri = Models.BDG+r.getLocalName();
        final Dataset ds = DatasetFactory.create(); // createGeneral() ?
        m.add(m.createResource(Models.BDA+r.getLocalName()), m.createProperty(Models.ADM, "graphId"), m.createResource(graphUri));
        ds.addNamedModel(graphUri, m);
        return ds;
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

    public static String getLastRevOfFile(final Git git, final String pathInRepo) throws GitAPIException {
        final Iterator<RevCommit> commits = git.log().addPath(pathInRepo).setMaxCount(1).call().iterator();
        if (!commits.hasNext()) {
            log.info("can't find any revision for ", pathInRepo);
            return null;
        }
        return commits.next().getName();
    }
    
    public static void fillLastRev(final GitInfo gi) throws EditException {
        if (EditConfig.testMode) {
            gi.revId = "testrev";
            return;
        }
        Repository r = getRepository(gi.repoLname);
        Git git = new Git(r);
        Iterator<RevCommit> commits;
        try {
            commits = git.log().addPath(gi.pathInRepo).setMaxCount(1).call().iterator();
        } catch (GitAPIException e) {
            git.close();
            throw new EditException(500, "cannot read last git revision of "+gi.pathInRepo, e);
        }
        if (!commits.hasNext()) {
            git.close();
            throw new EditException(500, "cannot read last git revision of "+gi.pathInRepo);
        }
        gi.revId = commits.next().getName();
        git.close();
    }
    
    public static final File sshDir = new File(FS.DETECTED.userHome(), "/.ssh");
    public static final SshdSessionFactory sshSessionFactory = new SshdSessionFactoryBuilder()
            .setPreferredAuthentications("publickey")
            .setHomeDirectory(FS.DETECTED.userHome())
            .setSshDirectory(sshDir)
            .build(new JGitKeyCache());
    
    public static final class SshTransportConfigCallback implements TransportConfigCallback {

        public SshTransportConfigCallback() { }

        @Override
        public void configure(Transport transport) {
            if (transport instanceof SshTransport) {
                SshTransport sshTransport = (SshTransport) transport;
                sshTransport.setSshSessionFactory(sshSessionFactory);
            }
        }
    }
    
    public static final SshTransportConfigCallback sshTransportConfigCallback = new SshTransportConfigCallback();
    
    // commits and pushes, and returns the revision name
    public static synchronized void commitAndPush(final GitInfo gi, final String commitMessage) throws IOException, GitAPIException {
        if (EditConfig.dryrunmodegit && (EditConfig.dryrunmodeusers || !gi.repoLname.equals("GR0100"))) {
            gi.revId = "drymoderev";
            log.info("dry run: don't commit to git");
            if (log.isDebugEnabled())
            	log.debug(ModelUtils.datasetToTrig(gi.ds));
            return;
        }
        log.info("commit and push {}, message {}", gi, commitMessage);
        // write the file
        final String repoPath = gitLnameToRepoPath.get(gi.repoLname);
        final String filePath = repoPath+"/"+gi.pathInRepo;
        final FileOutputStream fos = new FileOutputStream(filePath);
        log.info("write {}", filePath);
        datasetToOutputStream(gi.ds, fos);
        // add and commit the file
        final Repository r = getRepository(gi.repoLname);
        final Git git = new Git(r);
        RevCommit revR = null;
        try {
            final Status status = git.status().addPath(gi.pathInRepo).call();
            if (status.isClean()) {
                log.debug("file hasn't changed, getting the latest revision of the file");
                // kind of a strange case, in which we extract the latest revision
                gi.revId = getLastRevOfFile(git, gi.pathInRepo);
                git.close();
                return;
            }
            git.add().addFilepattern(gi.pathInRepo).call();
            revR = git.commit().setMessage(commitMessage).call();
            gi.revId = revR.getName();
        } catch (GitAPIException e) {
            log.error("could not commit {}", gi.pathInRepo, e);
            git.close();
            throw e;
        }
        // push if necessary
        final String remoteUrl = gitLnameToRemoteUrl.get(gi.repoLname);
        // no remote for some repositories, such as users
        if (remoteUrl != null && pushRelevant(gi.repoLname)) {
            try {
                git.push().setTransportConfigCallback(sshTransportConfigCallback).setTimeout(10).call();
            } catch (GitAPIException e) {
                log.error("unable to push {} to {}", gi.repoLname, remoteUrl, e);
                // not being able to push is bad but shouldn't be blocking everything
            }
        }
        git.close();       
    }
    
    // This saves the new model in git and returns a Fuseki-ready dataset
    public static synchronized GitInfo saveInGit(final Model newModel, final Resource r, final Resource shape, final String previousRevision, final String[] changeMessage, final Resource user)
            throws IOException, GitAPIException, EditException {
        final GitInfo gi = gitInfoForResource(r, previousRevision == null);
        Dataset result = null;
        String graphUri;
        if (gi.ds == null) {
            if (previousRevision != null)
                throw new EditException(404, "Resource doesn't exist");
            log.info("resource is new");
            // new resource
            if (!ModelUtils.isUser(r))
                ModelUtils.addSimpleLogEntry(newModel, r, user, changeMessage, false);
            gi.ds = createDatasetForNewResource(newModel, r);
            graphUri = Models.BDG+r.getLocalName();
        } else {
            if (previousRevision == null)
                throw new EditException(422, "Resource already exists");
            if (!previousRevision.equals(gi.revId))
                throw new EditException(412, "Previous revision is "+gi.revId+", not "+previousRevision);
            log.info("resource already exists in git");
            result = gi.ds;
            final Resource graph = ModelUtils.getMainGraph(result);
            log.debug("main graph is ", graph);
            graphUri = graph.getURI();
            // next lines changes the result variable directly
            ModelUtils.mergeModel(gi.ds, graphUri, newModel, r, shape, gi.repoLname, changeMessage, user);            
        }
        // this writes gi.ds in the relevant file, creates a commit, updates gi.revId and pushes if relevant
        commitAndPush(gi, getCommitMessage(r, changeMessage, user));
        return gi;
    }

    // This saves the new model in git and returns a Fuseki-ready dataset
    public static synchronized GitInfo forceSaveInGit(final Model newModel, final Resource r, final Resource shape, final String previousRevision, final String[] changeMessage, final Resource user)
            throws IOException, GitAPIException, EditException {
        final GitInfo gi = gitInfoForResource(r, false);
        Dataset result = null;
        String graphUri;
        if (gi.ds == null) {
            if (previousRevision != null)
                throw new EditException(422, "Resource already exists");
            log.info("resource is new");
            // new resource
            gi.ds = createDatasetForNewResource(newModel, r);
            graphUri = Models.BDG+r.getLocalName();
        } else {
            if (previousRevision.equals(gi.revId))
                throw new EditException(412, "Previous revision is "+gi.revId+", not "+previousRevision);
            log.info("resource already exists in git");
            result = gi.ds;
            final Resource graph = ModelUtils.getMainGraph(result);
            log.debug("main graph is ", graph);
            graphUri = graph.getURI();
            // next lines changes the result variable directly
            ModelUtils.mergeModel(gi.ds, graphUri, newModel, r, shape, gi.repoLname, changeMessage, user);            
        }
        // this writes gi.ds in the relevant file, creates a commit, updates gi.revId and pushes if relevant
        commitAndPush(gi, getCommitMessage(r, changeMessage, user));
        return gi;
    }
    
    private static void datasetToOutputStream(Dataset ds, OutputStream out) throws IOException {
        new STriGWriter().write(out, ds.asDatasetGraph(), EditConfig.prefix.getPrefixMap(), null, Helpers.createWriterContext());
        out.close();
    }
    
}
