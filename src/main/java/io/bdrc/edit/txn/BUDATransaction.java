package io.bdrc.edit.txn;

import java.util.TreeMap;

import javax.transaction.Status;

import io.bdrc.edit.modules.BUDAEditModule;
import io.bdrc.edit.txn.exceptions.ServiceException;
import io.bdrc.edit.txn.exceptions.ServiceSequenceException;

public class BUDATransaction {

    int status;
    int currentModule = -1;
    String id;
    String user;
    TreeMap<Integer, BUDAEditModule> modulesMap;

    public BUDATransaction(String id, String user) {
        this.id = id;
        this.user = user;
        this.modulesMap = new TreeMap<>();
    }

    /**
     * Enlist the resource specified with the transaction associated with the target
     * Transaction object.
     * 
     * @throws ServiceSequenceException
     */
    public boolean addModule(BUDAEditModule module, int order) throws ServiceSequenceException {
        if (modulesMap.containsKey(order)) {
            throw new ServiceSequenceException();
        }
        modulesMap.put(order, module);
        return true;
    }

    /**
     * Complete the transaction represented by this Transaction object or forward
     * the exception to the TransactionManager
     * 
     * @throws ServiceException
     */
    public void commit() throws Exception {
        setStatus(Status.STATUS_COMMITTED);
        for (int module : modulesMap.keySet()) {
            try {
                // log.logMsg("Running service ", servicesMap.get(svc).getName() + " SVC =" +
                // svc);
                modulesMap.get(module).run();
                currentModule = module;
                // log.logMsg("Finished Running service ", servicesMap.get(svc).getName());
            } catch (Exception e) {
                setStatus(Status.STATUS_MARKED_ROLLBACK);
                // log.logMsg("[ERROR] in BUDA Transaction Commit ", e.getMessage());
                throw e;
            }
        }
    }

    public int getStatus() {
        return status;
    }

    public void setStatus(int stat) {
        this.status = stat;
    }

    public int getCurrentModule() {
        return currentModule;
    }

    public String getId() {
        return id;
    }

    public String getUser() {
        return user;
    }

}
