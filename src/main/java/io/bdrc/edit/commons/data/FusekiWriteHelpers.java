package io.bdrc.edit.commons.data;

import static io.bdrc.libraries.Models.ADM;
import static io.bdrc.libraries.Models.BDG;

import java.net.MalformedURLException;
import java.util.HashMap;
import java.util.Map;

import org.apache.jena.query.Dataset;
import org.apache.jena.query.ReadWrite;
import org.apache.jena.rdf.model.Literal;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.ResIterator;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.ResourceFactory;
import org.apache.jena.rdf.model.Statement;
import org.apache.jena.rdfconnection.RDFConnection;
import org.apache.jena.rdfconnection.RDFConnectionFactory;
import org.apache.jena.rdfconnection.RDFConnectionFuseki;
import org.apache.jena.rdfconnection.RDFConnectionRemoteBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.bdrc.edit.EditConfig;
import io.bdrc.edit.EditConstants;
import io.bdrc.edit.commons.ops.CommonsGit.GitInfo;
import io.bdrc.edit.helpers.ModelUtils;
import io.bdrc.libraries.Models;


public class FusekiWriteHelpers {
    
    public static String FusekiUrl = "http://localhost:13180/fuseki/corerw/data";
    public static String FusekiAuthUrl = "http://localhost:13180/fuseki/authrw/data";
    private static volatile Model syncModel = ModelFactory.createDefaultModel();
    private static volatile boolean syncModelInitialized = false;
    private static volatile Model authSyncModel = ModelFactory.createDefaultModel();
    private static volatile boolean authSyncModelInitialized = false;
    public static String SYSTEM_GRAPH = BDG+"SystemGitSync";
    public static boolean updatingFuseki = true;
    
    public static String FusekiSparqlEndpoint = null;
    public static String FusekiAuthSparqlEndpoint = null;
    public static RDFConnection fuConn = null;
    public static RDFConnection fuAuthConn = null;
    public static Dataset testDataset = null;
    private static RDFConnectionRemoteBuilder fuConnBuilder = null;
    private static RDFConnectionRemoteBuilder fuAuthConnBuilder = null;
    public static String baseUrl = null;
    public static String baseAuthUrl = null;
    public static int initialLoadBulkSize = 50000; // the number of triples above which a dataset load is triggered
    public static boolean addGitRevision = true;
    
    public static final int CORE = 0;
    public static final int AUTH = 1;
    
    public static Logger logger = LoggerFactory.getLogger(FusekiWriteHelpers.class);
    
    private static void initConnectionBuilder() {
        fuConnBuilder = RDFConnectionFuseki.create()
                .destination(baseUrl)
                .queryEndpoint(baseUrl+"/query")
                .gspEndpoint(baseUrl+"/data")
                .updateEndpoint(baseUrl+"/update");
        fuAuthConnBuilder = RDFConnectionFuseki.create()
                .destination(baseAuthUrl)
                .queryEndpoint(baseAuthUrl+"/query")
                .gspEndpoint(baseAuthUrl+"/data")
                .updateEndpoint(baseAuthUrl+"/update");
    }
    
    public static void init(String fusekiHost, String fusekiPort, String fusekiEndpoint, String fusekiAuthEndpoint) throws MalformedURLException {
        baseUrl = "http://" + fusekiHost + ":" +  fusekiPort + "/fuseki/"+fusekiEndpoint;
        baseAuthUrl = "http://" + fusekiHost + ":" +  fusekiPort + "/fuseki/"+fusekiAuthEndpoint;
        FusekiUrl = baseUrl+"/data";
        FusekiAuthUrl = baseAuthUrl+"/data";
        FusekiSparqlEndpoint = baseUrl+"/query";
        FusekiAuthSparqlEndpoint = baseAuthUrl+"/query";
        initConnectionBuilder();
        openConnection(CORE);
        openConnection(AUTH);
    }
    
    public static RDFConnection openConnection(final int distantDB) {
        if (distantDB == CORE) {
            if (fuConn != null) {
                logger.debug("openConnection already connected to fuseki via RDFConnection at "+FusekiUrl);
                return fuConn;
            }
            if (testDataset != null) {
                logger.info("openConnection to fuseki on test dataset");
                fuConn = RDFConnectionFactory.connect(testDataset);
            } else {
                logger.info("openConnection to fuseki via RDFConnection at "+FusekiUrl);
                fuConn = fuConnBuilder.build();
            }
            return fuConn;
        } else {
            if (fuAuthConn != null) {
                logger.debug("openConnection already connected to fuseki via RDFConnection at "+FusekiAuthUrl);
                return fuAuthConn;
            }
            if (testDataset != null) {
                logger.info("openConnection to fuseki on test dataset");
                fuAuthConn = RDFConnectionFactory.connect(testDataset);
            } else {
                logger.info("openConnection to fuseki via RDFConnection at "+FusekiAuthUrl);
                fuAuthConn = fuAuthConnBuilder.build();
            }
            return fuAuthConn;
        }
    }
    
    public static synchronized final Model getSyncModel(final int distantDB) {
        if (distantDB == CORE) {
            if (syncModelInitialized)
                return syncModel;
            initSyncModel(distantDB);
            return syncModel;
        }
        if (authSyncModelInitialized)
            return authSyncModel;
        initSyncModel(distantDB);
        return syncModel;
    }
    
    static Model getModel(String graphName, final int distantDB) {
        logger.info("getModel {}", graphName);
        openConnection(distantDB);
        RDFConnection conn = distantDB == CORE ? fuConn : fuAuthConn;
        try {
            Model model = conn.fetch(graphName);
            logger.info("getModel:" + graphName + "  got model: " + model.size());
            return model; 
        } catch (Exception ex) {
            logger.info("getModel:" + graphName + "  FAILED ");
            return null;
        }
    }
    
    static synchronized void putModel(String graphName, Model model, final int distantDB) {
        if (logger.isDebugEnabled())
            logger.debug(ModelUtils.modelToTtl(model));
        if (EditConfig.dryrunmodefuseki && (EditConfig.dryrunmodeusers || distantDB == CORE)) {
            logger.error("drymode: don't put {} to Fuseki", graphName);
            return;
        }
        logger.info("put {} to Fuseki", graphName);
        openConnection(distantDB);
        RDFConnection conn = distantDB == CORE ? fuConn : fuAuthConn;
        if (!conn.isInTransaction()) {
            conn.begin(ReadWrite.WRITE);
        }
        conn.put(graphName, model);
        conn.commit();
    }
    
    static void addGitInfo(Model m, Resource graph, GitInfo gi) {
        logger.info("add gitinfo for {}", graph);
        ResIterator admIt = m.listSubjectsWithProperty(EditConstants.ADMIN_GRAPH_ID, graph);
        if (!admIt.hasNext()) {
            logger.error("can't find admin data for ", graph.getURI());
            return;
        }
        Resource adm = admIt.next();
        m.add(adm, EditConstants.GIT_PATH, m.createLiteral(gi.pathInRepo));
        m.add(adm, EditConstants.GIT_REPO, m.createResource(Models.BDA+gi.repoLname));
        m.add(adm, EditConstants.GIT_REVISION, m.createLiteral(gi.revId));
        if (logger.isDebugEnabled()) {
            logger.debug(ModelUtils.modelToTtl(m));
        }
    }
    
    static Model getInferredModel(final Model m) {
        logger.info("run reasoner");
        if (OntologyData.Reasoner == null) {
            logger.error("reasoner is null!");
            return m;
        }
        final Model inferredM = ModelFactory.createInfModel(OntologyData.Reasoner, m);
        if (logger.isDebugEnabled()) {
            logger.debug(ModelUtils.modelToTtl(inferredM));
        }
        return inferredM;
    }
    
    static Model prepareForUpload(Model m, Resource graph, GitInfo gi) {
        // adds directly in m
        addGitInfo(m, graph, gi);
        return getInferredModel(m);
    }
    
    public static void putDataset(final GitInfo gi) {
        logger.debug("putDataset {}", gi);
        // two path: the simple case
        if (!gi.repoLname.equals("GR0100")) {
            logger.debug("simple path");
            Resource g = ModelUtils.getMainGraph(gi.ds);
            Model m = gi.ds.getNamedModel(g.getURI());
            m = prepareForUpload(m, g, gi);
            final int dbType = distantDB(gi.repoLname);
            logger.debug("dbType is ", dbType);
            putModel(g.getURI(), m, dbType);
            updateSyncModel(gi.repoLname, gi.revId, dbType);
            return;
        }
        // and the more complex case of users
        // first the public model
        Model m = ModelUtils.getPublicUserModel(gi.ds);
        Resource g = ModelUtils.getPublicUserGraph(gi.ds);
        m = prepareForUpload(m, g, gi);
        putModel(g.getURI(), m, CORE);
        updateSyncModel(gi.repoLname, gi.revId, CORE);
        // then the private one
        m = ModelUtils.getPrivateUserModel(gi.ds);
        g = ModelUtils.getPrivateUserGraph(gi.ds);
        m = prepareForUpload(m, g, gi);
        putModel(g.getURI(), m, AUTH);
        updateSyncModel(gi.repoLname, gi.revId, AUTH);
    }
    
    public static synchronized final void initSyncModel(final int distantDB) {
        if (distantDB == CORE) {
            syncModelInitialized = true;
            logger.info("initSyncModel: " + SYSTEM_GRAPH);
            Model distantSyncModel = getModel(SYSTEM_GRAPH, distantDB);
            if (distantSyncModel != null) {
                syncModel.add(distantSyncModel);
            } else {
                updatingFuseki = false;
            }
        } else {
            authSyncModelInitialized = true;
            logger.info("initSyncModel: {}", SYSTEM_GRAPH);
            Model distantSyncModel = getModel(SYSTEM_GRAPH, distantDB);
            if (distantSyncModel != null) {
                authSyncModel.add(distantSyncModel);
            } else {
                updatingFuseki = false;
            }
        }
    }
    
    public static final Map<String,Resource> repoLnameToSyncModelResource = new HashMap<>();
    static {
        repoLnameToSyncModelResource.put("GR0100", ResourceFactory.createResource(ADM+"GitSyncInfoUser"));
        repoLnameToSyncModelResource.put("GR0008", ResourceFactory.createResource(ADM+"GitSyncInfoWork"));
        repoLnameToSyncModelResource.put("GR0015", ResourceFactory.createResource(ADM+"GitSyncInfoSubscriber"));
        repoLnameToSyncModelResource.put("GR0012", ResourceFactory.createResource(ADM+"GitSyncInfoInstance"));
        repoLnameToSyncModelResource.put("GR0011", ResourceFactory.createResource(ADM+"GitSyncInfoCollection"));
        repoLnameToSyncModelResource.put("GR0013", ResourceFactory.createResource(ADM+"GitSyncInfoEinstance"));
        repoLnameToSyncModelResource.put("GR0003", ResourceFactory.createResource(ADM+"GitSyncInfoItem"));
        repoLnameToSyncModelResource.put("GR0014", ResourceFactory.createResource(ADM+"GitSyncInfoIinstance"));
        repoLnameToSyncModelResource.put("GR0006", ResourceFactory.createResource(ADM+"GitSyncInfoPerson"));
        repoLnameToSyncModelResource.put("GR0005", ResourceFactory.createResource(ADM+"GitSyncInfoPlace"));
        repoLnameToSyncModelResource.put("GR0010", ResourceFactory.createResource(ADM+"GitSyncInfoRole"));
        repoLnameToSyncModelResource.put("GR0004", ResourceFactory.createResource(ADM+"GitSyncInfoLineage"));
        repoLnameToSyncModelResource.put("GR0001", ResourceFactory.createResource(ADM+"GitSyncInfoCorporation"));
        repoLnameToSyncModelResource.put("GR0007", ResourceFactory.createResource(ADM+"GitSyncInfoTopic"));
        
    }
    
    public static final Property hasLastRevision = ResourceFactory.createProperty(ADM, "hasLastRevision");
    
    public static int distantDB(final String repoLname) {
        if (repoLname.equals("GR0100") || repoLname.equals("GR0015"))
            return AUTH;
        return CORE;
    }
    
    public static synchronized void updateSyncModel(final String repoLname, final String revId, final int dbType) {
        if (EditConfig.dryrunmodefuseki) return;
        logger.info("update sync model");
        final Model model = getSyncModel(dbType);
        Resource res = repoLnameToSyncModelResource.get(repoLname);
        Literal lit = model.createLiteral(revId);
        Statement stmt = model.getProperty(res, hasLastRevision);
        if (stmt == null) {
            model.add(res, hasLastRevision, lit);
        } else {
            stmt.changeObject(lit);
        }
        // we put the sync model separately so that things are more consistent
        putModel(SYSTEM_GRAPH, model, dbType);
    }
}
