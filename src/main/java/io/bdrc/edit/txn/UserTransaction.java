package io.bdrc.edit.txn;

import java.io.IOException;
import java.util.Date;
import java.util.TreeMap;

import javax.transaction.Status;

import com.fasterxml.jackson.core.JsonProcessingException;

import io.bdrc.edit.EditConfig;
import io.bdrc.edit.Types;
import io.bdrc.edit.helpers.UserDataUpdate;
import io.bdrc.edit.patch.PatchContent;
import io.bdrc.edit.txn.exceptions.DataUpdateException;
import io.bdrc.edit.txn.exceptions.ModuleException;

public class UserTransaction extends EditTransaction {

    private TransactionLog log;
    Date commitTime;
    int status;
    String name;
    UserDataUpdate data;
    int currentModule = -1;

    public UserTransaction(String patch, String editor, String userId) throws DataUpdateException {
        String path = EditConfig.getProperty("logRootDir") + editor + "/" + "user/" + userId;
        log = new TransactionLog(path, editor, userId);
        this.data = new UserDataUpdate(new PatchContent(patch), editor, userId);
        this.modulesMap = new TreeMap<>();
        this.name = "TXN_USER" + userId;
    }

    @Override
    public boolean commit() throws IOException {
        setStatus(Status.STATUS_COMMITTED);
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

    public void setStatus(int stat) throws IOException {
        this.status = stat;
        log.addContent(name, " entered " + Types.getStatus(status));
    }

    public TransactionLog getLog() {
        return log;
    }

    public int getStatus() {
        return status;
    }

    @Override
    public boolean finalizeLog() throws JsonProcessingException, IOException {
        // TODO Auto-generated method stub
        return false;
    }

    public UserDataUpdate getData() {
        return data;
    }

    public void setData(UserDataUpdate data) {
        this.data = data;
    }

    public Date getCommitTime() {
        return commitTime;
    }

    public int getCurrentModule() {
        return currentModule;
    }

}
