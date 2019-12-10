package io.bdrc.edit.modules;

import java.io.ByteArrayInputStream;
import java.io.InputStream;

import org.apache.jena.atlas.web.HttpException;
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
import io.bdrc.edit.helpers.UserDataUpdate;
import io.bdrc.edit.txn.UserTransaction;
import io.bdrc.edit.txn.exceptions.ModuleException;
import io.bdrc.edit.txn.exceptions.PatchModuleException;

public class UserPatchModule implements BUDAEditModule {

    private String type;
    UserDataUpdate data;
    String fusekiUrl;

    public final static Logger log = LoggerFactory.getLogger(UserPatchModule.class.getName());

    public UserPatchModule(UserDataUpdate data, String type) throws ModuleException {
        this.data = data;
        this.type = type;
        this.fusekiUrl = EditConfig.getProperty("fusekiData");
        if (!type.equals(UserTransaction.TX_PUB_TYPE) && !type.equals(UserTransaction.TX_PRIV_TYPE)) {
            throw new ModuleException("Invalid transaction type: must be public or private");
        }
        if (type.equals(UserTransaction.TX_PRIV_TYPE)) {
            this.fusekiUrl = EditConfig.getProperty("fusekiAuthData");
        }
    }

    @Override
    public boolean rollback() throws ModuleException {
        // TODO Auto-generated method stub
        return false;
    }

    @Override
    public void run() throws PatchModuleException {
        InputStream patch = new ByteArrayInputStream(data.getPatch().getBytes());
        RDFPatchReaderText rdf = new RDFPatchReaderText(patch);
        RDFConnectionRemoteBuilder builder = RDFConnectionFuseki.create().destination(fusekiUrl);
        RDFConnectionFuseki fusConn = ((RDFConnectionFuseki) builder.build());
        DatasetGraph dsg = data.getDatasetGraph();
        // Applying changes
        RDFChangesApply apply = new RDFChangesApply(dsg);
        rdf.apply(apply);

        // Putting the graphs back into main fuseki dataset
        for (String st : data.getGraphs()) {
            try {
                Model m = ModelFactory.createModelForGraph(dsg.getGraph(NodeFactory.createURI(st)));
                // Helpers.putModel(fusConn, st, m);
            } catch (HttpException ex) {
                throw new PatchModuleException("No graph could be uploaded to fuseki as " + st);
            }
        }
        log.info("User Patch has been applied");
    }

    @Override
    public String getName() {
        return "USER_PATCH_MODULE_" + type + "_" + data.getUserId();
    }

    @Override
    public String getUserId() {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public int getStatus() {
        // TODO Auto-generated method stub
        return 0;
    }

    @Override
    public void setStatus(int st) throws ModuleException {
        // TODO Auto-generated method stub

    }

}
