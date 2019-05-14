package io.bdrc.edit.service;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.List;

import javax.servlet.http.HttpServletRequest;

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
import io.bdrc.edit.helpers.EditPatchHeaders;
import io.bdrc.edit.txn.exceptions.PatchServiceException;
import io.bdrc.edit.txn.exceptions.ServiceException;

public class PatchService implements BUDAEditService {

    String pragma;
    String payload;
    String id;
    String name;
    int status;
    EditPatchHeaders ph;

    public PatchService(HttpServletRequest req) {
        this.pragma = req.getHeader("Pragma");
        this.payload = req.getParameter("Payload");
        String time = Long.toString(System.currentTimeMillis());
        this.ph = new EditPatchHeaders(RDFPatchReaderText.readerHeader(new ByteArrayInputStream(payload.getBytes())));
        this.id = ph.getPatchId();
        this.name = "PATCH_SVC_" + time;
    }

    public PatchService(String pragma, String payload) {
        this.pragma = pragma;
        this.payload = payload;
        String time = Long.toString(System.currentTimeMillis());
        this.ph = new EditPatchHeaders(RDFPatchReaderText.readerHeader(new ByteArrayInputStream(payload.getBytes())));
        this.id = ph.getPatchId();
        this.name = "PATCH_SVC_" + time;
    }

    public String getPragma() {
        return pragma;
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
        // Fetching the graph to be patched
        try {

            System.out.println("Using remote endpoint >>" + EditConfig.getProperty("fusekiData"));
            InputStream patch = new ByteArrayInputStream(payload.getBytes());
            RDFPatchReaderText rdf = new RDFPatchReaderText(patch);

            RDFConnectionRemoteBuilder builder = RDFConnectionFuseki.create().destination(EditConfig.getProperty("fusekiData"));
            RDFConnectionFuseki fusConn = ((RDFConnectionFuseki) builder.build());
            Dataset ds = DatasetFactory.create();
            DatasetGraph dsg = ds.asDatasetGraph();
            List<String> graphs = ph.getGraphUris();
            if (graphs.size() == 0) {
                throw new PatchServiceException("No graph information available for patchId:" + ph.getPatchId());
            }
            for (String st : graphs) {
                Node graphUri = NodeFactory.createURI(st);
                try {
                    Graph gp = fusConn.fetch(st).getGraph();
                    dsg.addGraph(graphUri, gp);
                } catch (HttpException ex) {
                    throw new PatchServiceException("No graph could be fetched as " + st + " for patchId:" + ph.getPatchId());
                }
            }

            // Applying changes
            RDFChangesApply apply = new RDFChangesApply(dsg);
            rdf.apply(apply);

            // Putting the graph back into fuseki dataset
            for (String st : graphs) {
                Node graphUri = NodeFactory.createURI(st);
                try {
                    Model m = ModelFactory.createModelForGraph(dsg.getGraph(graphUri));
                    putModel(fusConn, st, m);
                } catch (HttpException ex) {
                    throw new PatchServiceException("No graph could be uploaded to fuseki as " + st + " for patchId:" + ph.getPatchId());
                }
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
        return "PatchService [ pragma=" + pragma + ", payload=" + payload + ", id=" + id + "]";
    }

    @Override
    public String getId() {
        // TODO Auto-generated method stub
        return id;
    }

}
