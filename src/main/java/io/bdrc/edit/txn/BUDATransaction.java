package io.bdrc.edit.txn;

import java.util.TreeMap;

import javax.transaction.Status;

import io.bdrc.edit.service.BUDAEditService;
import io.bdrc.edit.txn.exceptions.ServiceException;
import io.bdrc.edit.txn.exceptions.ServiceSequenceException;

public class BUDATransaction {

    int status;
    int currentSvc = -1;
    String id;
    String user;
    TreeMap<Integer, BUDAEditService> servicesMap;

    public BUDATransaction(String id, String user) {
        this.id = id;
        this.user = user;
        this.servicesMap = new TreeMap<>();
    }

    /**
     * Enlist the resource specified with the transaction associated with the target
     * Transaction object.
     * 
     * @throws ServiceSequenceException
     */
    public boolean enlistService(BUDAEditService serv, int order) throws ServiceSequenceException {
        if (servicesMap.containsKey(order)) {
            throw new ServiceSequenceException();
        }
        servicesMap.put(order, serv);
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
        for (int svc : servicesMap.keySet()) {
            try {
                // log.logMsg("Running service ", servicesMap.get(svc).getName() + " SVC =" +
                // svc);
                servicesMap.get(svc).run();
                currentSvc = svc;
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

    public int getCurrentSvc() {
        return currentSvc;
    }

    public String getId() {
        return id;
    }

    public String getUser() {
        return user;
    }

}
