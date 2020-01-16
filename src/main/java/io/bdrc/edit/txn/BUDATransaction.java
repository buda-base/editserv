package io.bdrc.edit.txn;

import java.io.IOException;
import java.util.Date;
import java.util.TreeMap;

import javax.transaction.Status;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.bdrc.edit.EditConfig;
import io.bdrc.edit.Types;
import io.bdrc.edit.helpers.DataUpdate;
import io.bdrc.edit.patch.Task;
import io.bdrc.edit.txn.exceptions.ModuleException;

public class BUDATransaction extends EditTransaction {

    String id;
    String user;
    String name;
    int status;
    DataUpdate data;
    Date commitTime;
    TransactionLog log;
    int currentModule = -1;

    public final static Logger logger = LoggerFactory.getLogger(BUDATransaction.class.getName());

    public BUDATransaction(Task t) throws Exception {
        try {
            log = new TransactionLog(EditConfig.getProperty("logRootDir") + user + "/", t);
            this.name = "TXN_" + t.getId();
            setStatus(Types.STATUS_PREPARING);
            this.id = t.getId();
            this.user = t.getUser();
            this.data = new DataUpdate(t);
            this.modulesMap = new TreeMap<>();
        } catch (Exception e) {
            logger.error("BudaTransaction failed to initialized", e);
            log.addError(name, e.getMessage());
            setStatus(Types.STATUS_FAILED);
            try {
                finalizeLog(log, name);
            } catch (Exception e1) {
                logger.error("BudaTransaction failed to initialized", e1);
                throw new Exception(e1);
            }
        }
    }

    public void setStatus(int stat) throws IOException {
        this.status = stat;
        log.addContent(name, " entered " + Types.getStatus(status));
    }

    public String getName() {
        return name;
    }

    public int getStatus() {
        return status;
    }

    public DataUpdate getData() {
        return data;
    }

    public TransactionLog getLog() {
        return log;
    }

    /**
     * Complete the transaction represented by this Transaction object or forward
     * the exception to the TransactionManager
     * 
     * @throws ModuleException
     * @throws IOException
     */
    @Override
    public boolean commit() throws IOException {
        setStatus(Status.STATUS_COMMITTED);
        commitTime = new Date();
        for (int module : modulesMap.keySet()) {
            try {
                modulesMap.get(module).run();
                currentModule = module;
            } catch (ModuleException e) {
                logger.error("BudaTransaction commit failed ", e);
                setStatus(Status.STATUS_MARKED_ROLLBACK);
                log.addError(name, e.getMessage());
                return false;
            } finally {
                finalizeLog(log, name);
            }
        }
        setStatus(Types.STATUS_SUCCESS);
        finalizeLog(log, name);
        return true;
    }

    public String getId() {
        return id;
    }

    public String getUser() {
        return user;
    }

    public Date getCommitTime() {
        return commitTime;
    }

    public int getCurrentModule() {
        return currentModule;
    }

}
