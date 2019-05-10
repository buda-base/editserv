package io.bdrc.edit;

import java.util.HashMap;

import javax.transaction.Status;

public class Types {

    public static HashMap<Integer, String> TXN_STATUS = new HashMap<>();

    public static final int STATUS_QUEUED = 17;
    public static final int STATUS_SUCCESS = 18;
    public static final int STATUS_FAILED = 19;
    public static final int STATUS_UNKNOWN = 20;
    public static final int STATUS_PROCESSING = 21;

    static {

        TXN_STATUS.put(Status.STATUS_ACTIVE, "STATUS_ACTIVE");
        TXN_STATUS.put(Status.STATUS_COMMITTED, "STATUS_COMMITTED");
        TXN_STATUS.put(Status.STATUS_COMMITTING, "STATUS_COMMITTING");
        TXN_STATUS.put(Status.STATUS_MARKED_ROLLBACK, "STATUS_MARKED_ROLLBACK");
        TXN_STATUS.put(Status.STATUS_NO_TRANSACTION, "STATUS_NO_TRANSACTION");
        TXN_STATUS.put(Status.STATUS_PREPARED, "STATUS_PREPARED");
        TXN_STATUS.put(Status.STATUS_PREPARING, "STATUS_PREPARING");
        TXN_STATUS.put(Status.STATUS_ROLLEDBACK, "STATUS_ROLLEDBACK");
        TXN_STATUS.put(Status.STATUS_ROLLING_BACK, "STATUS_ROLLING_BACK");
        TXN_STATUS.put(Status.STATUS_UNKNOWN, "STATUS_UNKNOWN");
        TXN_STATUS.put(STATUS_QUEUED, "STATUS_QUEUED");
        TXN_STATUS.put(STATUS_SUCCESS, "STATUS_SUCCESS");
        TXN_STATUS.put(STATUS_FAILED, "STATUS_FAILED");
        TXN_STATUS.put(STATUS_UNKNOWN, "STATUS_UNKNOWN");
        TXN_STATUS.put(STATUS_PROCESSING, "STATUS_PROCESSING");
    }

    public static String getTxnStatus(int st) {
        return TXN_STATUS.get(st);
    }

}
