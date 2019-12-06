package io.bdrc.edit.txn;

import java.io.IOException;

import com.fasterxml.jackson.core.JsonProcessingException;

import io.bdrc.edit.helpers.UserDataUpdate;
import io.bdrc.edit.patch.PatchContent;
import io.bdrc.edit.txn.exceptions.DataUpdateException;

public class UserTransaction extends EditTransaction {

    public static String TX_PUB_TYPE = "PUBLIC";
    public static String TX_PRIV_TYPE = "PRIVATE";

    private String type;
    private TransactionLog log;
    UserDataUpdate data;

    public UserTransaction(String patch, String type, String editor, String userId) throws DataUpdateException {
        log = new TransactionLog(editor, userId);
        this.type = type;
        this.data = new UserDataUpdate(new PatchContent(patch), editor, userId);
    }

    @Override
    public boolean commit() throws IOException {
        return false;
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

}
