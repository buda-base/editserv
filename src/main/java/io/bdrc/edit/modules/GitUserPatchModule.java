package io.bdrc.edit.modules;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.bdrc.edit.txn.TransactionLog;
import io.bdrc.edit.txn.exceptions.ModuleException;

public class GitUserPatchModule implements BUDAEditModule {

    public final static Logger logger = LoggerFactory.getLogger(GitUserPatchModule.class.getName());

    TransactionLog log;

    public GitUserPatchModule(TransactionLog log) {
        this.log = log;
    }

    @Override
    public boolean rollback() throws ModuleException {
        // TODO Auto-generated method stub
        return false;
    }

    @Override
    public void run() throws ModuleException {
        // TODO Auto-generated method stub
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

    @Override
    public String getName() {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public String getUserId() {
        // TODO Auto-generated method stub
        return null;
    }

}
