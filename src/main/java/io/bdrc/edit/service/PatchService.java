package io.bdrc.edit.service;

import javax.servlet.http.HttpServletRequest;

import io.bdrc.edit.EditConstants;
import io.bdrc.edit.txn.BUDATransactionManager;
import io.bdrc.edit.txn.QueuedPatch;
import io.bdrc.edit.txn.exceptions.ServiceException;

public class PatchService implements BUDAEditService {

    public PatchService(HttpServletRequest req) {

        String slug = req.getHeader("Slug");
        String pragma = req.getHeader("Pragma");
        String payload = req.getParameter("payload");
        QueuedPatch qp = new QueuedPatch(slug, pragma, payload);
        qp.setStatus(EditConstants.PATCH_SVC_QUEUED);
        BUDATransactionManager.WAITING_QUEUE.add(qp);
    }

    public void removePatch(QueuedPatch qp) {
        qp.setStatus(EditConstants.PATCH_SVC_PROCESSING);
        BUDATransactionManager.PROCESSED.put(qp.getId(), qp);
    }

    public QueuedPatch getPatch(String id) {
        return BUDATransactionManager.PROCESSED.get(id);
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
