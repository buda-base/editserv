package io.bdrc.edit.helpers;

import java.io.ByteArrayInputStream;
import java.io.UnsupportedEncodingException;
import java.nio.charset.Charset;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.commons.codec.binary.Hex;
import org.apache.jena.graph.Graph;
import org.apache.jena.graph.Node;
import org.apache.jena.graph.NodeFactory;
import org.apache.jena.graph.Triple;
import org.apache.jena.query.Dataset;
import org.apache.jena.query.DatasetFactory;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.ResourceFactory;
import org.apache.jena.rdf.model.Statement;
import org.apache.jena.rdf.model.StmtIterator;
import org.apache.jena.rdfconnection.RDFConnectionFuseki;
import org.apache.jena.rdfconnection.RDFConnectionRemoteBuilder;
import org.apache.jena.sparql.core.DatasetGraph;
import org.seaborne.patch.text.RDFPatchReaderText;

import io.bdrc.edit.EditConfig;
import io.bdrc.edit.EditConstants;
import io.bdrc.edit.patch.Task;
import io.bdrc.edit.sparql.QueryProcessor;
import io.bdrc.edit.txn.exceptions.DataUpdateException;

public class DataUpdate {

    private Task tsk;
    private EditPatchHeaders ph;
    private List<String> create;
    private List<String> graphs;
    private List<String> delete;
    private List<String> replace;
    private DatasetGraph dsg;
    private HashMap<String, AdminData> admData;
    private HashMap<String, String> gitRev;

    public DataUpdate(Task tsk) throws DataUpdateException, NoSuchAlgorithmException, UnsupportedEncodingException {
        super();
        this.tsk = tsk;
        this.ph = new EditPatchHeaders(RDFPatchReaderText.readerHeader(new ByteArrayInputStream(tsk.getPatch().getBytes())));
        this.create = ph.getCreateUris();
        this.graphs = ph.getGraphUris();
        this.delete = ph.getDeleteUris();
        this.replace = ph.getReplaceUrisPairs();
        this.admData = new HashMap<>();
        this.gitRev = new HashMap<>();
        prepareModels();
    }

    private void prepareModels() throws DataUpdateException, NoSuchAlgorithmException, UnsupportedEncodingException {
        RDFConnectionRemoteBuilder builder = RDFConnectionFuseki.create().destination(EditConfig.getProperty("fusekiData"));
        RDFConnectionFuseki fusConn = ((RDFConnectionFuseki) builder.build());
        Dataset ds = DatasetFactory.create();
        dsg = ds.asDatasetGraph();

        // Fetching the graphs, building the dataset to be patched
        for (String st : graphs) {
            Node graphUri = NodeFactory.createURI(st);
            try {
                Graph gp = fusConn.fetch(st).getGraph();
                fetchGitInfo(graphUri.getURI());
                Model m = ModelFactory.createModelForGraph(gp);
                m = removeGitRevisionInfo(st, m);
                dsg.addGraph(graphUri, m.getGraph());
            } catch (Exception ex) {
                ex.printStackTrace();
                throw new DataUpdateException("No graph could be fetched as " + st + " for patchId:" + ph.getPatchId());
            }
        }
        // Listing the graphs to create
        List<String> create = ph.getCreateUris();
        // add empty named graphs to the dataset
        for (String c : create) {
            Node graphUri = NodeFactory.createURI(c);
            dsg.addGraph(graphUri, Graph.emptyGraph);
            Graph g = dsg.getGraph(graphUri);
            Model m = ModelFactory.createModelForGraph(g);
            removeGitRevisionInfo(c, m);
            createGitInfo(graphUri.getURI());
            dsg.addGraph(graphUri, m.getGraph());
        }

        // Listing the graphs to delete
        List<String> delete = ph.getDeleteUris();
        // add empty named graphs to the dataset
        for (String d : delete) {
            Node graphUri = NodeFactory.createURI(d);
            dsg.addGraph(graphUri, Graph.emptyGraph);
            Graph g = dsg.getGraph(graphUri);
            Model m = ModelFactory.createModelForGraph(g);
            removeGitRevisionInfo(d, m);
            createGitInfo(graphUri.getURI());
            dsg.addGraph(graphUri, m.getGraph());
        }
    }

    private Model removeGitRevisionInfo(String graphUri, Model m) {
        String resId = graphUri.substring(graphUri.lastIndexOf("/") + 1);
        Triple tpl = Triple.create(ResourceFactory.createResource(EditConstants.BDA + resId).asNode(), AdminData.GIT_REVISION.asNode(), Node.ANY);
        Graph g = m.getGraph();
        List<Triple> list = g.find(tpl).toList();
        for (Triple t : list) {
            g.delete(t);
        }
        return ModelFactory.createModelForGraph(g);
    }

    public void addGitRevisionInfo(String graph, String sha1) {
        gitRev.put(graph, sha1);
    }

    public List<String> getAllAffectedGraphs() {
        List<String> all = Stream.of(graphs, create).flatMap(x -> x.stream()).collect(Collectors.toList());
        return all;
    }

    public String getResourceType(String resId) {
        return ph.getResourceType(resId);
    }

    private Model fetchGitInfo(String graphUri) {
        String resId = graphUri.substring(graphUri.lastIndexOf("/") + 1);
        admData.put(graphUri, new AdminData(resId, getResourceType(graphUri)));
        return admData.get(graphUri).asModel();
    }

    private Model createGitInfo(String graphUri) throws NoSuchAlgorithmException, UnsupportedEncodingException {
        String resId = graphUri.substring(graphUri.lastIndexOf("/") + 1);
        admData.put(graphUri, new AdminData(resId, getResourceType(graphUri), getGitDir(resId)));
        return admData.get(graphUri).asModel();
    }

    private String getGitDir(String resId) throws NoSuchAlgorithmException, UnsupportedEncodingException {
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

    public static String buildReplacePatch(String uriToReplace, String newUri) {
        StringBuffer sb = new StringBuffer();
        String replaceResId = uriToReplace.substring(uriToReplace.lastIndexOf('/') + 1);
        String newResId = newUri.substring(newUri.lastIndexOf('/') + 1);
        sb.append("TX .");
        Model m = QueryProcessor.getTriplesWithObject(uriToReplace);
        StmtIterator it = m.listStatements();
        while (it.hasNext()) {
            Statement st = it.next();
            String delCmd = "D <" + st.getSubject().getURI() + "> <" + st.getPredicate().getURI() + "> <" + uriToReplace + "> <" + replaceResId + ">";
            String addCmd = "A <" + st.getSubject().getURI() + "> <" + st.getPredicate().getURI() + "> <" + newUri + "> <" + newResId + ">";
            sb.append(System.lineSeparator() + delCmd);
            sb.append(System.lineSeparator() + addCmd);
        }
        sb.append("TC .");
        return sb.toString();
    }

    public AdminData getAdminData(String graphUri) {
        return admData.get(graphUri);
    }

    public List<String> getCreate() {
        return create;
    }

    public List<String> getDelete() {
        return delete;
    }

    public List<String> getReplace() {
        return replace;
    }

    public List<String> getGraphs() {
        return graphs;
    }

    public Task getTsk() {
        return tsk;
    }

    public String getUserId() {
        return tsk.getUser();
    }

    public String getTaskId() {
        return tsk.getId();
    }

    public String getPatch() {
        return tsk.getPatch();
    }

    public HashMap<String, String> getGitRev() {
        return gitRev;
    }

    public DatasetGraph getDatasetGraph() {
        return dsg;
    }

}
