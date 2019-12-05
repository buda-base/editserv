package io.bdrc.edit.txn;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.TreeMap;

import javax.transaction.Status;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.bdrc.edit.EditConfig;
import io.bdrc.edit.Types;
import io.bdrc.edit.helpers.DataUpdate;
import io.bdrc.edit.patch.Task;
import io.bdrc.edit.txn.exceptions.ModuleException;

public class BUDATransaction extends EditTransaction {

    String id;
    String user;
    DataUpdate data;
    Date commitTime;
    int currentModule = -1;

    public BUDATransaction(Task t) throws Exception {
        try {
            log = new TransactionLog(t);
            this.name = "TXN_" + t.getId();
            setStatus(Types.STATUS_PREPARING);
            this.id = t.getId();
            this.user = t.getUser();
            this.data = new DataUpdate(t);
            this.modulesMap = new TreeMap<>();
        } catch (Exception e) {
            e.printStackTrace();
            log.addError(name, e.getMessage());
            setStatus(Types.STATUS_FAILED);
            try {
                finalizeLog();
            } catch (Exception e1) {
                // TODO Auto-generated catch block
                e1.printStackTrace();
                throw new Exception(e1);
            }
        }
    }

    public String getName() {
        return name;
    }

    public DataUpdate getData() {
        return data;
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
        DateFormat dateFormat = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss");
        commitTime = new Date();
        for (int module : modulesMap.keySet()) {
            try {
                modulesMap.get(module).run();
                currentModule = module;
            } catch (ModuleException e) {
                setStatus(Status.STATUS_MARKED_ROLLBACK);
                log.addError(name, e.getMessage());
                return false;
            } finally {
                finalizeLog();
            }
        }
        setStatus(Types.STATUS_SUCCESS);
        finalizeLog();
        return true;
    }

    @Override
    public boolean finalizeLog() throws JsonProcessingException, IOException {
        String path = EditConfig.getProperty("logRootDir") + user + "/";
        File f = new File(path);
        if (!f.exists()) {
            f.mkdir();
        }
        boolean ok = true;
        HashMap<String, HashMap<String, String>> obj = new HashMap<>();
        obj.put(TransactionLog.HEADER, log.header);
        obj.put(TransactionLog.CONTENT, log.content);
        obj.put(TransactionLog.ERROR, log.error);
        ObjectMapper mapper = new ObjectMapper();
        FileOutputStream fos = new FileOutputStream(new File(path + name + ".log"));
        mapper.writerWithDefaultPrettyPrinter().writeValue(fos, obj);
        fos.close();
        return ok;
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
