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
import io.bdrc.edit.Types;
import io.bdrc.edit.helpers.Helpers;
import io.bdrc.edit.helpers.UserDataUpdate;
import io.bdrc.edit.txn.exceptions.ModuleException;
import io.bdrc.edit.txn.exceptions.PatchModuleException;
import io.bdrc.edit.users.BudaUser;
import io.bdrc.edit.users.UserDataService;

public class UserPatchModule implements BUDAEditModule {

    UserDataUpdate data;
    int status;

    public final static Logger log = LoggerFactory.getLogger(UserPatchModule.class.getName());

    public UserPatchModule(UserDataUpdate data) throws ModuleException {
        this.data = data;
        setStatus(Types.STATUS_PREPARED);
    }

    @Override
    public boolean rollback() throws ModuleException {
        // TODO Auto-generated method stub
        return false;
    }

    @Override
    public void run() throws PatchModuleException {
        RDFConnectionRemoteBuilder builder = RDFConnectionFuseki.create().destination(EditConfig.getProperty("fusekiData"));
        RDFConnectionFuseki pubFusConn = ((RDFConnectionFuseki) builder.build());
        RDFConnectionRemoteBuilder builder1 = RDFConnectionFuseki.create().destination(EditConfig.getProperty("fusekiAuthData"));
        RDFConnectionFuseki privFusConn = ((RDFConnectionFuseki) builder1.build());
        InputStream patch = new ByteArrayInputStream(data.getPatch().getBytes());
        RDFPatchReaderText rdf = new RDFPatchReaderText(patch);
        DatasetGraph dsg = data.getDatasetGraph();
        log.info("Graph to be patched :");
        ModelFactory.createModelForGraph(dsg.getGraph(NodeFactory.createURI(BudaUser.PUBLIC_PFX + data.getUserId()))).write(System.out, "TURTLE");
        // Applying changes
        RDFChangesApply apply = new RDFChangesApply(dsg);
        rdf.apply(apply);
        log.info("Graph after patching :");
        ModelFactory.createModelForGraph(dsg.getGraph(NodeFactory.createURI(BudaUser.PUBLIC_PFX + data.getUserId()))).write(System.out, "TURTLE");
        // Putting the graphs back into fuseki datasets
        for (String st : data.getGraphs()) {
            try {
                Model m = ModelFactory.createModelForGraph(dsg.getGraph(NodeFactory.createURI(st)));
                if (data.getEditPatchHeaders().getScope(st).equals(UserDataService.PUB_SCOPE)) {
                    Helpers.putModel(pubFusConn, st, m);
                }
                if (data.getEditPatchHeaders().getScope(st).equals(UserDataService.PRIV_SCOPE)) {
                    Helpers.putModel(privFusConn, st, m);
                }
            } catch (HttpException ex) {
                pubFusConn.close();
                privFusConn.close();
                // later we'll try a rollback, if possible (from previous git version a full
                // rollback should be possible)
                throw new PatchModuleException("No graph could be uploaded to fuseki as " + st);
            }
        }
        pubFusConn.close();
        privFusConn.close();
        log.info("User Patch has been applied");
    }

    @Override
    public String getName() {
        return "USER_PATCH_MODULE_" + data.getUserId();
    }

    @Override
    public String getUserId() {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public int getStatus() {
        return status;
    }

    @Override
    public void setStatus(int st) throws ModuleException {
        this.status = st;

    }

}
