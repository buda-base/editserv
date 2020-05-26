package io.bdrc.edit.txn.exceptions;

public class VersionConflictException extends Exception {

    /**
     * 
     */
    private static final long serialVersionUID = 7524990307097346142L;

    public VersionConflictException() {
        // TODO Auto-generated constructor stub
    }

    public VersionConflictException(String message) {
        super(message);
        // TODO Auto-generated constructor stub
    }

    public VersionConflictException(Throwable cause) {
        super(cause);
        // TODO Auto-generated constructor stub
    }

    public VersionConflictException(String message, Throwable cause) {
        super(message, cause);
        // TODO Auto-generated constructor stub
    }

    public VersionConflictException(String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
        super(message, cause, enableSuppression, writableStackTrace);
        // TODO Auto-generated constructor stub
    }

}
