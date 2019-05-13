package io.bdrc.edit.service;

import java.io.ByteArrayInputStream;
import java.io.InputStream;

import javax.servlet.http.HttpServletRequest;

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
import io.bdrc.edit.txn.exceptions.PatchServiceException;
import io.bdrc.edit.txn.exceptions.ServiceException;

public class PatchService implements BUDAEditService {

    String slug;
    String pragma;
    String payload;
    String id;
    String name;
    int status;

    public PatchService(HttpServletRequest req) {
        this.slug = req.getHeader("Slug");
        this.pragma = req.getHeader("Pragma");
        this.payload = req.getParameter("Payload");
        String time = Long.toString(System.currentTimeMillis());
        this.id = slug + "_" + time;
        this.name = "PATCH_SVC_" + time;
    }

    public PatchService(String slug, String pragma, String payload) {
        this.slug = slug;
        this.pragma = pragma;
        this.payload = payload;
        String time = Long.toString(System.currentTimeMillis());
        this.id = slug + "_" + time;
        this.name = "PATCH_SVC_" + time;
    }

    public String getSlug() {
        return slug;
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
            String graph = EditConstants.BDG + slug;
            System.out.println("GRAPH uri>>" + graph);
            Dataset ds = DatasetFactory.create();
            DatasetGraph dsg = ds.asDatasetGraph();
            Node graphUri = NodeFactory.createURI(graph);
            System.out.println("NODE >>" + graphUri);
            Graph gp = fusConn.fetch(graph).getGraph();
            System.out.println("GRAPH >>" + gp.size());
            dsg.addGraph(graphUri, gp);

            // Applying changes
            RDFChangesApply apply = new RDFChangesApply(dsg);
            rdf.apply(apply);

            // Putting the graph back into fuseki dataset
            Model m = ModelFactory.createModelForGraph(dsg.getGraph(graphUri));
            // fusConn.begin(ReadWrite.WRITE);
            // fusConn.put(graph, m);
            // fusConn.commit();
            // fusConn.end();
            putModel(fusConn, graph, m);
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
    public String getId() {
        return id;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public String toString() {
        return "PatchService [slug=" + slug + ", pragma=" + pragma + ", payload=" + payload + ", id=" + id + "]";
    }

}
