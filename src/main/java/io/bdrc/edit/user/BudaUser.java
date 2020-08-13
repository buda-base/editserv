package io.bdrc.edit.user;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.NoSuchAlgorithmException;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.jena.datatypes.xsd.XSDDatatype;
import org.apache.jena.graph.NodeFactory;
import org.apache.jena.query.Dataset;
import org.apache.jena.query.DatasetFactory;
import org.apache.jena.query.QueryExecution;
import org.apache.jena.query.QuerySolution;
import org.apache.jena.query.ResultSet;
import org.apache.jena.rdf.model.Literal;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.ResIterator;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.ResourceFactory;
import org.apache.jena.rdf.model.Statement;
import org.apache.jena.rdfconnection.RDFConnectionFuseki;
import org.apache.jena.rdfconnection.RDFConnectionRemoteBuilder;
import org.apache.jena.sparql.core.DatasetGraph;
import org.apache.jena.vocabulary.RDF;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.AbortedByHookException;
import org.eclipse.jgit.api.errors.ConcurrentRefUpdateException;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.InvalidRemoteException;
import org.eclipse.jgit.api.errors.NoHeadException;
import org.eclipse.jgit.api.errors.NoMessageException;
import org.eclipse.jgit.api.errors.TransportException;
import org.eclipse.jgit.api.errors.UnmergedPathsException;
import org.eclipse.jgit.api.errors.WrongRepositoryStateException;
import org.eclipse.jgit.dircache.DirCache;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;
import org.seaborne.patch.changes.RDFChangesApply;
import org.seaborne.patch.text.RDFPatchReaderText;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.bdrc.auth.AuthProps;
import io.bdrc.auth.model.User;
import io.bdrc.edit.EditConfig;
import io.bdrc.edit.EditConstants;
import io.bdrc.edit.commons.data.QueryProcessor;
import io.bdrc.edit.helpers.AdminData;
import io.bdrc.edit.helpers.Helpers;
import io.bdrc.edit.helpers.UserDataUpdate;
import io.bdrc.edit.txn.exceptions.DataUpdateException;
import io.bdrc.jena.sttl.STriGWriter;
import io.bdrc.libraries.GitHelpers;
import io.bdrc.libraries.GlobalHelpers;
import io.bdrc.libraries.Prefixes;

public class BudaUser {

    public static final String gitignore = "# Ignore everything\n" + "*\n" + "# Don't ignore directories, so we can recurse into them\n" + "!*/\n"
            + "# Don't ignore .gitignore and *.foo files\n" + "!.gitignore\n" + "!*.trig\n" + "";

    public final static Property SKOS_PREF_LABEL = ResourceFactory.createProperty("http://www.w3.org/2004/02/skos/core#prefLabel");

    public final static Logger log = LoggerFactory.getLogger(BudaUser.class);
    // TODO this should come from the auth library
    public static final String PRIVATE_PFX = "http://purl.bdrc.io/graph-nc/user-private/";
    public static final String PUBLIC_PFX = "http://purl.bdrc.io/graph-nc/user/";
    public static final String BDOU_PFX = "http://purl.bdrc.io/ontology/ext/user/";
    public static final String BDU_PFX = "http://purl.bdrc.io/resource-nc/user/";
    public static final String BDA = "http://purl.bdrc.io/admindata/";
    public static final String BDO = "http://purl.bdrc.io/ontology/core/";
    public static final String FOAF = "http://xmlns.com/foaf/0.1/";
    public static final String ADR_PFX = "http://purl.bdrc.io/resource-nc/auth/";

    public static final String PUBLIC_PROPS_KEY = "publicProps";
    public static final String ADMIN_PROPS_KEY = "adminEditProps";
    public static final String USER_PROPS_KEY = "userEditProps";

    public static String PUB_SCOPE = "public";
    public static String PRIV_SCOPE = "private";

    public static HashMap<String, List<String>> propsPolicies;

    public static Resource getRdfProfile(String auth0Id) throws IOException {
        Resource r = null;
        String query = "select distinct ?s where  {  ?s <http://purl.bdrc.io/ontology/ext/user/hasUserProfile> <http://purl.bdrc.io/resource-nc/auth/"
                + auth0Id + "> }";
        log.info("QUERY >> {} and service: {} ", query, EditConfig.getProperty("fusekiAuthData") + "query");
        QueryExecution qe = QueryProcessor.getResultSet(query, EditConfig.getProperty("fusekiAuthData") + "query");
        log.info("QUERY EXECUTION >> {}", qe);
        ResultSet rs = qe.execSelect();
        log.info("RS {} Has next >> {}", rs, rs.hasNext());
        if (rs.hasNext()) {
            r = rs.next().getResource("?s");
            log.info("RESOURCE >> {} ", r);
            return r;
        }
        qe.close();
        return null;
    }

    public static RDFNode getAuth0IdFromUserId(String userId) throws IOException {
        String query = "select distinct ?o where  {  <" + BDU_PFX + userId + "> <http://purl.bdrc.io/ontology/ext/user/hasUserProfile> ?o }";
        log.info("QUERY >> {} and service: {} ", query, EditConfig.getProperty("fusekiAuthData") + "query");
        QueryExecution qe = QueryProcessor.getResultSet(query, EditConfig.getProperty("fusekiAuthData") + "query");
        ResultSet rs = qe.execSelect();
        if (rs.hasNext()) {
            Resource r = rs.next().getResource("?o");
            log.info("RESOURCE >> {} and rdfId= {} ", r);
            return r;
        }
        return null;
    }

    public static boolean isActive(String userId) throws IOException {
        String query = "select distinct ?o where  {  <" + BDU_PFX + userId + "> <http://purl.bdrc.io/ontology/ext/user/isActive> ?o }";
        log.info("QUERY >> {} and service: {} ", query, EditConfig.getProperty("fusekiAuthData") + "query");
        QueryExecution qe = QueryProcessor.getResultSet(query, EditConfig.getProperty("fusekiAuthData") + "query");
        ResultSet rs = qe.execSelect();
        if (rs.hasNext()) {
            Literal r = rs.next().getLiteral("?o");
            log.info("RESOURCE >> {} and rdfId= {} ", r);
            return r.getBoolean();
        }
        return false;
    }

    public static void update(String userId, String patch) throws Exception {
        RDFConnectionRemoteBuilder builder = RDFConnectionFuseki.create().destination(EditConfig.getProperty("fusekiAuthData"));
        RDFConnectionFuseki fusConn = ((RDFConnectionFuseki) builder.build());
        InputStream ptc = new ByteArrayInputStream(patch.getBytes());
        RDFPatchReaderText rdf = new RDFPatchReaderText(ptc);
        Dataset ds = DatasetFactory.create();
        DatasetGraph dsg = ds.asDatasetGraph();
        Model m = fusConn.fetch(PRIVATE_PFX + userId);
        Model pub = fusConn.fetch(PUBLIC_PFX + userId);
        dsg.addGraph(NodeFactory.createURI(PRIVATE_PFX + userId), m.getGraph());
        RDFChangesApply apply = new RDFChangesApply(dsg);
        rdf.apply(apply);
        Model m1 = ModelFactory.createModelForGraph(dsg.getGraph(NodeFactory.createURI(PRIVATE_PFX + userId)));
        Helpers.putModel(fusConn, PRIVATE_PFX + userId, m1);
        ptc.close();
        fusConn.close();
        BudaUser.update(userId, pub, m1);
    }

    public static Model getUserModel(boolean full, Resource r) throws IOException {
        if (r == null) {
            return null;
        }
        RDFConnectionRemoteBuilder builder = RDFConnectionFuseki.create().destination(EditConfig.getProperty("fusekiAuthData"));
        RDFConnectionFuseki fusConn = ((RDFConnectionFuseki) builder.build());
        Model mod = ModelFactory.createDefaultModel();
        String rdfId = r.getURI().substring(r.getURI().lastIndexOf("/") + 1);
        mod.add(fusConn.fetch(PUBLIC_PFX + rdfId));
        if (full) {
            mod.add(fusConn.fetch(PRIVATE_PFX + rdfId));
        }
        fusConn.close();
        return mod;
    }

    public static Model getUserModelFromUserId(boolean full, String resId) throws IOException {
        if (resId == null) {
            return null;
        }
        RDFConnectionRemoteBuilder builder = RDFConnectionFuseki.create().destination(EditConfig.getProperty("fusekiAuthData"));
        RDFConnectionFuseki fusConn = ((RDFConnectionFuseki) builder.build());
        Model mod = ModelFactory.createDefaultModel();
        mod.add(fusConn.fetch(PUBLIC_PFX + resId));
        if (full) {
            mod.add(fusConn.fetch(PRIVATE_PFX + resId));
        }
        fusConn.close();
        return mod;
    }

    public static Model[] createBudaUserModels(User usr) {
        RDFConnectionRemoteBuilder builder = RDFConnectionFuseki.create().destination(EditConfig.getProperty("fusekiAuthData"));
        RDFConnectionFuseki fusConn = ((RDFConnectionFuseki) builder.build());
        log.info("createBudaUserModels for user {}", usr);
        Model[] mods = new Model[2];
        Model publicModel = ModelFactory.createDefaultModel();
        String userId = "U" + Integer.toString(Math.abs(usr.getName().hashCode()));
        log.debug("createBudaUserModel for >> {}", userId);
        Resource bUser = ResourceFactory.createResource(BDU_PFX + userId);
        publicModel.setNsPrefixes(Prefixes.getPrefixMapping());
        publicModel.add(bUser, RDF.type, ResourceFactory.createResource(FOAF + "Person"));
        publicModel.add(bUser, RDF.type, ResourceFactory.createResource(BDOU_PFX + "User"));
        publicModel.add(bUser, RDF.type, ResourceFactory.createResource(BDO + "Person"));
        // TODO there should be some language detection based on the first character:
        // if Chinese, then @zh-hani, if Tibetan then @bo, else no lang tag
        publicModel.add(bUser, SKOS_PREF_LABEL, ResourceFactory.createPlainLiteral(usr.getName()));
        // TODO don't write on System.out
        // for development purpose only
        // publicModel.write(System.out, "TURTLE");
        mods[0] = publicModel;

        Model privateModel = ModelFactory.createDefaultModel();
        privateModel.setNsPrefixes(Prefixes.getPrefixMapping());
        privateModel.add(bUser, RDF.type, ResourceFactory.createResource(FOAF + "Person"));
        privateModel.add(bUser, RDF.type, ResourceFactory.createResource(BDOU_PFX + "User"));
        privateModel.add(bUser, RDF.type, ResourceFactory.createResource(BDO + "Person"));
        log.info("hasUserProfile in createBudaUserModels = {}", usr.getUserId());
        String auth0Id = usr.getUserId();
        privateModel.add(bUser, ResourceFactory.createProperty(BDOU_PFX + "isActive"), ResourceFactory.createPlainLiteral("true"));
        privateModel.add(bUser, ResourceFactory.createProperty(BDOU_PFX + "hasUserProfile"), ResourceFactory.createResource(ADR_PFX + auth0Id));
        privateModel.add(bUser, ResourceFactory.createProperty(FOAF + "mbox"), ResourceFactory.createPlainLiteral(usr.getEmail()));

        mods[0] = publicModel;
        mods[1] = privateModel;
        fusConn.put(PUBLIC_PFX + userId, publicModel);
        fusConn.put(PRIVATE_PFX + userId, publicModel);
        fusConn.close();
        return mods;
    }

    public static boolean isSameUser(User usr, String userResId) throws IOException {
        String auth0Id = usr.getUserId();
        String userAuthId = getAuth0IdFromUserId(userResId).toString();
        userAuthId = userAuthId.substring(userAuthId.lastIndexOf("/") + 1);
        return auth0Id.equals(userAuthId);
    }

    public static Model[] createBudaUserModels(String userName, String usrId, String userEmail) {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss");
        RDFConnectionRemoteBuilder builder = RDFConnectionFuseki.create().destination(EditConfig.getProperty("fusekiAuthData"));
        RDFConnectionFuseki fusConn = ((RDFConnectionFuseki) builder.build());
        log.info("createBudaUserModels for user {}", userName);
        Model[] mods = new Model[2];
        Model publicModel = ModelFactory.createDefaultModel();
        String userId = "U" + Integer.toString(Math.abs(userName.hashCode()));
        log.debug("createBudaUserModel for >> {}", userId);
        Resource bUser = ResourceFactory.createResource(BDU_PFX + userId);
        publicModel.setNsPrefixes(Prefixes.getPrefixMapping());
        publicModel.add(bUser, RDF.type, ResourceFactory.createResource(FOAF + "Person"));
        publicModel.add(bUser, RDF.type, ResourceFactory.createResource(BDOU_PFX + "User"));
        publicModel.add(bUser, RDF.type, ResourceFactory.createResource(BDO + "Person"));
        // TODO there should be some language detection based on the first character:
        // if Chinese, then @zh-hani, if Tibetan then @bo, else no lang tag
        publicModel.add(bUser, SKOS_PREF_LABEL, ResourceFactory.createPlainLiteral(userName));
        // TODO don't write on System.out
        // for development purpose only
        publicModel.write(System.out, "TURTLE");
        mods[0] = publicModel;

        Model privateModel = ModelFactory.createDefaultModel();
        privateModel.setNsPrefixes(Prefixes.getPrefixMapping());
        privateModel.add(bUser, RDF.type, ResourceFactory.createResource(FOAF + "Person"));
        privateModel.add(bUser, RDF.type, ResourceFactory.createResource(BDOU_PFX + "User"));
        privateModel.add(bUser, RDF.type, ResourceFactory.createResource(BDO + "Person"));
        log.info("hasUserProfile in createBudaUserModels = {}", userId);
        String auth0Id = usrId;
        privateModel.add(bUser, ResourceFactory.createProperty(BDOU_PFX + "isActive"), ResourceFactory.createPlainLiteral("true"));
        privateModel.add(bUser, ResourceFactory.createProperty(BDOU_PFX + "hasUserProfile"), ResourceFactory.createResource(ADR_PFX + auth0Id));
        privateModel.add(bUser, ResourceFactory.createProperty(FOAF + "mbox"), ResourceFactory.createPlainLiteral(userEmail));
        privateModel.add(bUser, ResourceFactory.createProperty(BDOU_PFX + "accountCreation"),
                ResourceFactory.createTypedLiteral(sdf.format(new Date()), XSDDatatype.XSDdateTime));
        privateModel.add(bUser, ResourceFactory.createProperty(BDOU_PFX + "preferredLangTags"), ResourceFactory.createPlainLiteral("eng"));
        privateModel.add(bUser, SKOS_PREF_LABEL, ResourceFactory.createPlainLiteral(userName));

        mods[0] = publicModel;
        mods[1] = privateModel;
        fusConn.put(PUBLIC_PFX + userId, publicModel);
        fusConn.put(PRIVATE_PFX + userId, publicModel);
        fusConn.close();
        return mods;
    }

    public static HashMap<String, List<String>> getUserPropsEditPolicies() {
        if (propsPolicies == null) {
            propsPolicies = new HashMap<>();
            propsPolicies.put(BudaUser.PUBLIC_PROPS_KEY, Arrays.asList(EditConfig.getProperty(BudaUser.PUBLIC_PROPS_KEY).split(",")));
            propsPolicies.put(BudaUser.ADMIN_PROPS_KEY, Arrays.asList(EditConfig.getProperty(BudaUser.ADMIN_PROPS_KEY).split(",")));
            propsPolicies.put(BudaUser.USER_PROPS_KEY, Arrays.asList(EditConfig.getProperty(BudaUser.USER_PROPS_KEY).split(",")));
        }
        return propsPolicies;
    }

    public static void addNewBudaUser(User user) throws GitAPIException, IOException, NoSuchAlgorithmException {
        long start = System.currentTimeMillis();
        pullUserRepoIfRelevant();
        long start1 = System.currentTimeMillis();
        log.info("Pulling users repo took {} ms", (start1 - start));
        Model[] mod = BudaUser.createBudaUserModels(user);
        long start2 = System.currentTimeMillis();
        log.info("Creating buda users models took {} ms", (start2 - start1));
        Model pub = mod[0];
        Model priv = mod[1];
        String userId = null;
        ResIterator rit = priv.listSubjectsWithProperty(ResourceFactory.createProperty(BudaUser.BDOU_PFX + "hasUserProfile"));
        if (rit.hasNext()) {
            Resource r = rit.next();
            userId = r.getLocalName();
        } else {
            log.error("Invalid user model for {}", user);
            // return null;
        }
        GitBudaUserCreate gitTask = new GitBudaUserCreate(userId, pub, priv, user.getName());
        Thread t = new Thread(gitTask);
        t.start();
        String bucket = GlobalHelpers.getTwoLettersBucket(userId);
        AdminData ad = new AdminData(userId, AdminData.USER_RES_TYPE, bucket + "/" + userId + ".trig");
        Model adm = ad.asModel();
        RDFConnectionRemoteBuilder builder = RDFConnectionFuseki.create().destination(EditConfig.getProperty("fusekiAuthData"));
        RDFConnectionFuseki fusConn = ((RDFConnectionFuseki) builder.build());
        Helpers.putModel(fusConn, BudaUser.PRIVATE_PFX + userId, priv);
        fusConn.close();
        // Public graph is pushed to bdrcrw
        builder = RDFConnectionFuseki.create().destination(EditConfig.getProperty("fusekiUrl").replace("query", ""));
        fusConn = ((RDFConnectionFuseki) builder.build());
        Helpers.putModel(fusConn, BudaUser.PUBLIC_PFX + userId, pub);
        // adding adminData graph to public dataset
        Helpers.putModel(fusConn, EditConstants.BDA + userId, adm);
        fusConn.close();
    }

    public static Repository ensureUserGitRepo() {
        Repository repository = null;
        String dirpath = EditConfig.getProperty("usersGitLocalRoot");
        Helpers.createDirIfNotExists(dirpath);
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
    
    public static void pushUserRepo() throws InvalidRemoteException, TransportException, GitAPIException {
        Repository r = ensureUserGitRepo();
        Git git = new Git(r);
        git.push()
        .setCredentialsProvider(
                new UsernamePasswordCredentialsProvider(EditConfig.getProperty("gitUser"), EditConfig.getProperty("gitPass")))
        .setRemote(EditConfig.getProperty("usersRemoteGit")).call();
        git.close();
    }
    
    public static final int pushEveryS = 600; // push every 600 seconds
    public static boolean pushScheduled = false;
    public static ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    
    public static void pushUserRepoIfRelevant() {
        if (pushScheduled)
            return;
        pushScheduled = true;
        Runnable task = new Runnable() {
            public void run() {
                try {
                    pushUserRepo();
                    pushScheduled = false;
                } catch (GitAPIException e) {
                    log.error("error pushing to users repo", e);
                }
            }
        };
        scheduler.schedule(task, pushEveryS, TimeUnit.SECONDS);
        scheduler.shutdown();
    }
    
    public static final int pullEveryS = 6000; // pull every x seconds
    public static Instant lastPull = null;
    
    public static void pullUserRepoIfRelevant() throws GitAPIException, IOException {
        final Instant now = Instant.now();
        if (lastPull == null || lastPull.isBefore(now.minusSeconds(pullEveryS))) {
            Helpers.pullOrCloneUsers();
        }
        lastPull = now;
    }

    public static RevCommit update(UserDataUpdate data) throws IOException, NoSuchAlgorithmException, NoHeadException, NoMessageException,
            UnmergedPathsException, ConcurrentRefUpdateException, WrongRepositoryStateException, AbortedByHookException, GitAPIException {
        final RevCommit rev = update(data.getUserId(), data.getDatasetGraph());
        data.setGitRevisionInfo(rev.getName());
        return rev;
    }

    public static synchronized RevCommit update(final String userId, final DatasetGraph dsg) throws IOException, NoSuchAlgorithmException, NoHeadException, NoMessageException,
    UnmergedPathsException, ConcurrentRefUpdateException, WrongRepositoryStateException, AbortedByHookException, GitAPIException {
        pullUserRepoIfRelevant();
        RevCommit rev = null;
        final String dirpath = EditConfig.getProperty("usersGitLocalRoot");
        final String bucket = GlobalHelpers.getTwoLettersBucket(userId);
        Helpers.createDirIfNotExists(dirpath + bucket + "/");
        final String gitPath = bucket + "/" + userId + ".trig";
        final FileOutputStream fos = new FileOutputStream(dirpath + gitPath);
        new STriGWriter().write(fos, dsg, Prefixes.getPrefixMap(), "", GlobalHelpers.createWriterContext());
        Repository r = ensureUserGitRepo();
        Git git = new Git(r);
        if (!git.status().call().isClean()) {
            git.add().addFilepattern(gitPath).call();
            rev = git.commit().setMessage("User " + userId + " was updated" + Calendar.getInstance().getTime()).call();
        }
        git.close();
        pushUserRepoIfRelevant();
        return rev;
    }
    
    public static RevCommit update(String userId, Model pub, Model priv)
            throws IOException, NoSuchAlgorithmException, NoHeadException, NoMessageException, UnmergedPathsException, ConcurrentRefUpdateException,
            WrongRepositoryStateException, AbortedByHookException, GitAPIException {
        DatasetGraph dsg = DatasetFactory.create().asDatasetGraph();
        dsg.addGraph(ResourceFactory.createResource(BudaUser.PUBLIC_PFX + userId).asNode(), pub.getGraph());
        dsg.addGraph(ResourceFactory.createResource(BudaUser.PRIVATE_PFX + userId).asNode(), priv.getGraph());
        return update(userId, dsg);
    }

    public static void deleteBudaUser(String userid, boolean deleteGit) throws DataUpdateException {
        try {
            Helpers.pullOrCloneUsers();
            QueryProcessor.dropGraph("http://purl.bdrc.io/admindata/" + userid, EditConfig.getProperty("fusekiData"));
            QueryProcessor.dropGraph("http://purl.bdrc.io/admindata/" + userid, EditConfig.getProperty("fusekiAuthData"));
            QueryProcessor.dropGraph("http://purl.bdrc.io/graph-nc/user/" + userid, EditConfig.getProperty("fusekiData"));
            QueryProcessor.dropGraph("http://purl.bdrc.io/graph-nc/user/" + userid, EditConfig.getProperty("fusekiAuthData"));
            QueryProcessor.dropGraph("http://purl.bdrc.io/graph-nc/user-private/" + userid, EditConfig.getProperty("fusekiAuthData"));
            if (deleteGit) {
                String dirpath = EditConfig.getProperty("usersGitLocalRoot");
                String bucket = GlobalHelpers.getTwoLettersBucket(userid);
                Helpers.createDirIfNotExists(dirpath + bucket + "/");
                File f = new File(dirpath + bucket + "/" + userid + ".trig");
                f.delete();
                Repository r = null;
                if (r == null) {
                    r = ensureUserGitRepo();
                }
                Git git = new Git(r);
                git.add().addFilepattern(".").call();
                git.commit().setMessage("User " + userid + " was deleted").call();
                git.push()
                        .setCredentialsProvider(
                                new UsernamePasswordCredentialsProvider(EditConfig.getProperty("gitUser"), EditConfig.getProperty("gitPass")))
                        .setRemote(EditConfig.getProperty("usersRemoteGit")).call();
                git.close();
            }
        } catch (Exception ex) {
            log.error("BudaUser deleteBudaUser failed ", ex);
        }
    }

    private static ArrayList<String> getUserIds() {
        ArrayList<String> users = new ArrayList<>();
        String query = "select distinct ?s ?p ?o where  {  ?s a <http://purl.bdrc.io/ontology/ext/user/User> }";
        ResultSet set = QueryProcessor.getSelectResultSet(query, null);
        while (set.hasNext()) {
            QuerySolution qs = set.next();
            String tmp = qs.get("?s").asNode().getLocalName();
            users.add(tmp);
        }
        return users;
    }

    public static void rebuiltFuseki() {
        RDFConnectionRemoteBuilder pubBuilder = RDFConnectionFuseki.create().destination(EditConfig.getProperty("fusekiData"));
        RDFConnectionFuseki pubConn = ((RDFConnectionFuseki) pubBuilder.build());
        RDFConnectionRemoteBuilder privBuilder = RDFConnectionFuseki.create().destination(EditConfig.getProperty("fusekiAuthData"));
        RDFConnectionFuseki privConn = ((RDFConnectionFuseki) privBuilder.build());
        File[] files = new File(EditConfig.getProperty("usersGitLocalRoot")).listFiles(File::isDirectory);
        for (File file : files) {
            try (Stream<Path> walk = Files.walk(Paths.get(file.getAbsoluteFile().getAbsolutePath()))) {
                List<String> result = walk.map(x -> x.toString()).filter(f -> f.endsWith(".trig")).collect(Collectors.toList());
                for (String s : result) {
                    updateDataset(s, pubConn, privConn);
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        pubConn.close();
        privConn.close();
    }

    private static void updateDataset(String trigFilename, RDFConnectionFuseki pubConn, RDFConnectionFuseki privConn) throws IOException {
        Repository repository = BudaUser.ensureUserGitRepo();
        RevWalk revwalk = new RevWalk(repository);
        ObjectId lastCommitId = repository.resolve(Constants.HEAD);
        RevCommit commit = revwalk.parseCommit(lastCommitId);
        revwalk.close();
        String version = commit.getName();
        String resId = trigFilename.substring(trigFilename.lastIndexOf("/") + 1);
        resId = resId.substring(0, resId.lastIndexOf("."));
        DatasetGraph dsg = Helpers.buildGraphFromTrig(GlobalHelpers.readFileContent(trigFilename));
        Model pub = ModelFactory.createModelForGraph(dsg.getGraph(ResourceFactory.createResource(BudaUser.PUBLIC_PFX + resId).asNode()));
        Model priv = ModelFactory.createModelForGraph(dsg.getGraph(ResourceFactory.createResource(BudaUser.PRIVATE_PFX + resId).asNode()));
        Model adm = ModelFactory.createModelForGraph(dsg.getGraph(ResourceFactory.createResource(BudaUser.BDA + resId).asNode()));
        Resource r = ResourceFactory.createResource(BudaUser.BDA + resId);
        Statement st = ResourceFactory.createStatement(r, ResourceFactory.createProperty("http://purl.bdrc.io/ontology/admin/gitRevision"),
                ResourceFactory.createPlainLiteral(version));
        adm.add(st);
        Helpers.putModel(pubConn, BudaUser.PUBLIC_PFX + resId, pub);
        Helpers.putModel(pubConn, BudaUser.BDA + resId, adm);
        Helpers.putModel(privConn, BudaUser.BDA + resId, adm);
        Helpers.putModel(privConn, BudaUser.PRIVATE_PFX + resId, priv);
    }

    public static void main(String[] args) throws IOException, DataUpdateException {
        EditConfig.init();
        BudaUser.rebuiltFuseki();
        // RdfAuthModel.initForTest(false, true);
        // BudaUser.getUserIds();
        // BudaUser.deleteBudaUser("U678062094", true);
        // System.out.println(BudaUser.createBudaUserModels("Nicolas Berger",
        // "103776618189565648628", "quai.ledrurollin@gmail.com")[0]);
        // System.out.println(BudaUser.createBudaUserModels("Nicolas Berger",
        // "103776618189565648628", "quai.ledrurollin@gmail.com")[1]);
    }

}
