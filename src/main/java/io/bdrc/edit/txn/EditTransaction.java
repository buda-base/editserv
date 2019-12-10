package io.bdrc.edit.txn;

import java.io.IOException;
import java.util.TreeMap;

import com.fasterxml.jackson.core.JsonProcessingException;

import io.bdrc.edit.modules.BUDAEditModule;
import io.bdrc.edit.txn.exceptions.ServiceSequenceException;

public abstract class EditTransaction {

    TreeMap<Integer, BUDAEditModule> modulesMap;

    public abstract boolean commit() throws IOException;

    public abstract boolean finalizeLog() throws JsonProcessingException, IOException;

    public boolean addModule(BUDAEditModule module, int order) throws ServiceSequenceException {
        if (modulesMap.containsKey(order)) {
            throw new ServiceSequenceException();
        }
        modulesMap.put(order, module);
        return true;
    }
}
