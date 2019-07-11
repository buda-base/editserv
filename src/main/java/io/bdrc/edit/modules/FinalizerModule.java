package io.bdrc.edit.modules;

import java.io.File;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.bdrc.edit.EditConfig;
import io.bdrc.edit.Types;
import io.bdrc.edit.helpers.DataUpdate;
import io.bdrc.edit.patch.TaskGitManager;
import io.bdrc.edit.txn.TransactionLog;
import io.bdrc.edit.txn.exceptions.FinalizerModuleException;
import io.bdrc.edit.txn.exceptions.ServiceException;

public class FinalizerModule implements BUDAEditModule {

    /*
     * This service takes care of finalizing the transaction and do the necessary
     * cleanup It makes sure the queue is in a consistent state, give the
     * transaction its final status and store the transaction log properly
     */

    public final static Logger logger = LoggerFactory.getLogger(FinalizerModule.class.getName());
    TransactionLog log;

    // Task tsk;
    int status;
    String name;
    DataUpdate data;

    public FinalizerModule(DataUpdate data, TransactionLog log) {
        super();
        this.data = data;
        this.log = log;
        this.name = "FINAL_MOD_" + data.getTaskId();
        setStatus(Types.STATUS_PREPARED);
        log.addContent(name, name + " entered " + Types.getStatus(status));
    }

    @Override
    public boolean rollback() throws ServiceException {
        // TODO Auto-generated method stub
        return false;
    }

    @Override
    public void run() throws ServiceException {
        try {
            // 1) move task from user "stashed" to user "processed" directories
            new File(EditConfig.getProperty("gitTaskRepo") + getUserId() + "/" + getId() + ".patch").renameTo(new File(EditConfig.getProperty("gitTransactDir") + getId() + ".patch"));
            TaskGitManager.deleteTask(getUserId(), getId());
            // 2) close and write transaction log
            logger.info("Running Txn Closer Service for task {}", data.getTaskId());
        } catch (Exception e) {
            e.printStackTrace();
            throw new FinalizerModuleException(e);
        }
    }

    @Override
    public int getStatus() {
        return status;
    }

    @Override
    public void setStatus(int st) {
        status = st;
    }

    @Override
    public String getId() {
        return data.getTaskId();
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public String getUserId() {
        return data.getUserId();
    }

}
