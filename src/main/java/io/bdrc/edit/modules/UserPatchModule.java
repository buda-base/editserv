package io.bdrc.edit.modules;

import io.bdrc.edit.helpers.UserDataUpdate;
import io.bdrc.edit.txn.exceptions.ModuleException;

public class UserPatchModule implements BUDAEditModule {

    private String type;
    UserDataUpdate data;

    public UserPatchModule(UserDataUpdate data, String type) {
        this.data = data;
        this.type = type;
    }

    @Override
    public boolean rollback() throws ModuleException {
        // TODO Auto-generated method stub
        return false;
    }

    @Override
    public void run() throws ModuleException {
        // TODO Auto-generated method stub

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

    @Override
    public int getStatus() {
        // TODO Auto-generated method stub
        return 0;
    }

    @Override
    public void setStatus(int st) throws ModuleException {
        // TODO Auto-generated method stub

    }

}
