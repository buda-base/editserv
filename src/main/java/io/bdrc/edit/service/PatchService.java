package io.bdrc.edit.service;

import java.util.HashMap;
import java.util.concurrent.LinkedBlockingQueue;

import javax.servlet.http.HttpServletRequest;

import io.bdrc.edit.txn.QueuedPatch;
import io.bdrc.edit.txn.exceptions.ServiceException;

public class PatchService implements BUDAEditService {

    public static LinkedBlockingQueue<QueuedPatch> waitingQueue = new LinkedBlockingQueue<QueuedPatch>();
    public static HashMap<String, QueuedPatch> processed = new HashMap<>();

    public PatchService(HttpServletRequest req) {
        String slug = req.getHeader("Slug");
        String pragma = req.getHeader("Pragma");
    }

    @Override
    public boolean rollback() throws ServiceException {
        // TODO Auto-generated method stub
        return false;
    }

    @Override
    public void run() throws ServiceException {
        // TODO Auto-generated method stub

    }

    @Override
    public int getStatus() {
        // TODO Auto-generated method stub
        return 0;
    }

    @Override
    public void setStatus(int st) {
        // TODO Auto-generated method stub

    }

    @Override
    public int getType() {
        // TODO Auto-generated method stub
        return 0;
    }

    @Override
    public void setType(int tp) {
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
}
