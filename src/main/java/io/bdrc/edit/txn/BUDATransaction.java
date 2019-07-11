package io.bdrc.edit.txn;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.TreeMap;

import javax.transaction.Status;

import com.fasterxml.jackson.core.JsonProcessingException;

import io.bdrc.edit.EditConfig;
import io.bdrc.edit.Types;
import io.bdrc.edit.helpers.DataUpdate;
import io.bdrc.edit.modules.BUDAEditModule;
import io.bdrc.edit.txn.exceptions.ModuleException;
import io.bdrc.edit.txn.exceptions.ServiceSequenceException;

public class BUDATransaction {

    int status;
    int currentModule = -1;
    String id;
    String name;
    String user;
    TreeMap<Integer, BUDAEditModule> modulesMap;
    DataUpdate data;
    TransactionLog log;

    public BUDATransaction(DataUpdate data) {
        this.data = data;
        this.id = data.getTaskId();
        this.name = "TXN_" + data.getTaskId();
        this.user = data.getUserId();
        this.modulesMap = new TreeMap<>();
        log = new TransactionLog(data.getTsk());
    }

    public TransactionLog getLog() {
        return log;
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
     * @throws ModuleException
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
                log.addError(name, e.getMessage());
                throw e;
            }
        }
        setStatus(Types.STATUS_SUCCESS);
        finalizeLog();
    }

    public boolean finalizeLog() throws JsonProcessingException, IOException {
        String path = EditConfig.getProperty("logRootDir") + data.getUserId() + "/";
        File f = new File(path);
        if (!f.exists()) {
            f.mkdir();
        }
        boolean ok = true;
        BufferedWriter writer = new BufferedWriter(new FileWriter(path + name + ".log"));
        writer.write(TransactionLog.asJson(log));
        writer.close();
        return ok;
    }

    public int getStatus() {
        return status;
    }

    public void setStatus(int stat) {
        this.status = stat;
        log.addContent(name, System.lineSeparator() + name + " entered " + Types.getStatus(status));
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
