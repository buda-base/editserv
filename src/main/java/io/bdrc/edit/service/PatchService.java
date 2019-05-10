package io.bdrc.edit.service;

import java.io.ByteArrayInputStream;
import java.io.InputStream;

import javax.servlet.http.HttpServletRequest;

import org.apache.jena.query.DatasetAccessor;
import org.apache.jena.query.DatasetAccessorFactory;
import org.apache.jena.query.ReadWrite;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdfconnection.RDFConnectionFuseki;
import org.apache.jena.rdfconnection.RDFConnectionRemoteBuilder;
import org.apache.jena.sparql.core.DatasetGraph;
import org.apache.jena.sparql.core.DatasetGraphFactory;
import org.seaborne.patch.changes.RDFChangesApply;
import org.seaborne.patch.text.RDFPatchReaderText;

import io.bdrc.edit.EditConfig;
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
            String graph = "bdg:" + slug;
            DatasetAccessor access = DatasetAccessorFactory.createHTTP(EditConfig.getProperty("fusekiData"));
            Model m = access.getModel(graph);
            DatasetGraph gh = DatasetGraphFactory.create(m.getGraph());
            System.out.println(gh.getDefaultGraph().size());
            System.out.println("Model size =" + m.getGraph().size());
            // Applying changes
            RDFChangesApply apply = new RDFChangesApply(gh);
            InputStream patch = new ByteArrayInputStream(payload.getBytes());
            RDFPatchReaderText rdf = new RDFPatchReaderText(patch);
            rdf.apply(apply);
            System.out.println("Model size =" + m.getGraph().size());
            // Putting the graph back into fuseki dataset
            RDFConnectionRemoteBuilder builder = RDFConnectionFuseki.create().destination(EditConfig.getProperty("fusekiData"));
            RDFConnectionFuseki fusConn = ((RDFConnectionFuseki) builder.build());
            fusConn.begin(ReadWrite.WRITE);
            fusConn.put(graph, m);
            fusConn.commit();
            fusConn.end();
            fusConn.close();
            patch.close();
        } catch (Exception e) {
            throw new PatchServiceException(e);
        }
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
