package io.bdrc.edit.service;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Set;

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
import io.bdrc.edit.EditConstants;
import io.bdrc.edit.helpers.DataUpdate;
import io.bdrc.edit.txn.exceptions.GitRevisionException;
import io.bdrc.edit.txn.exceptions.ServiceException;

public class GitRevisionService implements BUDAEditService {

    HashMap<String, String> revMap;
    String patch;
    DataUpdate data;

    public GitRevisionService(DataUpdate data) throws GitRevisionException {
        super();
        this.revMap = data.getGitRev();
        this.patch = buildRevisionPatch();
        this.data = data;
    }

    private String buildRevisionPatch() throws GitRevisionException {
        Set<String> keys = revMap.keySet();
        StringBuffer sb = new StringBuffer();
        try {
            sb.append("TX .");
            for (String key : keys) {
                String resId = key.substring(key.lastIndexOf('/') + 1);
                String line = "A <" + EditConstants.BDA + resId + "> <http://purl.bdrc.io/ontology/admin/gitRevision> \"" + revMap.get(key) + "\" <http://purl.bdrc.io/graph/" + resId + "> .";
                sb.append(System.lineSeparator());
                sb.append(line);

            }
            sb.append(System.lineSeparator());
            sb.append("TC .");
        } catch (Exception e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
            throw new GitRevisionException(e);
        }
        String s = sb.toString();
        System.out.println(s);
        return s;
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
            Model m = ModelFactory.createModelForGraph(dsg.getGraph(NodeFactory.createURI(g)));
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
