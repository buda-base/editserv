package io.bdrc.edit.txn;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.TreeMap;

import javax.mail.MessagingException;
import javax.mail.internet.AddressException;
import javax.transaction.Status;

import org.apache.jena.atlas.logging.Log;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.bdrc.auth.model.User;
import io.bdrc.edit.EditConfig;
import io.bdrc.edit.Types;
import io.bdrc.edit.helpers.Helpers;
import io.bdrc.edit.helpers.UserDataUpdate;
import io.bdrc.edit.patch.PatchContent;
import io.bdrc.edit.txn.exceptions.ModuleException;
import io.bdrc.libraries.EmailNotification;
import io.bdrc.libraries.GlobalHelpers;

public class UserTransaction extends EditTransaction {

    public final static Logger logger = LoggerFactory.getLogger(UserTransaction.class.getName());

    private TransactionLog log;
    Date commitTime;
    int status;
    String name;
    User user;
    UserDataUpdate data;
    int currentModule = -1;

    public UserTransaction(String patch, User user, String userId) throws Exception {
        try {
            this.user = user;
            this.name = "TXN_USER_" + userId;
            String path = EditConfig.getProperty("usersLogRootDir") + GlobalHelpers.getTwoLettersBucket(userId) + "/";
            Helpers.createDirIfNotExists(path);
            log = new TransactionLog(path, user.getName(), userId);
            setStatus(Types.STATUS_PREPARING);
            this.data = new UserDataUpdate(new PatchContent(patch), user.getName(), userId);
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
            } catch (Exception e) {
                setStatus(Status.STATUS_MARKED_ROLLBACK);
                log.addError(name, e.getMessage());
                try {
                    sendNotification("User Transaction " + name + " failed :" + System.lineSeparator() + TransactionLog.asString(log), "User Transaction failure");
                } catch (MessagingException e1) {
                    logger.error("Email notification failure", e1);
                }
                return modulesMap.get(currentModule).rollback();
            } finally {
                finalizeLog(log, name);
            }
        }
        setStatus(Types.STATUS_SUCCESS);
        try {
            try {
                sendNotification("User Transaction " + name + " succeeded :" + System.lineSeparator() + TransactionLog.asString(log), "User Transaction Success");
            } catch (MessagingException e1) {
                logger.error("Email notification failure", e1);
            }
            finalizeLog(log, name);
        } catch (Exception e) {
            e.printStackTrace();
            Log.error(this, "Edit User Transaction Manager failed to close log properly for User Transaction " + getName() + "finishing with status:" + Types.getStatus(getStatus()), e);
        }
        return true;
    }

    private void sendNotification(String message, String subject) throws AddressException, MessagingException {
        ArrayList<String> recip = new ArrayList<>();
        recip.add(user.getEmail());
        EmailNotification notif = new EmailNotification(message, user.getEmail(), user.getName(), subject, EditConfig.getProperties(), recip);
        notif.send();
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
