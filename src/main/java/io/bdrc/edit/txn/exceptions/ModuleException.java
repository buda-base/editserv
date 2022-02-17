package io.bdrc.edit.txn.exceptions;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;

public class ModuleException extends Exception {

    int status;
	private static final long serialVersionUID = 7440728387322903030L;
	private static final Logger logger = LoggerFactory.getLogger(ModuleException.class);

	public ModuleException() {
		super();
		// TODO Auto-generated constructor stub
	}

	public ModuleException(String message, Throwable cause, boolean enableSuppression,
			boolean writableStackTrace) {
		super(message, cause, enableSuppression, writableStackTrace);
	}

	public ModuleException(String message, Throwable cause) {
		super(message, cause);
	}

    public ModuleException(int status, String message, Throwable cause) {
        super(message, cause);
        this.status = status;
        if (status == 500) {
            logger.error("error status {}, message: {}", status, message);
        }
    }
	
    public ModuleException(int status, String message) {
        super(message);
        this.status = status;
        if (status == 500) {
            logger.error("error status {}, message: {}", status, message);
        }
    }
    
	public ModuleException(String message) {
		super(message);
	}

	public ModuleException(Throwable cause) {
		super(cause);
	}
	
    public HttpStatus getHttpStatus() {
        return HttpStatus.resolve(status);
    }

}
