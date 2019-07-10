package io.bdrc.edit.modules;

import java.io.File;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.bdrc.edit.EditConfig;
import io.bdrc.edit.patch.Task;
import io.bdrc.edit.patch.TaskGitManager;
import io.bdrc.edit.txn.exceptions.FinalizerModuleException;
import io.bdrc.edit.txn.exceptions.ServiceException;

public class FinalizerModule implements BUDAEditModule {

    /*
     * This service takes care of finalizing the transaction and do the necessary
     * cleanup It makes sure the queue is in a consistent state, give the
     * transaction its final status and store the transaction log properly
     */

    public final static Logger log = LoggerFactory.getLogger(FinalizerModule.class.getName());

    Task tsk;

    public FinalizerModule(Task tsk) {
        super();
        this.tsk = tsk;
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
            new File(EditConfig.getProperty("gitTaskRepo") + tsk.getUser() + "/" + tsk.getId() + ".patch").renameTo(new File(EditConfig.getProperty("gitTransactDir") + tsk.getId() + ".patch"));
            TaskGitManager.deleteTask(tsk.getUser(), tsk.getId());
            // 2) close and write transaction log
            log.info("Running Txn Closer Service for task {}", tsk);
        } catch (Exception e) {
            e.printStackTrace();
            throw new FinalizerModuleException(e);
        }
    }

    @Override
    public int getStatus() {
        // TODO Auto-generated method stub
        return 0;
    }

    @Override
    public void setStatus(int st) {
        // TODO Auto-generated method stub

    }

    @Override
    public String getId() {
        // TODO Auto-generated method stub
        return null;
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
