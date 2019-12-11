package io.bdrc.edit.modules;

import org.apache.jena.graph.NodeFactory;
import org.apache.jena.rdf.model.ModelFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.bdrc.edit.Types;
import io.bdrc.edit.helpers.UserDataUpdate;
import io.bdrc.edit.txn.TransactionLog;
import io.bdrc.edit.txn.exceptions.GitPatchModuleException;
import io.bdrc.edit.txn.exceptions.ModuleException;
import io.bdrc.edit.users.UserDataService;

public class GitUserPatchModule implements BUDAEditModule {

    public final static Logger logger = LoggerFactory.getLogger(GitUserPatchModule.class.getName());

    TransactionLog log;
    UserDataUpdate data;

    public GitUserPatchModule(UserDataUpdate data, TransactionLog log) {
        this.log = log;
        this.data = data;
    }

    @Override
    public boolean rollback() throws ModuleException {
        // TODO Auto-generated method stub
        return false;
    }

    @Override
    public void run() throws ModuleException {
        logger.info("Graph from user update in gitUserPatchModule :");
        ModelFactory.createModelForGraph(data.getDatasetGraph().getGraph(NodeFactory.createURI("http://purl.bdrc.io/graph-nc/user/" + data.getUserId()))).write(System.out, "TURTLE");
        try {
            UserDataService.update(data);
        } catch (Exception e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
            setStatus(Types.STATUS_FAILED);
            log.addError(getName(), e.getMessage());
            throw new GitPatchModuleException(e);
        }
        logger.info("Git User Patch has been applied");
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

    public String getName() {
        return "USER_GIT_PATCH_MOD_" + data.getUserId();
    }

    @Override
    public String getUserId() {
        // TODO Auto-generated method stub
        return null;
    }

}
