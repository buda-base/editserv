package io.bdrc.edit.service;

import static io.bdrc.libraries.Models.ADM;
import static io.bdrc.libraries.Models.BDA;
import static io.bdrc.libraries.Models.BDG;
import static io.bdrc.libraries.Models.BDO;
import static io.bdrc.libraries.Models.BDR;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.util.List;
import java.util.SortedMap;

import org.apache.jena.graph.Node;
import org.apache.jena.graph.NodeFactory;
import org.apache.jena.query.DatasetFactory;
import org.apache.jena.rdf.model.Model;
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
import org.eclipse.jgit.api.errors.InvalidRemoteException;
import org.eclipse.jgit.api.errors.TransportException;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.TextProgressMonitor;
import org.eclipse.jgit.revwalk.RevCommit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.bdrc.edit.EditConfig;
import io.bdrc.edit.helpers.AdminData;
import io.bdrc.edit.helpers.DataUpdate;
import io.bdrc.edit.txn.exceptions.GitServiceException;
import io.bdrc.edit.txn.exceptions.ServiceException;
import io.bdrc.jena.sttl.CompareComplex;
import io.bdrc.jena.sttl.ComparePredicates;
import io.bdrc.jena.sttl.STTLWriter;
import io.bdrc.jena.sttl.STriGWriter;
import io.bdrc.libraries.GitHelpers;

public class GitPatchService implements BUDAEditService {

    public final static Logger log = LoggerFactory.getLogger(GitPatchService.class.getName());

    int status;
    String id;
    String userId;
    DataUpdate data;
    List<String> create;
    List<String> graphs;
    Context writerContext;
    static Repository localRepo;
    static String remoteURL;

    public GitPatchService(DataUpdate data) {
        this.data = data;
        this.id = "GIT_" + data.getTaskId();
        this.userId = data.getUserId();
        this.create = data.getCreate();
        this.graphs = data.getGraphs();
        this.writerContext = createWriterContext();
        // log.logMsg("GIT Service " + id + " entered status ",
        // Types.getSvcStatus(Types.SVC_STATUS_READY));
    }

    /**
     * Run the service and logs execution.
     * 
     * @throws GitAPIException
     * @throws TransportException
     * @throws InvalidRemoteException
     * 
     * @throws                        @throws IOException
     */
    public void run() throws GitServiceException {
        log.info("Running Git Patch Service for task {}", data.getTaskId());
        // First: processing the existing graphs being updated
        for (String g : graphs) {
            String resType = data.getResourceType(g);
            AdminData adm = data.getGitInfo(g);
            System.out.println("Admin DATA >>" + adm + " graph=" + g);
            GitHelpers.ensureGitRepo(resType, EditConfig.getProperty("gitLocalRoot"));
            FileOutputStream fos = null;
            try {
                fos = new FileOutputStream(EditConfig.getProperty("gitLocalRoot") + adm.getGitRepo().getGitRepoName() + "/" + adm.getGitPath());
                modelToOutputStream(data.getModelByUri(g), fos, g.substring(g.lastIndexOf("/") + 1));
                RevCommit rev = GitHelpers.commitChanges(resType, "Commit message at " + System.currentTimeMillis());
                data.addGitRevisionInfo(g, rev.getName());
                System.out.println("GIT USER >>" + EditConfig.getProperty("gitUser"));
                System.out.println("GIT PASS >>" + EditConfig.getProperty("gitPass"));
                // GitHelpers.push(resType, EditConfig.getProperty("gitRemoteBase"),
                // EditConfig.getProperty("gitUser"), EditConfig.getProperty("gitUser"),
                // EditConfig.getProperty("gitLocalRoot"));
                GitHelpers.push(resType, EditConfig.getProperty("gitRemoteBase"), "magate", "tchame2011", EditConfig.getProperty("gitLocalRoot"));

            } catch (FileNotFoundException | GitAPIException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
                throw new GitServiceException(e);
            }
        }

        // second: new resources, created graphs
        for (String c : create) {
            String resType = data.getResourceType(c);
            AdminData adm = data.getGitInfo(c);
            System.out.println("Admin DATA >>" + adm + " graph=" + c);
            GitHelpers.ensureGitRepo(resType, EditConfig.getProperty("gitLocalRoot"));
            FileOutputStream fos = null;
            try {
                fos = new FileOutputStream(EditConfig.getProperty("gitLocalRoot") + adm.getGitRepo().getGitRepoName() + "/" + adm.getGitPath());
                modelToOutputStream(data.getModelByUri(c), fos, c.substring(c.lastIndexOf("/") + 1));
                RevCommit rev = GitHelpers.commitChanges(resType, "Commit message at " + System.currentTimeMillis());
                data.addGitRevisionInfo(c, rev.getName());
                GitHelpers.push(resType, EditConfig.getProperty("gitRemoteBase"), EditConfig.getProperty("gitUser"), EditConfig.getProperty("gitUser"), EditConfig.getProperty("gitLocalRoot"));
            } catch (FileNotFoundException | GitAPIException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
                throw new GitServiceException(e);
            }
        }
    }

    public void modelToOutputStream(Model m, OutputStream out, String resId) throws FileNotFoundException {
        String uriStr = BDG + resId;
        Node graphUri = NodeFactory.createURI(uriStr);
        DatasetGraph dsg = DatasetFactory.create().asDatasetGraph();
        dsg.addGraph(graphUri, m.getGraph());
        new STriGWriter().write(out, dsg, getPrefixMap(), graphUri.toString(m), writerContext);
    }

    private Context createWriterContext() {
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

    public boolean rollback() throws ServiceException {
        try {
            // 1) Pull remote directory
            Git git = new Git(localRepo);
            git.pull().setProgressMonitor(new TextProgressMonitor()).setRemote("origin").call();
            /*
             * switch (type) { case Types.GIT_CREATE_SVC: // 2) delete the resource ttl file
             * thats was created before String dir = EditConfig.getProperty("gitLocalRoot")
             * + resType + "s/";
             * System.out.println("Running GitService... in create SVC with dir : " + dir);
             * String sub = getDir(data.getResId());
             * System.out.println("Running GitService... dir is.. " + dir + " and sub is: "
             * + sub); dir = dir + sub + "/"; File f = new File(dir + data.getResId() +
             * ".ttl"); boolean del = f.delete(); // 3) add and commit to local
             * git.add().addFilepattern(sub + "/").call();
             * git.commit().setMessage("Rolling back resource " + data.getResId()).call();
             * // 4) push to remote git.push().setCredentialsProvider(new
             * UsernamePasswordCredentialsProvider(EditConfig.getProperty("gitUser"),
             * EditConfig.getProperty("gitPassword"))).setRemote(remoteURL).call(); break; }
             */
            git.close();
        } catch (GitAPIException e) {
            e.printStackTrace();
            throw new GitServiceException(e);
        }
        return false;
    }

    @Override
    public int getStatus() {
        return status;
    }

    @Override
    public void setStatus(int st) {
        status = st;
    }

    @Override
    public String getId() {
        return id;
    }

    @Override
    public String getName() {
        return "GIT Service";
    }

    @Override
    public String getUserId() {
        // TODO Auto-generated method stub
        return null;
    }

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

}
