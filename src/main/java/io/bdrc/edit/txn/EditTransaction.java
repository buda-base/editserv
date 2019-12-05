package io.bdrc.edit.txn;

import java.io.IOException;
import java.util.TreeMap;

import com.fasterxml.jackson.core.JsonProcessingException;

import io.bdrc.edit.Types;
import io.bdrc.edit.modules.BUDAEditModule;
import io.bdrc.edit.txn.exceptions.ServiceSequenceException;

public abstract class EditTransaction {

    TreeMap<Integer, BUDAEditModule> modulesMap;
    TransactionLog log;
    int status;
    String name;

    public abstract boolean commit() throws IOException;

    public abstract boolean finalizeLog() throws JsonProcessingException, IOException;

    public boolean addModule(BUDAEditModule module, int order) throws ServiceSequenceException {
        if (modulesMap.containsKey(order)) {
            throw new ServiceSequenceException();
        }
        modulesMap.put(order, module);
        return true;
    }

    public TransactionLog getLog() {
        return log;
    }

    public void setStatus(int stat) throws IOException {
        this.status = stat;
        log.addContent(name, " entered " + Types.getStatus(status));
    }

    public int getStatus() {
        return status;
    }

}
