package io.bdrc.edit.txn;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.concurrent.LinkedBlockingQueue;

import io.bdrc.edit.Types;

public class BUDATransactionManager implements Runnable {

    private static BUDATransactionManager INSTANCE;
    public static ArrayList<String> REQUESTS;
    public static LinkedBlockingQueue<BUDATransaction> WAITING_QUEUE;
    public static HashMap<String, BUDATransaction> PROCESSES;

    private BUDATransactionManager() {
        WAITING_QUEUE = new LinkedBlockingQueue<BUDATransaction>();
        PROCESSES = new HashMap<>();
        REQUESTS = new ArrayList<>();
    }

    public static BUDATransactionManager getInstance() {
        if (INSTANCE == null) {
            INSTANCE = new BUDATransactionManager();
        }
        return INSTANCE;
    }

    public void queueTxn(BUDATransaction btx) {
        WAITING_QUEUE.add(btx);
        REQUESTS.add(btx.getId());
    }

    public static String getTxnStatus(String id) {
        BUDATransaction btx = PROCESSES.get(id);
        if (btx != null) {
            return Types.getTxnStatus(btx.getStatus());
        }
        if (REQUESTS.contains(id)) {
            return Types.getTxnStatus(Types.STATUS_QUEUED);
        }
        return "UNKNOWN";
    }

    @Override
    public void run() {
        while (true) {
            BUDATransaction btx = null;
            try {
                // Wait for the next available transaction in the queue
                // btx = WAITING_QUEUE.take();
                if (btx != null) {
                    // Not a request anymore
                    REQUESTS.remove(btx.getId());
                    btx.setStatus(Types.STATUS_PROCESSING);
                    // The request has become a process
                    PROCESSES.put(btx.getId(), btx);
                    // btx.commit();
                }

            } catch (Exception e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
        }

    }

}
