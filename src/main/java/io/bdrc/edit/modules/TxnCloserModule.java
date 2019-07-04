package io.bdrc.edit.modules;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.bdrc.edit.patch.Task;
import io.bdrc.edit.txn.exceptions.ServiceException;

public class TxnCloserModule implements BUDAEditModule {

    /*
     * This service takes care of finalizing the transaction and do the necessary
     * cleanup It makes sure the queue is in a consistent state, give the
     * transaction its final status and store the transaction log properly
     */

    public final static Logger log = LoggerFactory.getLogger(TxnCloserModule.class.getName());

    Task tsk;

    public TxnCloserModule(Task tsk) {
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
        // TODO Auto-generated method stub
        log.info("Running Txn Closer Service for task {}", tsk);
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
