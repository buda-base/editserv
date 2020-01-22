package io.bdrc.edit.helpers;

import static io.bdrc.libraries.Models.ADM;
import static io.bdrc.libraries.Models.BDA;
import static io.bdrc.libraries.Models.BDG;
import static io.bdrc.libraries.Models.BDO;
import static io.bdrc.libraries.Models.BDR;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.StringReader;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.SortedMap;

import org.apache.jena.graph.Node;
import org.apache.jena.graph.NodeFactory;
import org.apache.jena.query.Dataset;
import org.apache.jena.query.DatasetFactory;
import org.apache.jena.query.ReadWrite;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdfconnection.RDFConnectionFuseki;
import org.apache.jena.reasoner.Reasoner;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFDataMgr;
import org.apache.jena.riot.system.PrefixMap;
import org.apache.jena.riot.system.PrefixMapFactory;
import org.apache.jena.sparql.core.DatasetGraph;
import org.apache.jena.sparql.util.Context;
import org.apache.jena.sparql.util.Symbol;
import org.apache.jena.vocabulary.OWL;
import org.apache.jena.vocabulary.RDF;
import org.apache.jena.vocabulary.RDFS;
import org.apache.jena.vocabulary.SKOS;
import org.apache.jena.vocabulary.VCARD4;
import org.apache.jena.vocabulary.XSD;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.TextProgressMonitor;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.bdrc.edit.EditConfig;
import io.bdrc.jena.sttl.CompareComplex;
import io.bdrc.jena.sttl.ComparePredicates;
import io.bdrc.jena.sttl.STTLWriter;
import io.bdrc.jena.sttl.STriGWriter;
import io.bdrc.libraries.GitHelpers;

public class Helpers {

    public final static Logger log = LoggerFactory.getLogger(Helpers.class.getName());

    public static PrefixMap getPrefixMap() {
        PrefixMap pm = PrefixMapFactory.create();
        pm.add("", BDO);
        pm.add("adm", ADM);
        pm.add("bda", BDA);
        pm.add("bdg", BDG);
        pm.add("bdr", BDR);
        pm.add("owl", OWL.getURI());
        pm.add("rdf", RDF.getURI());
        pm.add("rdfs", RDFS.getURI());
        pm.add("skos", SKOS.getURI());
        pm.add("vcard", VCARD4.getURI());
        pm.add("xsd", XSD.getURI());
        return pm;
    }

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

    public static AdminData fetchAdminInfo(String graphUri, EditPatchHeaders ph) throws NoSuchAlgorithmException {
        String resId = graphUri.substring(graphUri.lastIndexOf("/") + 1);
        AdminData ad = new AdminData(resId, getResourceType(graphUri, ph));
        return ad;
    }

    public static DatasetGraph buildGraphFromTrig(String data) {
        Dataset ds = DatasetFactory.create();
        RDFDataMgr.read(ds, new StringReader(data), "", Lang.TRIG);
        return ds.asDatasetGraph();
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

    public static void pullOrCloneUsers() throws GitAPIException, IOException {
        String remoteGit = EditConfig.getProperty("usersRemoteGit");
        String dir = System.getProperty("editserv.configpath") + "users";
        File theDir = new File(dir);
        if (!theDir.exists()) {
            // clone
            Git git = Git.cloneRepository().setCredentialsProvider(new UsernamePasswordCredentialsProvider(EditConfig.getProperty("gitUser"), EditConfig.getProperty("gitPass"))).setURI(remoteGit)
                    .setDirectory(new File(System.getProperty("editserv.configpath") + "users")).call();
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
            git.pull().setCredentialsProvider(new UsernamePasswordCredentialsProvider(EditConfig.getProperty("gitUser"), EditConfig.getProperty("gitPass"))).setProgressMonitor(new TextProgressMonitor()).setRemote("origin").call();
            git.close();
        }
    }

    public static void modelToOutputStream(Model m, OutputStream out, String resId) throws FileNotFoundException {
        // m = removeGitInfo(m);
        String uriStr = BDG + resId;
        Node graphUri = NodeFactory.createURI(uriStr);
        DatasetGraph dsg = DatasetFactory.create().asDatasetGraph();
        dsg.addGraph(graphUri, m.getGraph());
        new STriGWriter().write(out, dsg, getPrefixMap(), graphUri.toString(m), Helpers.createWriterContext());
    }

    public static void main(String[] args) throws GitAPIException, IOException {
        EditConfig.init();
        Helpers.pullOrCloneUsers();
    }

}
