package io.bdrc.edit.modules;

import java.io.File;

import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.bdrc.edit.EditConfig;
import io.bdrc.edit.Types;
import io.bdrc.edit.helpers.DataUpdate;
import io.bdrc.edit.patch.TaskGitManager;
import io.bdrc.edit.txn.TransactionLog;
import io.bdrc.edit.txn.exceptions.FinalizerModuleException;
import io.bdrc.edit.txn.exceptions.ModuleException;

public class FinalizerModule implements BUDAEditModule {

    /*
     * This service takes care of finalizing the transaction and do the necessary
     * cleanup. It makes sure the queue is in a consistent state, give the
     * transaction its final status and store the transaction log properly
     */

    public final static Logger logger = LoggerFactory.getLogger(FinalizerModule.class.getName());

    TransactionLog log;
    int status;
    DataUpdate data;

    public FinalizerModule(DataUpdate data, TransactionLog log) throws FinalizerModuleException {
        super();
        this.data = data;
        this.log = log;
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
            // 1) move task from user "stashed" to user "processed" directories
            FileUtils.copyFile(new File(EditConfig.getProperty("gitTaskRepo") + getUserId() + "/" + data.getTaskId() + ".patch"), new File(EditConfig.getProperty("gitTransactDir") + data.getTaskId() + ".patch"));
            TaskGitManager.deleteTask(getUserId(), data.getTaskId());
            // 2) close and write transaction log
            logger.info("Running Txn Closer Service for task {}", data.getTaskId());
            setStatus(Types.STATUS_SUCCESS);
        } catch (Exception e) {
            e.printStackTrace();
            setStatus(Types.STATUS_FAILED);
            log.addError(getName(), e.getMessage());
            throw new FinalizerModuleException(e);
        }
    }

    @Override
    public int getStatus() {
        return status;
    }

    @Override
    public void setStatus(int st) throws FinalizerModuleException {
        try {
            status = st;
            log.addContent(getName(), " entered " + Types.getStatus(status));
            log.setLastStatus(Types.getStatus(status));
        } catch (Exception e) {
            e.printStackTrace();
            setStatus(Types.STATUS_FAILED);
            log.setLastStatus(getName() + ": " + Types.getStatus(status));
            log.addError(getName(), e.getMessage());
            throw new FinalizerModuleException(e);
        }
    }

    @Override
    public String getName() {
        return "FINAL_MOD_" + data.getTaskId();
    }

    @Override
    public String getUserId() {
        return data.getUserId();
    }

}
