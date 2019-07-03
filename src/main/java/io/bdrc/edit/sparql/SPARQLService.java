package io.bdrc.edit.sparql;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.bdrc.edit.patch.Task;
import io.bdrc.edit.service.BUDAEditService;
import io.bdrc.edit.txn.exceptions.ServiceException;

public class SPARQLService implements BUDAEditService {

    public final static Logger log = LoggerFactory.getLogger(SPARQLService.class.getName());

    Task tsk;

    public SPARQLService(Task tsk) {
        super();
        this.tsk = tsk;
    }

    public Task getTsk() {
        return tsk;
    }

    @Override
    public boolean rollback() throws ServiceException {
        // TODO Auto-generated method stub
        return false;
    }

    @Override
    public void run() throws ServiceException {
        // TODO Auto-generated method stub
        log.info("Running SPARQL Service for task {}", tsk);
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

    @Override
    public String getUserId() {
        // TODO Auto-generated method stub
        return null;
    }

}