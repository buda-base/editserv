package io.bdrc.edit.modules;

import java.io.ByteArrayInputStream;
import java.io.InputStream;

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
import io.bdrc.edit.helpers.Helpers;
import io.bdrc.edit.helpers.UserDataUpdate;
import io.bdrc.edit.txn.TransactionLog;
import io.bdrc.edit.txn.exceptions.GitRevisionModuleException;
import io.bdrc.edit.txn.exceptions.ModuleException;
import io.bdrc.edit.txn.exceptions.PatchModuleException;
import io.bdrc.libraries.GitHelpers;
import io.bdrc.libraries.GlobalHelpers;

public class GitUserRevisionModule implements BUDAEditModule {

    public final static Logger logger = LoggerFactory.getLogger(GitUserRevisionModule.class.getName());

    TransactionLog log;
    UserDataUpdate data;
    int status;

    public GitUserRevisionModule(UserDataUpdate data, TransactionLog log) throws ModuleException {
        this.log = log;
        this.data = data;
        setStatus(Types.STATUS_PREPARED);
    }

    private String buildRevisionPatch() throws GitRevisionModuleException {
        String rev = data.getGitRevisionInfo();
        StringBuffer sb = new StringBuffer();
        try {
            sb.append("TX .");
            String resId = data.getUserId();
            String line = "A <" + EditConstants.BDA + resId + "> <http://purl.bdrc.io/ontology/admin/gitRevision> \"" + rev + "\" <" + EditConstants.BDA + resId + "> .";
            sb.append(System.lineSeparator());
            sb.append(line);
            sb.append(System.lineSeparator());
            sb.append("TC .");
        } catch (Exception e) {
            logger.error("GitUserRevisionModule buildRevisionPatch failed ", e);
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
    public void run() throws ModuleException {
        setStatus(Types.STATUS_PROCESSING);
        RDFConnectionFuseki pubFusConn = null;
        RDFConnectionFuseki privFusConn = null;
        try {
            InputStream ptc = new ByteArrayInputStream(buildRevisionPatch().getBytes());
            RDFPatchReaderText rdf = new RDFPatchReaderText(ptc);
            String repoName = EditConfig.getProperty("usersGitLocalRoot");
            String gitPath = GlobalHelpers.getTwoLettersBucket(data.getUserId()) + "/" + data.getUserId() + ".trig";
            DatasetGraph dsg = Helpers.buildGraphFromTrig(GitHelpers.getGitHeadFileContent(repoName, gitPath));
            RDFConnectionRemoteBuilder builder = RDFConnectionFuseki.create().destination(EditConfig.getProperty("fusekiData"));
            pubFusConn = ((RDFConnectionFuseki) builder.build());
            RDFConnectionRemoteBuilder builder1 = RDFConnectionFuseki.create().destination(EditConfig.getProperty("fusekiAuthData"));
            privFusConn = ((RDFConnectionFuseki) builder1.build());
            // Applying changes
            RDFChangesApply apply = new RDFChangesApply(dsg);
            rdf.apply(apply);
            Model m = ModelFactory.createModelForGraph(dsg.getGraph(NodeFactory.createURI(EditConstants.BDA + data.getUserId())));
            Helpers.putModel(pubFusConn, EditConstants.BDA + data.getUserId(), m);
            Helpers.putModel(privFusConn, EditConstants.BDA + data.getUserId(), m);
            pubFusConn.close();
            privFusConn.close();
            setStatus(Types.STATUS_SUCCESS);
        } catch (Exception e) {
            logger.error("GitUserRevisionModule failed ", e);
            pubFusConn.close();
            privFusConn.close();
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
    public void setStatus(int st) throws ModuleException {
        try {
            this.status = st;
            log.addContent(getName(), " entered " + Types.getStatus(status));
            log.setLastStatus(Types.getStatus(status));
        } catch (Exception e) {
            logger.error("GitUserRevisionModule set status failed ", e);
            setStatus(Types.STATUS_FAILED);
            log.setLastStatus(getName() + ": " + Types.getStatus(status));
            log.addError(getName(), e.getMessage());
            throw new PatchModuleException(e);
        }
    }

    @Override
    public String getName() {
        return "USER_GIT_REV_MOD_" + data.getUserId();
    }

    @Override
    public String getUserId() {
        return data.getUserId();
    }

}
