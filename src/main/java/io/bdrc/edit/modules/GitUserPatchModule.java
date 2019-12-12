package io.bdrc.edit.modules;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.bdrc.edit.Types;
import io.bdrc.edit.helpers.UserDataUpdate;
import io.bdrc.edit.txn.TransactionLog;
import io.bdrc.edit.txn.exceptions.GitPatchModuleException;
import io.bdrc.edit.txn.exceptions.ModuleException;
import io.bdrc.edit.users.BudaUser;

public class GitUserPatchModule implements BUDAEditModule {

    public final static Logger logger = LoggerFactory.getLogger(GitUserPatchModule.class.getName());

    TransactionLog log;
    UserDataUpdate data;
    int status;

    public GitUserPatchModule(UserDataUpdate data, TransactionLog log) {
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
        try {
            BudaUser.update(data);
        } catch (Exception e) {
            e.printStackTrace();
            setStatus(Types.STATUS_FAILED);
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
    public void setStatus(int st) {
        this.status = st;
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
