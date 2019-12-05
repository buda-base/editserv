package io.bdrc.edit.txn;

import java.io.IOException;

import com.fasterxml.jackson.core.JsonProcessingException;

import io.bdrc.edit.patch.PatchContent;

public class UserTransaction extends EditTransaction {

    public static String TX_PUB_TYPE = "PUBLIC";
    public static String TX_PRIV_TYPE = "PRIVATE";

    private PatchContent ptc;
    private String type;
    private TransactionLog log;

    public UserTransaction(String patch, String type, String editor, String userId) {
        log = new TransactionLog(editor, userId);
        this.type = type;
        this.ptc = new PatchContent(patch);
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

    @Override
    public void setStatus(int stat) throws IOException {
        // TODO Auto-generated method stub

    }

    @Override
    public int getStatus() {
        // TODO Auto-generated method stub
        return 0;
    }

}
