package io.bdrc.edit.txn;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.concurrent.LinkedBlockingQueue;

import io.bdrc.edit.Types;

public class BUDATransactionManager {

    public static ArrayList<String> REQUESTS;
    public static LinkedBlockingQueue<BUDATransaction> WAITING_QUEUE;
    public static HashMap<String, BUDATransaction> PROCESSED;

    static {
        WAITING_QUEUE = new LinkedBlockingQueue<BUDATransaction>();
        PROCESSED = new HashMap<>();
        REQUESTS = new ArrayList<>();
    }

    public static void queueTxn(BUDATransaction btx) {
        WAITING_QUEUE.add(btx);
        REQUESTS.add(btx.getId());
    }

    public static String getTxnStatus(String id) {
        BUDATransaction btx = PROCESSED.get(id);
        if (btx != null) {
            return Types.getTxnStatus(btx.getStatus());
        }
        if (REQUESTS.contains(id)) {
            return Types.getTxnStatus(Types.STATUS_QUEUED);
        }
        System.out.println("ID=" + id + " and requests=" + REQUESTS);
        return "UNKNOWN";
    }

}
