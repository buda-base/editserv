package io.bdrc.edit.helpers;

import java.io.ByteArrayInputStream;
import java.io.UnsupportedEncodingException;
import java.nio.charset.Charset;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.List;

import org.apache.commons.codec.binary.Hex;
import org.apache.jena.graph.Graph;
import org.apache.jena.graph.Node;
import org.apache.jena.graph.NodeFactory;
import org.apache.jena.query.Dataset;
import org.apache.jena.query.DatasetFactory;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdfconnection.RDFConnectionFuseki;
import org.apache.jena.rdfconnection.RDFConnectionRemoteBuilder;
import org.apache.jena.sparql.core.DatasetGraph;
import org.seaborne.patch.text.RDFPatchReaderText;

import io.bdrc.edit.EditConfig;
import io.bdrc.edit.patch.Task;
import io.bdrc.edit.txn.exceptions.DataUpdateException;

public class DataUpdate {

    private Task tsk;
    private EditPatchHeaders ph;
    private List<String> create;
    private List<String> graphs;
    private DatasetGraph dsg;
    private HashMap<String, Model> models;
    private HashMap<String, AdminData> gitInfo;

    public DataUpdate(Task tsk) throws DataUpdateException, NoSuchAlgorithmException, UnsupportedEncodingException {
        super();
        this.tsk = tsk;
        this.ph = new EditPatchHeaders(RDFPatchReaderText.readerHeader(new ByteArrayInputStream(tsk.getPatch().getBytes())));
        this.create = ph.getCreateUris();
        this.graphs = ph.getGraphUris();
        this.models = new HashMap<>();
        this.gitInfo = new HashMap<>();
        prepareModels();
    }

    private void prepareModels() throws DataUpdateException, NoSuchAlgorithmException, UnsupportedEncodingException {
        System.out.println("Using remote endpoint >>" + EditConfig.getProperty("fusekiData"));

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
                models.put(graphUri.getURI(), ModelFactory.createModelForGraph(gp));
                dsg.addGraph(graphUri, gp);
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
            m.add(createGitInfo(graphUri.getURI()));
            models.put(graphUri.getURI(), m);
        }
    }

    public String getResourceType(String resId) {
        System.out.println("MP >>>>>>>>>>>>>>>" + ph.getResTypeMapping() + " resId=" + resId);
        return ph.getResourceType(resId);
    }

    public Model getModelByUri(String Uri) {
        return models.get(Uri);
    }

    private Model fetchGitInfo(String graphUri) {
        String resId = graphUri.substring(graphUri.lastIndexOf("/") + 1);
        AdminData data = new AdminData(resId, getResourceType(graphUri));
        gitInfo.put(graphUri, data);
        // System.out.println("GIT info Map>>" + gitInfo);
        // System.out.println("admin data>>" + data);
        System.out.println("GRAPH URI>>" + graphUri + " resType=" + ph.getResourceType(graphUri));
        return data.asModel();
    }

    private Model createGitInfo(String graphUri) throws NoSuchAlgorithmException, UnsupportedEncodingException {
        String resId = graphUri.substring(graphUri.lastIndexOf("/") + 1);
        AdminData data = new AdminData(resId, getResourceType(graphUri), getGitDir(resId), "");
        gitInfo.put(graphUri, data);
        // System.out.println("GIT info Map>>" + gitInfo);
        // System.out.println("admin data>>" + data);
        System.out.println("GRAPH URI>>" + graphUri + " resType=" + ph.getResourceType(graphUri));
        return data.asModel();
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

    public AdminData getGitInfo(String graphUri) {
        return gitInfo.get(graphUri);
    }

    public List<String> getCreate() {
        return create;
    }

    public List<String> getGraphs() {
        return graphs;
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

    public DatasetGraph getDatasetGraph() {
        return dsg;
    }

}
