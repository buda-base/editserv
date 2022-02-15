package io.bdrc.edit.helpers;

import static io.bdrc.libraries.Models.ADM;
import static io.bdrc.libraries.Models.BDG;
import static io.bdrc.libraries.Models.BDO;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.StringReader;
import java.util.HashMap;
import java.util.List;
import java.util.SortedMap;

import org.apache.jena.graph.Node;
import org.apache.jena.graph.NodeFactory;
import org.apache.jena.graph.Triple;
import org.apache.jena.query.Dataset;
import org.apache.jena.query.DatasetFactory;
import org.apache.jena.query.ReadWrite;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdfconnection.RDFConnectionFuseki;
import org.apache.jena.rdfconnection.RDFConnectionRemoteBuilder;
import org.apache.jena.reasoner.Reasoner;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFDataMgr;
import org.apache.jena.sparql.core.DatasetGraph;
import org.apache.jena.sparql.util.Context;
import org.apache.jena.sparql.util.Symbol;
import org.apache.jena.vocabulary.SKOS;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.TextProgressMonitor;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.bdrc.edit.EditConfig;
import io.bdrc.edit.TransactionLog;
import io.bdrc.jena.sttl.CompareComplex;
import io.bdrc.jena.sttl.ComparePredicates;
import io.bdrc.jena.sttl.STTLWriter;
import io.bdrc.jena.sttl.STriGWriter;
import io.bdrc.libraries.GitHelpers;

public class Helpers {

    public final static Logger log = LoggerFactory.getLogger(Helpers.class.getName());

    public static Context createWriterContext() {
        SortedMap<String, Integer> nsPrio = ComparePredicates.getDefaultNSPriorities();
        nsPrio.put(SKOS.getURI(), 1);
        nsPrio.put("http://purl.bdrc.io/ontology/admin/", 5);
        nsPrio.put("http://purl.bdrc.io/ontology/toberemoved/", 6);
        List<String> predicatesPrio = CompareComplex.getDefaultPropUris();
        predicatesPrio.add(ADM + "logDate");
        predicatesPrio.add(BDO + "seqNum");
        predicatesPrio.add(BDO + "onYear");
        predicatesPrio.add(BDO + "notBefore");
        predicatesPrio.add(BDO + "notAfter");
        predicatesPrio.add(BDO + "noteText");
        predicatesPrio.add(BDO + "noteWork");
        predicatesPrio.add(BDO + "noteLocationStatement");
        predicatesPrio.add(BDO + "volumeNumber");
        predicatesPrio.add(BDO + "eventWho");
        predicatesPrio.add(BDO + "eventWhere");
        Context ctx = new Context();
        ctx.set(Symbol.create(STTLWriter.SYMBOLS_NS + "nsPriorities"), nsPrio);
        ctx.set(Symbol.create(STTLWriter.SYMBOLS_NS + "nsDefaultPriority"), 2);
        ctx.set(Symbol.create(STTLWriter.SYMBOLS_NS + "complexPredicatesPriorities"), predicatesPrio);
        ctx.set(Symbol.create(STTLWriter.SYMBOLS_NS + "indentBase"), 4);
        ctx.set(Symbol.create(STTLWriter.SYMBOLS_NS + "predicateBaseWidth"), 18);
        return ctx;
    }

    public static String getResourceType(String resId, EditPatchHeaders ph) {
        return ph.getResourceType(resId);
    }


    public static DatasetGraph buildGraphFromTrig(String data) {
        Dataset ds = DatasetFactory.create();
        RDFDataMgr.read(ds, new StringReader(data), "", Lang.TRIG);
        return ds.asDatasetGraph();
    }

    public static Dataset datasetFromTrig(final String data) {
        Dataset ds = DatasetFactory.create();
        RDFDataMgr.read(ds, new StringReader(data), "", Lang.TRIG);
        return ds;
    }
    
    public static void putModelWithInference(RDFConnectionFuseki fusConn, String graph, Model m, Reasoner reasoner) {
        fusConn.begin(ReadWrite.WRITE);
        Model mi = ModelFactory.createInfModel(reasoner, m);
        fusConn.put(graph, mi);
        fusConn.commit();
        fusConn.end();
    }

    public static void putModel(RDFConnectionFuseki fusConn, String graph, Model m) {
        fusConn.begin(ReadWrite.WRITE);
        fusConn.put(graph, m);
        fusConn.commit();
        fusConn.end();
    }

    public static void deleteTriples(String graphUri, List<Triple> tps, String fusekiDataUrl) {
        if (fusekiDataUrl == null) {
            fusekiDataUrl = EditConfig.getProperty("fusekiData");
        }
        RDFConnectionRemoteBuilder builder = RDFConnectionFuseki.create().destination(fusekiDataUrl);
        RDFConnectionFuseki fusConn = ((RDFConnectionFuseki) builder.build());
        String query = "DELETE DATA {GRAPH <" + graphUri + "> {";
        for (Triple tp : tps) {
            query = query + tp.getSubject().getURI() + " " + tp.getPredicate().getURI() + " ";
            if (tp.getObject().isURI()) {
                query = query + tp.getObject().getURI() + "  .";
            }
            if (tp.getObject().isLiteral()) {
                query = query + tp.getObject().getLiteralValue().toString() + "  .";
            }
        }
        query = query + "} }";
        fusConn.update(query);
        fusConn.close();
    }

    public static void insertTriples(String graphUri, List<Triple> tps, String fusekiDataUrl) {
        if (fusekiDataUrl == null) {
            fusekiDataUrl = EditConfig.getProperty("fusekiData");
        }
        RDFConnectionRemoteBuilder builder = RDFConnectionFuseki.create().destination(fusekiDataUrl);
        RDFConnectionFuseki fusConn = ((RDFConnectionFuseki) builder.build());
        String query = "INSERT DATA {GRAPH <" + graphUri + "> {";
        for (Triple tp : tps) {
            query = query + tp.getSubject().getURI() + " " + tp.getPredicate().getURI() + " ";
            if (tp.getObject().isURI()) {
                query = query + tp.getObject().getURI() + "  .";
            }
            if (tp.getObject().isLiteral()) {
                query = query + tp.getObject().getLiteralValue().toString() + "  .";
            }
        }
        query = query + "} }";
        fusConn.update(query);
        fusConn.close();
    }

    public static void createDirIfNotExists(String dir) {
        File theDir = new File(dir);
        if (!theDir.exists()) {
            log.info("Directory {} doesn't exist, creting it...",dir);
            try {
                theDir.mkdir();
            } catch (SecurityException se) {
                log.error("Could not create " + dir, se);
            }
        }
        log.info("Directory {} already existing... moving on",dir);
    }

    public static void pullOrCloneUsers() throws GitAPIException, IOException {
        String remoteGit = EditConfig.getProperty("usersRemoteGit");
        String dir = System.getProperty("editserv.configpath") + "users";
        File theDir = new File(dir);
        if (!theDir.exists()) {
            // clone
            Git git = Git.cloneRepository()
                    .setCredentialsProvider(
                            new UsernamePasswordCredentialsProvider(EditConfig.getProperty("gitUser"), EditConfig.getProperty("gitPass")))
                    .setURI(remoteGit).setDirectory(new File(System.getProperty("editserv.configpath") + "users")).call();
            git.close();
        } else {
            // pull
            FileRepositoryBuilder builder = new FileRepositoryBuilder();
            File gitDir = new File(dir + "/.git");
            File wtDir = new File(dir);
            Repository repository = builder.setGitDir(gitDir).setWorkTree(wtDir).readEnvironment() // scan environment GIT_* variables
                    .build();
            if (!repository.getObjectDatabase().exists()) {
                repository.create();
                PrintWriter out = new PrintWriter(dir + ".gitignore");
                out.println(GitHelpers.gitignore);
                out.close();
            }
            Git git = new Git(repository);
            git.pull()
                    .setCredentialsProvider(
                            new UsernamePasswordCredentialsProvider(EditConfig.getProperty("gitUser"), EditConfig.getProperty("gitPass")))
                    .setProgressMonitor(new TextProgressMonitor()).setRemote("origin").call();
            git.close();
        }
        log.info("Users were cloned or pulled from {} into {}",remoteGit,dir);
    }

    public static void modelToOutputStream(Model m, OutputStream out, String resId) throws FileNotFoundException {
        // m = removeGitInfo(m);
        String uriStr = BDG + resId;
        Node graphUri = NodeFactory.createURI(uriStr);
        DatasetGraph dsg = DatasetFactory.create().asDatasetGraph();
        dsg.addGraph(graphUri, m.getGraph());
        new STriGWriter().write(out, dsg, EditConfig.prefix.getPrefixMap(), graphUri.toString(m), Helpers.createWriterContext());
    }

    public static boolean finalizeLog(TransactionLog log, String name) throws JsonProcessingException, IOException {
        File f = new File(log.getPath());
        System.out.println("LOG PATH >> " + log.getPath() + " exist ? =" + f.exists());
        if (!f.exists()) {
            f.mkdir();
        }
        System.out.println("LOG PATH AFTER >> " + log.getPath() + " exist ? =" + f.exists());
        boolean ok = true;
        HashMap<String, HashMap<String, String>> obj = new HashMap<>();
        obj.put(TransactionLog.HEADER, log.header);
        obj.put(TransactionLog.CONTENT, log.content);
        obj.put(TransactionLog.ERROR, log.error);
        ObjectMapper mapper = new ObjectMapper();
        FileOutputStream fos = new FileOutputStream(new File(log.getPath() + name + ".log"));
        mapper.writerWithDefaultPrettyPrinter().writeValue(fos, obj);
        fos.close();
        return ok;
    }

    public static String getShortName(String fullResUri) {
        return fullResUri.substring(fullResUri.lastIndexOf("/") + 1);
    }

    public static void main(String[] args) throws Exception {
        EditConfig.init();
        Helpers.pullOrCloneUsers();
    }

}
