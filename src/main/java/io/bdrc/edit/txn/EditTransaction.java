package io.bdrc.edit.txn;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.TreeMap;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.bdrc.edit.modules.BUDAEditModule;
import io.bdrc.edit.txn.exceptions.ModuleException;
import io.bdrc.edit.txn.exceptions.ServiceSequenceException;

public abstract class EditTransaction {

    TreeMap<Integer, BUDAEditModule> modulesMap;

    public abstract boolean commit() throws IOException, ModuleException;

    public boolean finalizeLog(TransactionLog log, String name) throws JsonProcessingException, IOException {
        File f = new File(log.getPath());
        System.out.println("LOG PATH >> " + log.getPath() + " exist ? =" + f.exists());
        if (!f.exists()) {
            f.mkdir();
        }
        System.out.println("LOG PATH AFTER >> " + log.getPath() + " exist ? =" + f.exists());
        boolean ok = true;
        HashMap<String, HashMap<String, String>> obj = new HashMap<>();
        obj.put(TransactionLog.HEADER, log.header);
        obj.put(TransactionLog.CONTENT, log.content);
        obj.put(TransactionLog.ERROR, log.error);
        ObjectMapper mapper = new ObjectMapper();
        FileOutputStream fos = new FileOutputStream(new File(log.getPath() + name + ".log"));
        mapper.writerWithDefaultPrettyPrinter().writeValue(fos, obj);
        fos.close();
        return ok;
    }

    public boolean addModule(BUDAEditModule module, int order) throws ServiceSequenceException {
        if (modulesMap.containsKey(order)) {
            throw new ServiceSequenceException();
        }
        modulesMap.put(order, module);
        return true;
    }
}
