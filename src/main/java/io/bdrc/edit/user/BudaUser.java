package io.bdrc.edit.user;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.jena.datatypes.xsd.XSDDatatype;
import org.apache.jena.query.Dataset;
import org.apache.jena.query.DatasetFactory;
import org.apache.jena.query.QueryExecution;
import org.apache.jena.query.QueryExecutionFactory;
import org.apache.jena.query.ResultSet;
import org.apache.jena.rdf.model.Literal;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.ResourceFactory;
import org.apache.jena.rdfconnection.RDFConnectionFuseki;
import org.apache.jena.rdfconnection.RDFConnectionRemoteBuilder;
import org.apache.jena.vocabulary.RDF;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.bdrc.auth.AccessInfo;
import io.bdrc.auth.model.User;
import io.bdrc.edit.EditConfig;
import io.bdrc.edit.EditConstants;
import io.bdrc.edit.commons.data.FusekiWriteHelpers;
import io.bdrc.edit.commons.data.QueryProcessor;
import io.bdrc.edit.commons.ops.CommonsGit;
import io.bdrc.edit.commons.ops.CommonsGit.GitInfo;
import io.bdrc.edit.helpers.ModelUtils;
import io.bdrc.edit.txn.exceptions.EditException;
import io.bdrc.libraries.Models;

public class BudaUser {

    public static final String gitignore = "# Ignore everything\n" + "*\n" + "# Don't ignore directories, so we can recurse into them\n" + "!*/\n"
            + "# Don't ignore .gitignore and *.foo files\n" + "!.gitignore\n" + "!*.trig\n" + "";

    public final static Property SKOS_PREF_LABEL = ResourceFactory.createProperty("http://www.w3.org/2004/02/skos/core#prefLabel");

    public final static Logger log = LoggerFactory.getLogger(BudaUser.class);

    public static final String PUBLIC_PROPS_KEY = "publicProps";
    public static final String ADMIN_PROPS_KEY = "adminEditProps";
    public static final String USER_PROPS_KEY = "userEditProps";

    public static String PUB_SCOPE = "public";
    public static String PRIV_SCOPE = "private";

    public static Map<String, List<String>> propsPolicies;
    
    private static final Map<String, Resource> auth0IdToRdfProfile = new HashMap<>();

    // get the bdu:UXXX, surprisingly difficult
    public static Resource getUserFromAccess(final AccessInfo acc) throws EditException {
        final String authId = acc.getId();
        final String auth0Id = authId.substring(authId.lastIndexOf("|") + 1);
        try {
            return getRdfProfile(auth0Id);
        } catch (IOException e) {
            throw new EditException(500, "can't find user profile", e);
        }
    }
    
    public static Resource getRdfProfile(String auth0Id) throws IOException {
        if (auth0IdToRdfProfile.containsKey(auth0Id))
            return auth0IdToRdfProfile.get(auth0Id);
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
            auth0IdToRdfProfile.put(auth0Id, r);
            return r;
        }
        qe.close();
        return null;
    }

    public static Resource getRdfProfile(String auth0Id, Model m) throws IOException {
        if (auth0IdToRdfProfile.containsKey(auth0Id))
            return auth0IdToRdfProfile.get(auth0Id);
        Resource r = null;
        String query = "select distinct ?s where  {  ?s <http://purl.bdrc.io/ontology/ext/user/hasUserProfile> <http://purl.bdrc.io/resource-nc/auth/"
                + auth0Id + "> }";
        log.info("QUERY >> {} and service: {} ", query, EditConfig.getProperty("fusekiAuthData") + "query");
        QueryExecution qe = QueryExecutionFactory.create(query, m);
        log.info("QUERY EXECUTION >> {}", qe);
        ResultSet rs = qe.execSelect();
        log.info("RS {} Has next >> {}", rs, rs.hasNext());
        if (rs.hasNext()) {
            r = rs.next().getResource("?s");
            log.info("RESOURCE >> {} ", r);
            auth0IdToRdfProfile.put(auth0Id, r);
            return r;
        }
        qe.close();
        return null;
    }
    
    public static RDFNode getAuth0IdFromUser(Resource user) throws IOException {
        String query = "select distinct ?o where  {  <" + user.getURI() + "> <http://purl.bdrc.io/ontology/ext/user/hasUserProfile> ?o }";
        log.info("QUERY >> {} and service: {} ", query, FusekiWriteHelpers.FusekiAuthSparqlEndpoint);
        QueryExecution qe = QueryProcessor.getResultSet(query, FusekiWriteHelpers.FusekiAuthSparqlEndpoint);
        ResultSet rs = qe.execSelect();
        if (rs.hasNext()) {
            Resource r = rs.next().getResource("?o");
            log.info("RESOURCE >> {} and rdfId= {} ", r);
            return r;
        }
        return null;
    }

    public static boolean isActive(Resource user) throws IOException {
        String query = "select distinct ?o where  {  <" + user.getURI() + "> <http://purl.bdrc.io/ontology/ext/user/isActive> ?o }";
        log.info("QUERY >> {} and service: {} ", query, FusekiWriteHelpers.FusekiAuthSparqlEndpoint);
        QueryExecution qe = QueryProcessor.getResultSet(query, FusekiWriteHelpers.FusekiAuthSparqlEndpoint);
        ResultSet rs = qe.execSelect();
        if (rs.hasNext()) {
            Literal r = rs.next().getLiteral("?o");
            log.info("RESOURCE >> {} and rdfId= {} ", r);
            return r.getBoolean();
        }
        return false;
    }

    public static Model getUserModelFromFuseki(final Resource user) throws IOException {
        RDFConnectionRemoteBuilder builder = RDFConnectionFuseki.create().destination(FusekiWriteHelpers.baseAuthUrl);
        RDFConnectionFuseki fusConn = ((RDFConnectionFuseki) builder.build());
        final String userLname = user.getLocalName();
        Model fetched = fusConn.fetch(EditConstants.BDGUP + user.getLocalName());
        fusConn.close();
        if (fetched == null)
            return null;
        removeAdminData(fetched, userLname);
        return fetched;
    }

    public static Model getUserModelFromGit(final Resource user) throws IOException, EditException {
        // (wrt second argument) we're not in creation mode but we don't want to look on
        // Fuseki for guidance on where we should store the user profile
        final GitInfo gi = CommonsGit.gitInfoForResource(user, true);
        if (gi.ds == null)
            return null;
        final Model m = ModelUtils.getMainModel(gi.ds);
        removeAdminData(m, user.getLocalName());
        return m;
    }
    
    public static void removeAdminData(Model m, String resLocalName) {
        Resource r = m.getResource("http://purl.bdrc.io/admindata/"+resLocalName);
        m.removeAll(r, null, (RDFNode) null);
        // TODO: remove log entries
    }

    // TODO: move to RIDController?
    public static Resource createUserId(final User usr) throws IOException {
        final String authId = usr.getAuthId();
        int toAdd = 0;
        while (toAdd < 10) {
            final String newId = "U0ES" + Integer.toString(Math.abs(authId.hashCode()+toAdd));
            final Resource user = ResourceFactory.createResource(EditConstants.BDU+newId);
            if (getAuth0IdFromUser(user) == null)
                return user;
            toAdd += 1;
        }
        log.error("couldn't find an available ID after 10 attempts, aborting");
        return null;
    }
    
    public static final SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss");

    // creates a dataset for users who just registered
    // might have its place in io.bdrc.auth.model.User
    public static Dataset createUserDataset(final User usr, final String userLname) {
        log.info("createBudaUserModels for user {}", usr);
        final Dataset res = DatasetFactory.create();
        log.debug("createBudaUserModel for >> {}", userLname);
        final Resource bUser = ResourceFactory.createResource(EditConstants.BDU + userLname);
        final Model privateModel = ModelFactory.createDefaultModel();
        privateModel.setNsPrefixes(EditConfig.prefix.getPrefixMapping());
        privateModel.add(bUser, RDF.type, ResourceFactory.createResource(EditConstants.FOAF + "Person"));
        privateModel.add(bUser, RDF.type, ResourceFactory.createResource(EditConstants.BDOU + "User"));
        privateModel.add(bUser, RDF.type, ResourceFactory.createResource(EditConstants.BDO + "Person"));
        log.info("hasUserProfile in createBudaUserModels = {}", usr.getUserId());
        final String auth0Id = usr.getUserId();
        privateModel.add(bUser, ResourceFactory.createProperty(EditConstants.BDOU + "isActive"), ResourceFactory.createPlainLiteral("true"));
        privateModel.add(bUser, ResourceFactory.createProperty(EditConstants.BDOU + "hasUserProfile"), ResourceFactory.createResource(EditConstants.ADR + auth0Id));
        if (usr.getEmail() != null)
            privateModel.add(bUser, ResourceFactory.createProperty(EditConstants.FOAF + "mbox"), ResourceFactory.createPlainLiteral(usr.getEmail()));
        privateModel.add(bUser, ResourceFactory.createProperty(EditConstants.BDOU + "accountCreation"),
                ResourceFactory.createTypedLiteral(sdf.format(new Date()), XSDDatatype.XSDdateTime));
        privateModel.add(bUser, ResourceFactory.createProperty(EditConstants.BDOU + "preferredUiLang"), ResourceFactory.createPlainLiteral("en"));
        if (usr.getName() != null)
            privateModel.add(bUser, SKOS_PREF_LABEL, ResourceFactory.createPlainLiteral(usr.getName()));
        final Model adminModel = ModelFactory.createDefaultModel();
        Resource adm = ResourceFactory.createResource(EditConstants.BDA + userLname);
        adminModel.add(ResourceFactory.createStatement(adm, RDF.type, EditConstants.ADMIN_DATA));
        adminModel.add(ResourceFactory.createStatement(adm, EditConstants.ADMIN_ABOUT, bUser));
        adminModel.add(ResourceFactory.createStatement(adm, EditConstants.ADMIN_STATUS, EditConstants.STATUS_PROV));
        res.addNamedModel(EditConstants.BDGU+userLname, ModelUtils.publicUserModelFromPrivate(privateModel, bUser));
        res.addNamedModel(EditConstants.BDGUP+userLname, privateModel);
        res.addNamedModel(EditConstants.BDA+userLname, privateModel);
        return res;
    }
    
    public static GitInfo createAndSaveUser(final User usr, final Resource userR) throws IOException, GitAPIException {
        final GitInfo gi = new GitInfo();
        final String rLname = userR.getLocalName();
        gi.pathInRepo = Models.getMd5(rLname)+"/"+rLname+".trig";
        gi.repoLname = "GR0100";
        final String guessedPath = CommonsGit.gitLnameToRepoPath.get("GR0100")+"/"+gi.pathInRepo;
        if ((new File(guessedPath)).exists()) {
            log.error("trying to create new user {} but {} exists!", rLname, guessedPath);
            return null;
        }
        gi.ds = createUserDataset(usr, rLname);
        CommonsGit.commitAndPush(gi, "create new user");
        // revision is in gi.rev
        FusekiWriteHelpers.putDataset(gi);
        return gi;
    }

    public static Map<String, List<String>> getUserPropsEditPolicies() {
        if (propsPolicies == null) {
            propsPolicies = new HashMap<>();
            propsPolicies.put(BudaUser.PUBLIC_PROPS_KEY, Arrays.asList(EditConfig.getProperty(BudaUser.PUBLIC_PROPS_KEY).split(",")));
            propsPolicies.put(BudaUser.ADMIN_PROPS_KEY, Arrays.asList(EditConfig.getProperty(BudaUser.ADMIN_PROPS_KEY).split(",")));
            propsPolicies.put(BudaUser.USER_PROPS_KEY, Arrays.asList(EditConfig.getProperty(BudaUser.USER_PROPS_KEY).split(",")));
        }
        return propsPolicies;
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
                    // TODO: call the right API
                    //updateDataset(s, pubConn, privConn);
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        pubConn.close();
        privConn.close();
    }

    public static void main(String[] args) throws Exception {
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
