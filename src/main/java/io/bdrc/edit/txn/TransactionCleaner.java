package io.bdrc.edit.txn;

public class TransactionCleaner extends java.util.TimerTask {

    public TransactionCleaner() {

    }

    @Override
    public void run() {
        // TODO Auto-generated method stub
        BUDATransactionManager.cleanProcesses();
    }

}
