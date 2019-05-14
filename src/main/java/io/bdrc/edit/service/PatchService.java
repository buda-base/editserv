package io.bdrc.edit.service;

import java.io.BufferedWriter;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
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
import io.bdrc.edit.EditConstants;
import io.bdrc.edit.helpers.EditPatchHeaders;
import io.bdrc.edit.txn.exceptions.PatchServiceException;
import io.bdrc.edit.txn.exceptions.ServiceException;

public class PatchService implements BUDAEditService {

    String pragma;
    String payload;
    String userId;
    String id;
    String name;
    int status;
    EditPatchHeaders ph;

    public PatchService(HttpServletRequest req, String userId) throws PatchServiceException {
        this.pragma = req.getHeader("Pragma");
        this.payload = req.getParameter("Payload");
        this.userId = userId;
        String time = Long.toString(System.currentTimeMillis());
        this.ph = new EditPatchHeaders(RDFPatchReaderText.readerHeader(new ByteArrayInputStream(payload.getBytes())));
        this.id = ph.getPatchId();
        this.name = "PATCH_SVC_" + time;
        savePatch();
    }

    public PatchService(String pragma, String payload, String userId) throws PatchServiceException {
        this.pragma = pragma;
        this.payload = payload;
        this.userId = userId;
        String time = Long.toString(System.currentTimeMillis());
        this.ph = new EditPatchHeaders(RDFPatchReaderText.readerHeader(new ByteArrayInputStream(payload.getBytes())));
        this.id = ph.getPatchId();
        this.name = "PATCH_SVC_" + time;
        savePatch();
    }

    private void savePatch() throws PatchServiceException {
        try {
            File f = new File(EditConfig.getProperty("patchesDir") + userId);
            if (!f.exists()) {
                f.mkdir();
            }
            String filename = "";
            if (pragma.equals(EditConstants.PTC_FINAL)) {
                filename = EditConfig.getProperty("patchesDir") + userId + "/" + id + EditConstants.PTC_EXT;
            }
            if (pragma.equals(EditConstants.PTC_STASH)) {
                filename = EditConfig.getProperty("patchesDir") + userId + "/stashed/" + id + EditConstants.PTC_EXT;
            }
            System.out.println("FILENAME >> " + filename);
            BufferedWriter writer = new BufferedWriter(new FileWriter(filename));
            writer.write(payload);
            writer.close();
        } catch (Exception e) {
            throw new PatchServiceException(e);
        }
    }

    public static void deletePatch(String patchId, String userId, boolean stashed) throws IOException {
        String filename = "";
        if (!stashed) {

            filename = EditConfig.getProperty("patchesDir") + userId + "/" + patchId + EditConstants.PTC_EXT;
        } else {
            filename = EditConfig.getProperty("patchesDir") + userId + "/stashed/" + patchId + EditConstants.PTC_EXT;
        }
        new File(filename).delete();
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
    public String toString() {
        return "PatchService [ pragma=" + pragma + ", payload=" + payload + ", id=" + id + "]";
    }

    @Override
    public String getId() {
        // TODO Auto-generated method stub
        return id;
    }

}
