package io.bdrc.edit.txn;

import java.util.HashMap;
import java.util.concurrent.LinkedBlockingQueue;

public class BUDATransactionManager {

    public static LinkedBlockingQueue<BUDATransaction> WAITING_QUEUE;
    public static HashMap<String, BUDATransaction> PROCESSED;

    static {
        WAITING_QUEUE = new LinkedBlockingQueue<BUDATransaction>();
        PROCESSED = new HashMap<>();
    }

    public static void queueTxn(BUDATransaction btx) {
        WAITING_QUEUE.add(btx);
    }

}
