package io.bdrc.edit.modules;

import java.io.ByteArrayInputStream;
import java.io.InputStream;

import org.apache.jena.atlas.web.HttpException;
import org.apache.jena.graph.NodeFactory;
import org.apache.jena.query.ReadWrite;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdfconnection.RDFConnectionFuseki;
import org.apache.jena.rdfconnection.RDFConnectionRemoteBuilder;
import org.apache.jena.sparql.core.DatasetGraph;
import org.seaborne.patch.changes.RDFChangesApply;
import org.seaborne.patch.text.RDFPatchReaderText;

import io.bdrc.edit.EditConfig;
import io.bdrc.edit.Types;
import io.bdrc.edit.helpers.DataUpdate;
import io.bdrc.edit.helpers.EditPatchHeaders;
import io.bdrc.edit.txn.TransactionLog;
import io.bdrc.edit.txn.exceptions.PatchServiceException;
import io.bdrc.edit.txn.exceptions.ServiceException;

public class PatchModule implements BUDAEditModule {

    DataUpdate data;
    String userId;
    String name;
    int status;
    EditPatchHeaders ph;
    TransactionLog log;

    public PatchModule(DataUpdate data, TransactionLog log) throws PatchServiceException {
        this.userId = data.getUserId();
        String time = Long.toString(System.currentTimeMillis());
        this.name = "PATCH_MOD_" + data.getTaskId();
        this.data = data;
        this.log = log;
        setStatus(Types.STATUS_PREPARED);
        log.addContent(name, name + " entered " + Types.getStatus(status));
    }

    @Override
    public boolean rollback() throws ServiceException {
        // TODO Auto-generated method stub
        return false;
    }

    @Override
    public void run() throws ServiceException {
        try {
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
                    Model m = ModelFactory.createModelForGraph(dsg.getGraph(NodeFactory.createURI(st)));
                    System.out.println("Model after patching for ------>> " + st);
                    // m.write(System.out, "TRIG");
                    putModel(fusConn, st, m);
                    data.getDatasetGraph().addGraph(NodeFactory.createURI(st), m.getGraph());
                } catch (HttpException ex) {
                    throw new PatchServiceException("No graph could be uploaded to fuseki as " + st + " for patchId:" + ph.getPatchId());
                }
            }
            // Adding created and populated graphs to the main fuseki dataset
            for (String c : data.getCreate()) {
                Model m = ModelFactory.createModelForGraph(dsg.getGraph(NodeFactory.createURI(c)));
                putModel(fusConn, c, m);
                data.getDatasetGraph().addGraph(NodeFactory.createURI(c), m.getGraph());
            }
            fusConn.close();
            patch.close();
        } catch (Exception e) {
            e.printStackTrace();
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
        return data.getTaskId();
    }

}
