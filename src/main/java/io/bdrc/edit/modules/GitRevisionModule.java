package io.bdrc.edit.modules;

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
import io.bdrc.edit.Types;
import io.bdrc.edit.helpers.DataUpdate;
import io.bdrc.edit.txn.TransactionLog;
import io.bdrc.edit.txn.exceptions.GitRevisionException;
import io.bdrc.edit.txn.exceptions.ServiceException;

public class GitRevisionModule implements BUDAEditModule {

    HashMap<String, String> revMap;
    String patch;
    String name;
    String id;
    int status;
    DataUpdate data;
    TransactionLog log;

    public GitRevisionModule(DataUpdate data, TransactionLog log) throws GitRevisionException {
        super();
        this.revMap = data.getGitRev();
        this.patch = buildRevisionPatch();
        this.id = data.getTaskId();
        this.data = data;
        this.log = log;
        this.name = "GIT_REV_MOD_" + data.getTaskId();
        setStatus(Types.STATUS_PREPARED);
        log.addContent(name, name + " entered " + Types.getStatus(status));
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
        return status;
    }

    @Override
    public void setStatus(int st) {
        status = st;
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
    public String getUserId() {
        return data.getUserId();
    }

}
