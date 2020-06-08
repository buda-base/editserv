package io.bdrc.edit.user;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.bdrc.edit.TransactionLog;
import io.bdrc.edit.Types;
import io.bdrc.edit.helpers.UserDataUpdate;
import io.bdrc.edit.txn.exceptions.GitPatchModuleException;
import io.bdrc.edit.txn.exceptions.ModuleException;
import io.bdrc.edit.txn.exceptions.PatchModuleException;

public class GitUserPatchModule implements BUDAEditModule {

    public final static Logger logger = LoggerFactory.getLogger(GitUserPatchModule.class.getName());

    TransactionLog log;
    UserDataUpdate data;
    int status;

    public GitUserPatchModule(UserDataUpdate data, TransactionLog log) throws PatchModuleException {
        this.log = log;
        this.data = data;
        setStatus(Types.STATUS_PREPARED);
    }

    @Override
    public boolean rollback() throws ModuleException {
        // TODO Auto-generated method stub
        return false;
    }

    @Override
    public void run() throws ModuleException {
        setStatus(Types.STATUS_PROCESSING);
        try {
            BudaUser.update(data);
            setStatus(Types.STATUS_SUCCESS);
        } catch (Exception e) {
            setStatus(Types.STATUS_FAILED);
            logger.error("GitUserPatchModule update failed for userid " + data.getUserId(), e);
            log.addError(getName(), e.getMessage());
            throw new GitPatchModuleException(e);
        }
        logger.info("Git User Patch has been applied");
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
            logger.error("GitUserPatchModule buildRevisionPatch failed ", e);
            setStatus(Types.STATUS_FAILED);
            log.setLastStatus(getName() + ": " + Types.getStatus(status));
            log.addError(getName(), e.getMessage());
            throw new PatchModuleException(e);
        }
    }

    public String getName() {
        return "USER_GIT_PATCH_MOD_" + data.getUserId();
    }

    @Override
    public String getUserId() {
        return data.getUserId();
    }

}
