package io.bdrc.edit.txn;

import java.util.TreeMap;

import io.bdrc.edit.service.BUDAEditService;
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
    public boolean enlistResource(BUDAEditService serv, int order) throws ServiceSequenceException {
        if (servicesMap.containsKey(order)) {
            throw new ServiceSequenceException();
        }
        servicesMap.put(order, serv);
        return true;
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
