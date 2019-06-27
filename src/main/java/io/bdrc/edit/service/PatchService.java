package io.bdrc.edit.service;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.HashMap;

import org.apache.jena.atlas.web.HttpException;
import org.apache.jena.graph.NodeFactory;
import org.apache.jena.query.ReadWrite;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdfconnection.RDFConnectionFuseki;
import org.apache.jena.rdfconnection.RDFConnectionRemoteBuilder;
import org.apache.jena.sparql.core.DatasetGraph;
import org.seaborne.patch.changes.RDFChangesApply;
import org.seaborne.patch.text.RDFPatchReaderText;

import io.bdrc.edit.EditConfig;
import io.bdrc.edit.helpers.DataUpdate;
import io.bdrc.edit.helpers.EditPatchHeaders;
import io.bdrc.edit.txn.exceptions.PatchServiceException;
import io.bdrc.edit.txn.exceptions.ServiceException;

public class PatchService implements BUDAEditService {

    DataUpdate data;
    String userId;
    String id;
    String name;
    int status;
    EditPatchHeaders ph;
    private HashMap<String, Model> models;

    public PatchService(DataUpdate data) throws PatchServiceException {
        this.userId = data.getUserId();
        String time = Long.toString(System.currentTimeMillis());
        this.id = data.getTaskId();
        this.name = "TASK_SVC_" + time;
    }

    public HashMap<String, Model> getModels() {
        return models;
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
            InputStream patch = new ByteArrayInputStream(data.getPatch().getBytes());
            RDFPatchReaderText rdf = new RDFPatchReaderText(patch);
            RDFConnectionRemoteBuilder builder = RDFConnectionFuseki.create().destination(EditConfig.getProperty("fusekiData"));
            RDFConnectionFuseki fusConn = ((RDFConnectionFuseki) builder.build());

            DatasetGraph dsg = data.getDatasetGraph();
            // Applying changes
            RDFChangesApply apply = new RDFChangesApply(dsg);
            rdf.apply(apply);

            // Putting the graphs back into main fuseki dataset
            for (String st : data.getGraphs()) {
                try {
                    Model m = data.getModelByUri(NodeFactory.createURI(st).getURI());
                    putModel(fusConn, st, m);
                } catch (HttpException ex) {
                    throw new PatchServiceException("No graph could be uploaded to fuseki as " + st + " for patchId:" + ph.getPatchId());
                }
            }
            // Adding created and populated graphs to the main fuseki dataset
            for (String c : data.getCreate()) {
                Model m = data.getModelByUri(NodeFactory.createURI(c).getURI());
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
    public String getId() {
        // TODO Auto-generated method stub
        return id;
    }

}
