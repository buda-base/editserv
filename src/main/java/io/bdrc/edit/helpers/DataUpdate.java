package io.bdrc.edit.helpers;

import java.io.ByteArrayInputStream;
import java.util.HashMap;
import java.util.List;

import org.apache.jena.atlas.web.HttpException;
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

    public DataUpdate(Task tsk) throws DataUpdateException {
        super();
        this.tsk = tsk;
        this.ph = new EditPatchHeaders(RDFPatchReaderText.readerHeader(new ByteArrayInputStream(tsk.getPatch().getBytes())));
        this.create = ph.getCreateUris();
        this.graphs = ph.getGraphUris();
        prepareModels();
    }

    private void prepareModels() throws DataUpdateException {
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
                models.put(graphUri.getURI(), ModelFactory.createModelForGraph(gp));
                dsg.addGraph(graphUri, gp);
            } catch (HttpException ex) {
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

    public Model getModelByUri(String Uri) {
        return models.get(Uri);
    }

    private Model createGitInfo(String graphUri) {
        String resId = graphUri.substring(graphUri.lastIndexOf("/") + 1);
        AdminData data = new AdminData(resId, ph.getResourceType(resId));
        return data.asModel();
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
