package io.bdrc.edit.service;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.List;

import org.apache.jena.atlas.web.HttpException;
import org.apache.jena.graph.Graph;
import org.apache.jena.graph.Node;
import org.apache.jena.graph.NodeFactory;
import org.apache.jena.query.Dataset;
import org.apache.jena.query.DatasetFactory;
import org.apache.jena.query.ReadWrite;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdfconnection.RDFConnectionFuseki;
import org.apache.jena.rdfconnection.RDFConnectionRemoteBuilder;
import org.apache.jena.sparql.core.DatasetGraph;
import org.seaborne.patch.changes.RDFChangesApply;
import org.seaborne.patch.text.RDFPatchReaderText;

import io.bdrc.edit.EditConfig;
import io.bdrc.edit.helpers.AdminData;
import io.bdrc.edit.helpers.EditPatchHeaders;
import io.bdrc.edit.patch.Task;
import io.bdrc.edit.txn.exceptions.PatchServiceException;
import io.bdrc.edit.txn.exceptions.ServiceException;

public class PatchService implements BUDAEditService {

    String payload;
    String userId;
    String id;
    String name;
    int status;
    EditPatchHeaders ph;

    public PatchService(Task tsk) throws PatchServiceException {
        this.payload = tsk.getPatch();
        this.userId = tsk.getUser();
        String time = Long.toString(System.currentTimeMillis());
        this.ph = new EditPatchHeaders(RDFPatchReaderText.readerHeader(new ByteArrayInputStream(payload.getBytes())));
        this.id = tsk.getId();
        this.name = "TASK_SVC_" + time;
    }

    public PatchService(String payload, String userId) throws PatchServiceException {
        this.payload = payload;
        this.userId = userId;
        String time = Long.toString(System.currentTimeMillis());
        this.ph = new EditPatchHeaders(RDFPatchReaderText.readerHeader(new ByteArrayInputStream(payload.getBytes())));
        this.id = ph.getPatchId();
        this.name = "TASK_SVC_" + time;
    }

    public String getPayload() {
        return payload;
    }

    @Override
    public boolean rollback() throws ServiceException {
        // TODO Auto-generated method stub
        return false;
    }

    @Override
    public void run() throws ServiceException {
        try {
            System.out.println("Using remote endpoint >>" + EditConfig.getProperty("fusekiData"));
            InputStream patch = new ByteArrayInputStream(payload.getBytes());
            RDFPatchReaderText rdf = new RDFPatchReaderText(patch);

            RDFConnectionRemoteBuilder builder = RDFConnectionFuseki.create().destination(EditConfig.getProperty("fusekiData"));
            RDFConnectionFuseki fusConn = ((RDFConnectionFuseki) builder.build());
            Dataset ds = DatasetFactory.create();
            DatasetGraph dsg = ds.asDatasetGraph();
            // Listing the graphs to be patched
            List<String> graphs = ph.getGraphUris();
            if (graphs.size() == 0) {
                throw new PatchServiceException("No graph information available for patchId:" + ph.getPatchId());
            }
            // Fetching the graphs, building the dataset to be patched
            for (String st : graphs) {
                Node graphUri = NodeFactory.createURI(st);
                try {
                    Graph gp = fusConn.fetch(st).getGraph();
                    dsg.addGraph(graphUri, gp);
                } catch (HttpException ex) {
                    throw new PatchServiceException("No graph could be fetched as " + st + " for patchId:" + ph.getPatchId());
                }
            }
            // Listing the graphs to create
            List<String> create = ph.getCreateUris();
            // add empty named graphs to the dataset
            for (String c : create) {
                Node graphUri = NodeFactory.createURI(c);
                dsg.addGraph(graphUri, Graph.emptyGraph);
            }

            // Applying changes
            RDFChangesApply apply = new RDFChangesApply(dsg);
            rdf.apply(apply);

            // Putting the graphs back into main fuseki dataset
            for (String st : graphs) {
                Node graphUri = NodeFactory.createURI(st);
                try {
                    Model m = ModelFactory.createModelForGraph(dsg.getGraph(graphUri));
                    putModel(fusConn, st, m);
                } catch (HttpException ex) {
                    throw new PatchServiceException("No graph could be uploaded to fuseki as " + st + " for patchId:" + ph.getPatchId());
                }
            }
            // Adding created and populated graphs to the main fuseki dataset
            for (String c : create) {
                Node graphUri = NodeFactory.createURI(c);
                Graph g = dsg.getGraph(graphUri);
                Model m = ModelFactory.createModelForGraph(g);
                m.add(createGitInfo(graphUri.getURI()));
                putModel(fusConn, c, m);
            }
            fusConn.close();
            patch.close();
        } catch (Exception e) {
            throw new PatchServiceException(e);
        }
    }

    private void putModel(RDFConnectionFuseki fusConn, String graph, Model m) throws Exception {
        fusConn.begin(ReadWrite.WRITE);
        fusConn.put(graph, m);
        fusConn.commit();
        fusConn.end();
    }

    private Model createGitInfo(String graphUri) {
        String resId = graphUri.substring(graphUri.lastIndexOf("/") + 1);
        AdminData data = new AdminData(resId, ph.getResourceType(resId));
        return data.asModel();
    }

    public String getUserId() {
        return userId;
    }

    @Override
    public int getStatus() {
        return status;
    }

    @Override
    public void setStatus(int st) {
        this.status = st;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public String toString() {
        return "PatchService [ payload=" + payload + ", id=" + id + "]";
    }

    @Override
    public String getId() {
        // TODO Auto-generated method stub
        return id;
    }

}
