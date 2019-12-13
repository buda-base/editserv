package io.bdrc.edit.txn;

import java.io.IOException;
import java.util.Date;
import java.util.TreeMap;

import javax.transaction.Status;

import org.apache.jena.atlas.logging.Log;

import io.bdrc.edit.EditConfig;
import io.bdrc.edit.Types;
import io.bdrc.edit.helpers.Helpers;
import io.bdrc.edit.helpers.UserDataUpdate;
import io.bdrc.edit.patch.PatchContent;
import io.bdrc.edit.txn.exceptions.ModuleException;
import io.bdrc.libraries.GlobalHelpers;

public class UserTransaction extends EditTransaction {

    private TransactionLog log;
    Date commitTime;
    int status;
    String name;
    UserDataUpdate data;
    int currentModule = -1;

    public UserTransaction(String patch, String editor, String userId) throws Exception {
        try {
            this.name = "TXN_USER_" + userId;
            String path = EditConfig.getProperty("usersLogRootDir") + GlobalHelpers.getTwoLettersBucket(userId) + "/";
            Helpers.createDirIfNotExists(path);
            log = new TransactionLog(path, editor, userId);
            setStatus(Types.STATUS_PREPARING);
            this.data = new UserDataUpdate(new PatchContent(patch), editor, userId);
            this.modulesMap = new TreeMap<>();
        } catch (Exception e) {
            e.printStackTrace();
            log.addError(name, e.getMessage());
            setStatus(Types.STATUS_FAILED);
            try {
                finalizeLog(log, name);
            } catch (Exception e1) {
                // TODO Auto-generated catch block
                e1.printStackTrace();
                throw new Exception(e1);
            }
        }

    }

    @Override
    public boolean commit() throws IOException, ModuleException {
        setStatus(Status.STATUS_COMMITTED);
        commitTime = new Date();
        for (int module : modulesMap.keySet()) {
            try {
                currentModule = module;
                modulesMap.get(module).run();
            } catch (ModuleException e) {
                setStatus(Status.STATUS_MARKED_ROLLBACK);
                log.addError(name, e.getMessage());
                return modulesMap.get(currentModule).rollback();
            } finally {
                finalizeLog(log, name);
            }
        }
        setStatus(Types.STATUS_SUCCESS);
        try {
            finalizeLog(log, name);
        } catch (Exception e) {
            e.printStackTrace();
            Log.error(this, "Edit User Transaction Manager failed to close log properly for User Transaction " + getName() + "finishing with status:" + Types.getStatus(getStatus()), e);
        }
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

    public String getName() {
        return this.name;
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
