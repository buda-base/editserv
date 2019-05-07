package io.bdrc.edit.service;

import io.bdrc.edit.txn.exceptions.ServiceException;

public interface BUDAEditService {

    /** The create edit type */
    public final String EDIT_CREATE = "Edit_Create";

    /** The update edit type */
    public final String EDIT_UPDATE = "Edit_Update";

    /** The delete edit type */
    public final String EDIT_DELETE = "Edit_Delete";

    /**
     * Rollback to previous state, before service execution/failure
     */
    public boolean rollback() throws ServiceException;

    /**
     * Run the service and logs execution.
     */
    public void run() throws ServiceException;

    /**
     * Obtain the status of the service object.
     */
    public int getStatus();

    /**
     * Set the status of the service object.
     */
    public void setStatus(int st);

    /**
     * Obtain the status of the service object.
     */
    public int getType();

    /**
     * Set the status of the service object.
     */
    public void setType(int tp);

    /**
     * Obtain the id of the service object.
     */
    public String getId();

    /**
     * Obtain the name of the service object.
     */
    public String getName();

}
