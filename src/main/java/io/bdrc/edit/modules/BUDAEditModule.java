package io.bdrc.edit.modules;

import io.bdrc.edit.txn.exceptions.ModuleException;

public interface BUDAEditModule {

    /**
     * Rollback to previous state, before service execution/failure
     */
    public boolean rollback() throws ModuleException;

    /**
     * Run the service and logs execution.
     */
    public void run() throws ModuleException;

    /**
     * Obtain the status of the service object.
     */
    public int getStatus();

    /**
     * Set the status of the service object.
     */
    public void setStatus(int st) throws ModuleException;

    /**
     * Obtain the id of the service object.
     */
    public String getId();

    /**
     * Obtain the name of the service object.
     */
    public String getName();

    /**
     * Obtain the id of the user of this service.
     */
    public String getUserId();

}
