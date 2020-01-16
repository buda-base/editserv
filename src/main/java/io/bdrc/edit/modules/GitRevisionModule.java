package io.bdrc.edit.modules;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Set;

import org.apache.jena.graph.NodeFactory;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdfconnection.RDFConnectionFuseki;
import org.apache.jena.rdfconnection.RDFConnectionRemoteBuilder;
import org.apache.jena.sparql.core.DatasetGraph;
import org.seaborne.patch.changes.RDFChangesApply;
import org.seaborne.patch.text.RDFPatchReaderText;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.bdrc.edit.EditConfig;
import io.bdrc.edit.EditConstants;
import io.bdrc.edit.Types;
import io.bdrc.edit.helpers.DataUpdate;
import io.bdrc.edit.helpers.Helpers;
import io.bdrc.edit.txn.TransactionLog;
import io.bdrc.edit.txn.exceptions.GitRevisionModuleException;
import io.bdrc.edit.txn.exceptions.ModuleException;

public class GitRevisionModule implements BUDAEditModule {

    HashMap<String, String> revMap;
    String patch;
    String id;
    int status;
    DataUpdate data;
    TransactionLog log;
    public final static Logger logger = LoggerFactory.getLogger(GitRevisionModule.class.getName());

    public GitRevisionModule(DataUpdate data, TransactionLog log) throws GitRevisionModuleException {
        super();
        this.revMap = data.getGitRev();
        this.patch = buildRevisionPatch();
        this.id = data.getTaskId();
        this.data = data;
        this.log = log;
        setStatus(Types.STATUS_PREPARED);
    }

    private String buildRevisionPatch() throws GitRevisionModuleException {
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
            logger.error("GitRevisionModule buildRevisionPatch failed ", e);
            throw new GitRevisionModuleException(e);
        }
        String s = sb.toString();
        return s;
    }

    @Override
    public boolean rollback() throws ModuleException {
        // TODO Auto-generated method stub
        return false;
    }

    @Override
    public void run() throws GitRevisionModuleException {
        setStatus(Types.STATUS_PROCESSING);
        try {
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
                Helpers.putModel(fusConn, g, m);
            }
            setStatus(Types.STATUS_SUCCESS);
        } catch (Exception e) {
            logger.error("GitRevisionModule run failed ", e);
            setStatus(Types.STATUS_FAILED);
            log.addError(getName(), e.getMessage());
            throw new GitRevisionModuleException(e);
        }
    }

    @Override
    public int getStatus() {
        return status;
    }

    @Override
    public void setStatus(int st) throws GitRevisionModuleException {
        try {
            status = st;
            log.addContent(getName(), " entered " + Types.getStatus(status));
            log.setLastStatus(Types.getStatus(status));
        } catch (Exception e) {
            logger.error("GitRevisionModule set status failed ", e);
            setStatus(Types.STATUS_FAILED);
            log.setLastStatus(getName() + ": " + Types.getStatus(status));
            log.addError(getName(), e.getMessage());
            throw new GitRevisionModuleException(e);
        }
    }

    @Override
    public String getName() {
        return "GIT_REV_MOD_" + data.getTaskId();
    }

    @Override
    public String getUserId() {
        return data.getUserId();
    }

}
