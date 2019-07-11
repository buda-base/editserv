package io.bdrc.edit.modules;

import static io.bdrc.libraries.Models.ADM;
import static io.bdrc.libraries.Models.BDA;
import static io.bdrc.libraries.Models.BDG;
import static io.bdrc.libraries.Models.BDO;
import static io.bdrc.libraries.Models.BDR;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.util.List;
import java.util.SortedMap;

import org.apache.jena.graph.Node;
import org.apache.jena.graph.NodeFactory;
import org.apache.jena.query.DatasetFactory;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
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
import io.bdrc.edit.Types;
import io.bdrc.edit.helpers.AdminData;
import io.bdrc.edit.helpers.DataUpdate;
import io.bdrc.edit.sparql.QueryProcessor;
import io.bdrc.edit.txn.TransactionLog;
import io.bdrc.edit.txn.exceptions.GitServiceException;
import io.bdrc.edit.txn.exceptions.ServiceException;
import io.bdrc.jena.sttl.CompareComplex;
import io.bdrc.jena.sttl.ComparePredicates;
import io.bdrc.jena.sttl.STTLWriter;
import io.bdrc.jena.sttl.STriGWriter;
import io.bdrc.libraries.GitHelpers;

public class GitPatchModule implements BUDAEditModule {

    public final static Logger logger = LoggerFactory.getLogger(GitPatchModule.class.getName());

    int status;
    String name;
    DataUpdate data;
    List<String> create;
    List<String> graphs;
    List<String> delete;
    Context writerContext;
    static Repository localRepo;
    static String remoteURL;
    TransactionLog log;

    public GitPatchModule(DataUpdate data, TransactionLog log) {
        this.data = data;
        this.name = "GIT_PATCH_MOD" + data.getTaskId();
        this.create = data.getCreate();
        this.graphs = data.getGraphs();
        this.delete = data.getDelete();
        this.writerContext = createWriterContext();
        this.log = log;
        setStatus(Types.STATUS_PREPARED);
        log.addContent(name, name + " entered " + Types.getStatus(status));
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
        logger.info("Running Git Patch Service for task {}", data.getTaskId());
        String gitUser = EditConfig.getProperty("gitUser");
        String gitPass = EditConfig.getProperty("gitPass");
        // First: processing the existing graphs being updated
        processUpdates(gitUser, gitPass);
        // second: graphs to be deleted
        processDeletes(gitUser, gitPass);
        // third: new resources, created graphs
        processCreates(gitUser, gitPass);

    }

    private void processDeletes(String gitUser, String gitPass) throws GitServiceException {
        try {
            for (String d : delete) {
                String resId = d.substring(d.lastIndexOf("/") + 1);
                QueryProcessor.dropGraph(d);
                String resType = data.getResourceType(d);
                AdminData adm = data.getAdminData(d);
                System.out.println("ADM Data >>" + adm);
                GitHelpers.ensureGitRepo(resType, EditConfig.getProperty("gitLocalRoot"));
                File f = null;
                String deletePath = EditConfig.getProperty("gitLocalRoot") + adm.getGitRepo().getGitRepoName() + "/" + adm.getGitPath() + "/" + resId + ".trig";
                System.out.println("Delete path =" + deletePath);
                boolean delete = new File(deletePath).delete();
                System.out.println("Running GitService... deleting process returned : " + delete);
                GitHelpers.commitDelete(resType, adm.getGitPath() + "/" + resId + ".trig", resId + " deleted by " + data.getUserId());
                GitHelpers.push(resType, EditConfig.getProperty("gitRemoteBase"), gitUser, gitPass, EditConfig.getProperty("gitLocalRoot"));
            }
        } catch (Exception e) {
            e.printStackTrace();
            throw new GitServiceException(e);
        }
    }

    private void processUpdates(String gitUser, String gitPass) throws GitServiceException {

        for (String g : graphs) {
            System.out.println("GRAPH DATA >>" + g);
            String resType = data.getResourceType(g);
            AdminData adm = data.getAdminData(g);
            System.out.println("ADM Data >>" + adm);
            GitHelpers.ensureGitRepo(resType, EditConfig.getProperty("gitLocalRoot"));
            FileOutputStream fos = null;
            try {
                String resId = g.substring(g.lastIndexOf("/") + 1);
                fos = new FileOutputStream(EditConfig.getProperty("gitLocalRoot") + adm.getGitRepo().getGitRepoName() + "/" + adm.getGitPath());
                System.out.println("GIT WRITE PATH >>" + EditConfig.getProperty("gitLocalRoot") + adm.getGitRepo().getGitRepoName() + "/" + adm.getGitPath());
                Model to_write = ModelFactory.createModelForGraph(data.getDatasetGraph().getGraph(NodeFactory.createURI(g)));
                System.out.println("MODEL SIZE >>" + to_write.size());
                // to_write.write(System.out, "TURTLE");
                modelToOutputStream(to_write, fos, resId + ".trig");
                RevCommit rev = GitHelpers.commitChanges(resType, "Committed by " + getUserId() + " for task:" + data.getTaskId());
                data.addGitRevisionInfo(g, rev.getName());
                GitHelpers.push(resType, EditConfig.getProperty("gitRemoteBase"), gitUser, gitPass, EditConfig.getProperty("gitLocalRoot"));

            } catch (FileNotFoundException | GitAPIException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
                throw new GitServiceException(e);
            }
        }
    }

    private void processCreates(String gitUser, String gitPass) throws GitServiceException {
        for (String c : create) {
            String resType = data.getResourceType(c);
            AdminData adm = data.getAdminData(c);
            System.out.println("Admin DATA >>" + adm + " graph=" + c);
            GitHelpers.ensureGitRepo(resType, EditConfig.getProperty("gitLocalRoot"));
            FileOutputStream fos = null;
            try {
                String resId = c.substring(c.lastIndexOf("/") + 1);
                File file = new File(EditConfig.getProperty("gitLocalRoot") + adm.getGitRepo().getGitRepoName() + "/" + adm.getGitPath());
                if (!file.exists()) {
                    file.mkdir();
                }
                fos = new FileOutputStream(file + "/" + resId + ".trig");
                System.out.println("OUTPUT >>" + file + "/" + resId + ".trig");
                modelToOutputStream(ModelFactory.createModelForGraph(data.getDatasetGraph().getGraph(NodeFactory.createURI(c))), fos, resId);
                RevCommit rev = GitHelpers.commitChanges(resType, "Committed by " + getUserId() + " for task:" + data.getTaskId());
                System.out.println("COMMIT >>" + rev.getName());
                data.addGitRevisionInfo(c, rev.getName());
                GitHelpers.push(resType, EditConfig.getProperty("gitRemoteBase"), gitUser, gitPass, EditConfig.getProperty("gitLocalRoot"));
            } catch (Exception e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
                throw new GitServiceException(e);
            }
        }
    }

    public void modelToOutputStream(Model m, OutputStream out, String resId) throws FileNotFoundException {
        // m = removeGitInfo(m);
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
        return data.getTaskId();
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public String getUserId() {
        return data.getUserId();
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
