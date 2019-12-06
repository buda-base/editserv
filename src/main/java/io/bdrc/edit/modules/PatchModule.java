package io.bdrc.edit.modules;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.ArrayList;

import org.apache.jena.atlas.web.HttpException;
import org.apache.jena.graph.NodeFactory;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdfconnection.RDFConnectionFuseki;
import org.apache.jena.rdfconnection.RDFConnectionRemoteBuilder;
import org.apache.jena.reasoner.Reasoner;
import org.apache.jena.sparql.core.DatasetGraph;
import org.seaborne.patch.changes.RDFChangesApply;
import org.seaborne.patch.text.RDFPatchReaderText;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.bdrc.edit.EditConfig;
import io.bdrc.edit.Types;
import io.bdrc.edit.helpers.DataUpdate;
import io.bdrc.edit.helpers.Helpers;
import io.bdrc.edit.txn.TransactionLog;
import io.bdrc.edit.txn.exceptions.ModuleException;
import io.bdrc.edit.txn.exceptions.PatchModuleException;

public class PatchModule implements BUDAEditModule {

    DataUpdate data;
    String userId;
    int status;
    TransactionLog log;
    Reasoner reasoner;

    public final static Logger logger = LoggerFactory.getLogger(PatchModule.class.getName());

    public PatchModule(DataUpdate data, TransactionLog log, Reasoner reasoner) throws PatchModuleException {
        this.userId = data.getUserId();
        this.data = data;
        this.log = log;
        this.reasoner = reasoner;
        setStatus(Types.STATUS_PREPARED);
    }

    @Override
    public boolean rollback() throws ModuleException {
        // TODO Auto-generated method stub
        return false;
    }

    @Override
    public void run() throws PatchModuleException {
        try {
            ArrayList<String> replaced = new ArrayList<>();
            setStatus(Types.STATUS_PROCESSING);
            InputStream patch = new ByteArrayInputStream(data.getPatch().getBytes());
            RDFPatchReaderText rdf = new RDFPatchReaderText(patch);
            RDFConnectionRemoteBuilder builder = RDFConnectionFuseki.create().destination(EditConfig.getProperty("fusekiData"));
            RDFConnectionFuseki fusConn = ((RDFConnectionFuseki) builder.build());
            DatasetGraph dsg = data.getDatasetGraph();
            // Applying changes
            RDFChangesApply apply = new RDFChangesApply(dsg);
            rdf.apply(apply);

            for (String s : data.getReplace()) {
                String[] parts = s.split("-");
                String ptc = DataUpdate.buildReplacePatch(parts[0], parts[1]);
                InputStream replacePatch = new ByteArrayInputStream(ptc.getBytes());
                RDFPatchReaderText replaceRdf = new RDFPatchReaderText(replacePatch);
                RDFChangesApply applyReplace = new RDFChangesApply(dsg);
                replaceRdf.apply(applyReplace);
                replaced.add(parts[0]);
            }

            // Putting the graphs back into main fuseki dataset
            for (String st : data.getGraphs()) {
                try {
                    Model m = ModelFactory.createModelForGraph(dsg.getGraph(NodeFactory.createURI(st)));
                    Helpers.putModelWithInference(fusConn, st, m, reasoner);
                    data.getDatasetGraph().addGraph(NodeFactory.createURI(st), m.getGraph());
                } catch (HttpException ex) {
                    throw new PatchModuleException("No graph could be uploaded to fuseki as " + st + " for patchId:" + data.getTaskId());
                }
            }
            // Adding created and populated graphs to the main fuseki dataset
            for (String c : data.getCreate()) {
                Model m = ModelFactory.createModelForGraph(dsg.getGraph(NodeFactory.createURI(c)));
                Helpers.putModelWithInference(fusConn, c, m, reasoner);
                data.getDatasetGraph().addGraph(NodeFactory.createURI(c), m.getGraph());
            }
            for (String st : replaced) {
                Model m = ModelFactory.createModelForGraph(dsg.getGraph(NodeFactory.createURI(st)));
                Helpers.putModelWithInference(fusConn, st, m, reasoner);
            }
            fusConn.close();
            patch.close();
            setStatus(Types.STATUS_SUCCESS);
        } catch (Exception e) {
            e.printStackTrace();
            setStatus(Types.STATUS_FAILED);
            log.addError(getName(), e.getMessage());
            throw new PatchModuleException(e);
        }
    }

    public String getUserId() {
        return userId;
    }

    @Override
    public int getStatus() {
        return status;
    }

    @Override
    public void setStatus(int st) throws PatchModuleException {
        try {
            this.status = st;
            log.addContent(getName(), " entered " + Types.getStatus(status));
            log.setLastStatus(Types.getStatus(status));
        } catch (Exception e) {
            e.printStackTrace();
            setStatus(Types.STATUS_FAILED);
            log.setLastStatus(getName() + ": " + Types.getStatus(status));
            log.addError(getName(), e.getMessage());
            throw new PatchModuleException(e);
        }
    }

    @Override
    public String getName() {
        return "PATCH_MOD_" + data.getTaskId();
    }

}
