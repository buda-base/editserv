package io.bdrc.edit.txn;

import java.util.HashMap;
import java.util.concurrent.LinkedBlockingQueue;

public class BUDATransactionManager {

    public static LinkedBlockingQueue<QueuedPatch> WAITING_QUEUE;
    public static HashMap<String, QueuedPatch> PROCESSED;

    static {
        WAITING_QUEUE = new LinkedBlockingQueue<QueuedPatch>();
        PROCESSED = new HashMap<>();
    }

}
