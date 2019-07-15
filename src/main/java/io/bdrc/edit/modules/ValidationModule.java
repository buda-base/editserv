package io.bdrc.edit.modules;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.bdrc.edit.Types;
import io.bdrc.edit.helpers.DataUpdate;
import io.bdrc.edit.sparql.QueryProcessor;
import io.bdrc.edit.txn.TransactionLog;
import io.bdrc.edit.txn.exceptions.ModuleException;
import io.bdrc.edit.txn.exceptions.PatchModuleException;
import io.bdrc.edit.txn.exceptions.ValidationModuleException;

public class ValidationModule implements BUDAEditModule {

    public static final String PRE_VALIDATION = "pre_validation";
    public static final String GIT_VERSION_VALIDATION = "git_version_validation";

    DataUpdate data;
    String userId;
    String name;
    int status;
    TransactionLog log;
    String validation;

    public final static Logger logger = LoggerFactory.getLogger(PatchModule.class.getName());

    public ValidationModule(DataUpdate data, TransactionLog log, String validation) throws ModuleException {
        this.validation = validation;
        this.userId = data.getUserId();
        this.name = "PATCH_MOD_" + data.getTaskId();
        this.data = data;
        this.log = log;
        setStatus(Types.STATUS_PREPARED);
    }

    @Override
    public boolean rollback() throws ModuleException {
        // TODO Auto-generated method stub
        return false;
    }

    @Override
    public void run() throws ValidationModuleException {
        // Make sure the resources we want to create don't already exist
        switch (validation) {
        case PRE_VALIDATION:
            List<String> create = data.getCreate();
            for (String uri : create) {
                if (QueryProcessor.resourceExist(uri)) {
                    throw new ValidationModuleException("Cannot create the resource " + uri + " as it already exists in the main dataset");
                }
            }
            List<String> graphs = data.getGraphs();
            for (String uri : graphs) {
                if (!QueryProcessor.resourceExist(uri)) {
                    throw new ValidationModuleException("Cannot update the resource " + uri + " as it doesn't exist in the main dataset");
                }
            }
            break;
        }
    }

    @Override
    public int getStatus() {
        // TODO Auto-generated method stub
        return 0;
    }

    @Override
    public void setStatus(int st) throws PatchModuleException {
        try {
            this.status = st;
            log.addContent(name, " entered " + Types.getStatus(status));
            log.setLastStatus(Types.getStatus(status));
        } catch (Exception e) {
            e.printStackTrace();
            setStatus(Types.STATUS_FAILED);
            log.setLastStatus(name + ": " + Types.getStatus(status));
            log.addError(name, e.getMessage());
            throw new PatchModuleException(e);
        }
    }

    @Override
    public String getId() {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public String getName() {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public String getUserId() {
        // TODO Auto-generated method stub
        return null;
    }

    public DataUpdate getData() {
        return data;
    }

    public TransactionLog getLog() {
        return log;
    }

    public String getValidation() {
        return validation;
    }

}
