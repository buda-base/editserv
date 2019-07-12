package io.bdrc.edit.txn;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.concurrent.LinkedBlockingQueue;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.JsonProcessingException;

import io.bdrc.edit.Types;

public class BUDATransactionManager implements Runnable {

    private static BUDATransactionManager INSTANCE;
    public static ArrayList<String> REQUESTS;
    public static LinkedBlockingQueue<BUDATransaction> WAITING_QUEUE;
    public static HashMap<String, BUDATransaction> PROCESSES;

    public final static Logger logger = LoggerFactory.getLogger(BUDATransactionManager.class.getName());

    private BUDATransactionManager() {
        WAITING_QUEUE = new LinkedBlockingQueue<BUDATransaction>();
        PROCESSES = new HashMap<>();
        REQUESTS = new ArrayList<>();
    }

    public static BUDATransactionManager getInstance() {
        if (INSTANCE == null) {
            INSTANCE = new BUDATransactionManager();
        }
        logger.info(">> BUDATransactionManager returning instance...");
        return INSTANCE;
    }

    public void queueTxn(BUDATransaction btx) {
        logger.info(">> BUDATransactionManager queing " + btx);
        WAITING_QUEUE.add(btx);
        REQUESTS.add(btx.getId());
    }

    public static String getTxnStatus(String id) {
        BUDATransaction btx = PROCESSES.get(id);
        if (btx != null) {
            return Types.getStatus(btx.getStatus());
        }
        if (REQUESTS.contains(id)) {
            return Types.getStatus(Types.STATUS_QUEUED);
        }
        return "UNKNOWN";
    }

    @SuppressWarnings("unused")
    @Override
    public void run() {
        while (true) {
            BUDATransaction btx = null;
            try {
                // Wait for the next available transaction in the queue
                btx = WAITING_QUEUE.take();
                logger.info(">> BUDATransactionManager got " + btx + " from the queue");
                if (btx != null) {
                    // Not a request anymore
                    REQUESTS.remove(btx.getId());
                    btx.setStatus(Types.STATUS_PROCESSING);
                    // The request has become a process
                    PROCESSES.put(btx.getId(), btx);
                    btx.commit();
                    logger.info(">> BUDATransactionManager submitted " + btx);
                }

            } catch (Exception e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            } finally {
                try {
                    btx.finalizeLog();
                } catch (JsonProcessingException e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                } catch (IOException e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                }
            }
        }

    }

}
