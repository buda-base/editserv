package io.bdrc.edit.service;

import javax.servlet.http.HttpServletRequest;

import io.bdrc.edit.txn.exceptions.ServiceException;

public class PatchService implements BUDAEditService {

    String slug;
    String pragma;
    String payload;
    String id;

    public PatchService(HttpServletRequest req) {
        this.slug = req.getHeader("Slug");
        this.pragma = req.getHeader("Pragma");
        this.payload = req.getParameter("payload");
        this.id = slug + "_" + Long.toString(System.currentTimeMillis());
    }

    public String getSlug() {
        return slug;
    }

    public String getPragma() {
        return pragma;
    }

    public String getPayload() {
        return payload;
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
