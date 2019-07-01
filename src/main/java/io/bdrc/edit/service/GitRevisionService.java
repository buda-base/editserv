package io.bdrc.edit.service;

import java.io.BufferedWriter;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.util.HashMap;
import java.util.List;
import java.util.Set;

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
import io.bdrc.edit.txn.exceptions.GitRevisionException;
import io.bdrc.edit.txn.exceptions.ServiceException;

public class GitRevisionService implements BUDAEditService {

    HashMap<String, String> revMap;
    String patch;
    DataUpdate data;

    public GitRevisionService(HashMap<String, String> revMap, DataUpdate data) throws GitRevisionException {
        super();
        this.revMap = revMap;
        this.patch = buildRevisionPatch();
        this.data = data;
    }

    private String buildRevisionPatch() throws GitRevisionException {
        Set<String> keys = revMap.keySet();
        BufferedWriter bw = new BufferedWriter(new StringWriter());
        for (String key : keys) {
            String resId = key.substring(key.lastIndexOf('/') + 1);
            String line = "A <" + key + "> <http://purl.bdrc.io/ontology/admin/gitRevision> \"" + revMap.get(key) + "\" <http://purl.bdrc.io/graph/" + resId + ">";
            try {
                bw.write(line);
                bw.newLine();
            } catch (IOException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
                throw new GitRevisionException(e);
            }
        }
        return bw.toString();
    }

    @Override
    public boolean rollback() throws ServiceException {
        // TODO Auto-generated method stub
        return false;
    }

    @Override
    public void run() throws ServiceException {
        InputStream ptc = new ByteArrayInputStream(patch.getBytes());
        RDFPatchReaderText rdf = new RDFPatchReaderText(ptc);
        RDFConnectionRemoteBuilder builder = RDFConnectionFuseki.create().destination(EditConfig.getProperty("fusekiData"));
        RDFConnectionFuseki fusConn = ((RDFConnectionFuseki) builder.build());
        DatasetGraph dsg = data.getDatasetGraph();
        // Applying changes
        RDFChangesApply apply = new RDFChangesApply(dsg);
        rdf.apply(apply);
        List<String> allGraphs = data.getAllAffectedGraphs();
        for (String g : allGraphs) {
            Model m = data.getModelByUri(NodeFactory.createURI(g).getURI());
            putModel(fusConn, g, m);
        }
    }

    private void putModel(RDFConnectionFuseki fusConn, String graph, Model m) {
        fusConn.begin(ReadWrite.WRITE);
        fusConn.put(graph, m);
        fusConn.commit();
        fusConn.end();
    }

    @Override
    public int getStatus() {
        // TODO Auto-generated method stub
        return 0;
    }

    @Override
    public void setStatus(int st) {
        // TODO Auto-generated method stub

    }

    @Override
    public String getId() {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public String getName() {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public String getUserId() {
        // TODO Auto-generated method stub
        return null;
    }

}
