package io.bdrc.edit.service;

import static io.bdrc.libraries.Models.ADM;
import static io.bdrc.libraries.Models.BDA;
import static io.bdrc.libraries.Models.BDG;
import static io.bdrc.libraries.Models.BDO;
import static io.bdrc.libraries.Models.BDR;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.nio.charset.Charset;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.SortedMap;

import org.apache.commons.codec.binary.Hex;
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
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.TextProgressMonitor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.bdrc.edit.EditConfig;
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
     * @throws IOException
     */
    public void run() throws GitServiceException {
        log.info("Running Git Patch Service for task {}", data.getTaskId());
        // First the existing graphs being updated
        for (String g : graphs) {
            String resType = data.getResourceType(g);
            System.out.println("Admin DATA >>" + data.getGitInfo(g) + " graph=" + g);
            GitHelpers.ensureGitRepo(resType, EditConfig.getProperty("gitLocalRoot"));

        }
        /*
         * this.remoteURL = EditConfig.getProperty("gitRemoteRootUrl") +
         * resType.toLowerCase() + "s.git"; try { localRepo = new
         * FileRepositoryBuilder().setGitDir(new
         * File(EditConfig.getProperty("gitLocalRoot") + resType +
         * "s/.git")).setWorkTree(new File(EditConfig.getProperty("gitLocalRoot") +
         * resType + "s/")).readEnvironment().build(); } catch (IOException e) { //
         * log.logMsg("GIT Service " + id + " entered status ", //
         * Types.getSvcStatus(Types.SVC_STATUS_INIT_FAILED)); throw new
         * GitServiceException(e); } // log.logMsg("Git Service " + id +
         * " entered status ", // Types.getSvcStatus(Types.SVC_STATUS_STARTING)); try {
         * // 1) Pull remote directory Git git = new Git(localRepo);
         * git.pull().setProgressMonitor(new
         * TextProgressMonitor()).setRemote("origin").call();
         * System.out.println("Running GitService... remote repo was pulled.. " + id);
         * /* switch (type) { case Types.GIT_CREATE_SVC: // 2) check for a directory
         * corresponding for the given resource Id // if none, create one Model m =
         * data.getMod(); String dir = EditConfig.getProperty("gitLocalRoot") +
         * resType.toLowerCase() + "s/"; String sub = getDir(data.getResId()); if
         * (!hasNoDir()) { boolean exist = new
         * File(EditConfig.getProperty("gitLocalRoot") + resType.toLowerCase() + "s/" +
         * sub).exists(); if (!exist) { new File(dir + sub).mkdir(); } else { dir = dir
         * + sub + "/"; } } System.out.println("Running GitService... dir is.. " + dir +
         * " and sub =" + sub); // 3) write the new resource to the repo as ttl
         * FileOutputStream output = new FileOutputStream(dir + data.getResId() +
         * ".ttl", false); m.write(output, RDFLanguages.strLangTurtle); // 4) add and
         * commit to local git.add().addFilepattern(sub + "/").call();
         * git.commit().setMessage("Adding new resource " + data.getResId()).call(); //
         * 5) push to remote git.push().setCredentialsProvider(new
         * UsernamePasswordCredentialsProvider(EditConfig.getProperty("gitUser"),
         * EditConfig.getProperty("gitPassword"))).setRemote(remoteURL).call(); break;
         * case Types.GIT_DELETE_SVC: Model m_d = data.getMod(); // 2) Finding path of
         * file to be deleted String dir_d = EditConfig.getProperty("gitLocalRoot") +
         * resType.toLowerCase() + "s/"; String sub_d = getDir(data.getResId()); String
         * path = null; String rmPath = null; if (!hasNoDir()) { path = dir_d + sub_d +
         * "/" + data.getResId() + ".ttl"; rmPath = sub_d + "/" + data.getResId() +
         * ".ttl"; dir_d = dir_d + sub_d + "/"; } else { rmPath = data.getResId() +
         * ".ttl"; path = dir_d + data.getResId() + ".ttl"; }
         * System.out.println("Running GitService... dir_delete is.. " + dir_d +
         * " and sub_d =" + sub_d + " rmPath >>" + rmPath); // 3) delete the ttl file in
         * local git repo boolean delete = new File(path).delete();
         * System.out.println("Running GitService... deleting process returned : " +
         * delete); // 4) add and commit to local
         * git.rm().addFilepattern(rmPath).call();
         * git.commit().setMessage("Deleting resource " + data.getResId()).call(); // 5)
         * push to remote git.push().setCredentialsProvider(new
         * UsernamePasswordCredentialsProvider(EditConfig.getProperty("gitUser"),
         * EditConfig.getProperty("gitPassword"))).setRemote(remoteURL).call(); break; }
         * 
         * git.close(); // log.logMsg("Git Service " + id + " entered status ", //
         * Types.getSvcStatus(Types.SVC_STATUS_SUCCESS)); } catch (GitAPIException e) {
         * // log.logMsg("Git Service " + id + " entered status ", //
         * Types.getSvcStatus(Types.GIT_FAILED)); e.printStackTrace(); throw new
         * GitServiceException(e); }
         */
    }

    public void modelToOutputStream(Model m, OutputStream out, String type, int outputType, String fname) throws FileNotFoundException {
        // compute graph uri from fname; if fname == null then testing so use a dummy
        // graph URI
        String foo = (fname != null && !fname.isEmpty()) ? fname.substring(fname.lastIndexOf("/") + 1) : "GraphForTesting";
        foo = foo.replace(".trig", "").replace(".ttl", "");
        String uriStr = BDG + foo;
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
        } catch (GitAPIException e) {
            e.printStackTrace();
            throw new GitServiceException(e);
        }
        return false;
    }

    private String getDir(String resId) throws NoSuchAlgorithmException, UnsupportedEncodingException {
        int index = resId.indexOf('_');
        if (index != -1) {
            resId = resId.substring(0, index);
        }
        MessageDigest md = MessageDigest.getInstance("MD5");
        md.reset();
        md.update(resId.getBytes(Charset.forName("UTF8")));
        String hash = new String(Hex.encodeHex(md.digest())).substring(0, 2);
        return hash;
    }

    private boolean hasNoDir(String resType) {
        return resType.equalsIgnoreCase("office") || resType.equalsIgnoreCase("corporation");
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
