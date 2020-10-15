package io.bdrc.edit.user;

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
import io.bdrc.edit.EditConstants;
import io.bdrc.edit.TransactionLog;
import io.bdrc.edit.Types;
import io.bdrc.edit.helpers.Helpers;
import io.bdrc.edit.helpers.UserDataUpdate;
import io.bdrc.edit.txn.exceptions.ModuleException;
import io.bdrc.edit.txn.exceptions.PatchModuleException;

public class UserPatchModule implements BUDAEditModule {

    public static String NL = System.lineSeparator();
    UserDataUpdate data;
    int status;
    TransactionLog log;

    public final static Logger logger = LoggerFactory.getLogger(UserPatchModule.class.getName());

    public UserPatchModule(UserDataUpdate data, TransactionLog log) throws ModuleException {
        this.data = data;
        this.log = log;
        setStatus(Types.STATUS_PREPARED);
    }

    public static String getSetActivePatch(String userId, boolean active) {
        StringBuffer buff = new StringBuffer();
        buff.append("TX ." + NL);
        buff.append(" D <" + BudaUser.BDU_PFX + userId + "> <http://purl.bdrc.io/ontology/ext/user/isActive> \"" + Boolean.toString(!active) + "\" <"
                + BudaUser.PRIVATE_PFX + userId + "> .");
        buff.append(NL + " A <" + BudaUser.BDU_PFX + userId + "> <http://purl.bdrc.io/ontology/ext/user/isActive> \"" + Boolean.toString(active)
                + "\" <" + BudaUser.PRIVATE_PFX + userId + "> .");
        buff.append(NL + "TC ." + NL);
        return buff.toString();
    }

    @Override
    public boolean rollback() throws ModuleException {
        // TODO Auto-generated method stub
        return false;
    }

    @Override
    public void run() throws ModuleException {
        try {
            setStatus(Types.STATUS_PROCESSING);
            RDFConnectionRemoteBuilder builder = RDFConnectionFuseki.create().destination(EditConfig.getProperty("fusekiData"));
            RDFConnectionFuseki pubFusConn = ((RDFConnectionFuseki) builder.build());
            RDFConnectionRemoteBuilder builder1 = RDFConnectionFuseki.create().destination(EditConfig.getProperty("fusekiAuthData"));
            RDFConnectionFuseki privFusConn = ((RDFConnectionFuseki) builder1.build());
            InputStream patch = new ByteArrayInputStream(data.getPatch().getBytes());
            RDFPatchReaderText rdf = new RDFPatchReaderText(patch);
            DatasetGraph dsg = data.getDatasetGraph();
            logger.info("Graph to be patched :");
            ModelFactory.createModelForGraph(dsg.getGraph(NodeFactory.createURI(BudaUser.PUBLIC_PFX + data.getUserId()))).write(System.out, "TURTLE");
            // Applying changes
            RDFChangesApply apply = new RDFChangesApply(dsg);
            rdf.apply(apply);
            logger.info("Graph after patching :");
            ModelFactory.createModelForGraph(dsg.getGraph(NodeFactory.createURI(BudaUser.PUBLIC_PFX + data.getUserId()))).write(System.out, "TURTLE");
            // Putting the graphs back into fuseki datasets
            Model adm = ModelFactory.createModelForGraph(dsg.getGraph(NodeFactory.createURI(EditConstants.BDA + data.getUserId())));
            Helpers.putModel(pubFusConn, EditConstants.BDA + data.getUserId(), adm);
            Helpers.putModel(privFusConn, EditConstants.BDA + data.getUserId(), adm);
            for (String st : data.getGraphs()) {
                try {
                    Model m = ModelFactory.createModelForGraph(dsg.getGraph(NodeFactory.createURI(st)));
                    if (data.getEditPatchHeaders().getScope(st).equals(BudaUser.PUB_SCOPE)) {
                        Helpers.putModel(pubFusConn, st, m);
                        Helpers.putModel(privFusConn, st, m);
                    }
                    if (data.getEditPatchHeaders().getScope(st).equals(BudaUser.PRIV_SCOPE)) {
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
            setStatus(Types.STATUS_SUCCESS);
            logger.info("User Patch has been applied");
        } catch (Exception e) {
            logger.error("UserPatchModule failed ", e);
            setStatus(Types.STATUS_FAILED);
            log.addError(getName(), e.getMessage());
            throw new PatchModuleException(e);
        }
    }

    @Override
    public String getName() {
        return "USER_PATCH_MODULE_" + data.getUserId();
    }

    @Override
    public String getUserId() {
        return data.getUserId();
    }

    @Override
    public int getStatus() {
        return status;
    }

    @Override
    public void setStatus(int st) throws ModuleException {
        try {
            this.status = st;
            log.addContent(getName(), " entered " + Types.getStatus(status));
            log.setLastStatus(Types.getStatus(status));
        } catch (Exception e) {
            logger.error("UserPatchModule set status failed ", e);
            setStatus(Types.STATUS_FAILED);
            log.setLastStatus(getName() + ": " + Types.getStatus(status));
            log.addError(getName(), e.getMessage());
            throw new PatchModuleException(e);
        }
    }

}
