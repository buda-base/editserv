package io.bdrc.edit.txn.exceptions;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;

public class EditException extends Exception {

    int status;
	private static final long serialVersionUID = 7440728387322903030L;
	private static final Logger logger = LoggerFactory.getLogger(EditException.class);

	public EditException() {
		super();
		// TODO Auto-generated constructor stub
	}

	public EditException(String message, Throwable cause, boolean enableSuppression,
			boolean writableStackTrace) {
		super(message, cause, enableSuppression, writableStackTrace);
	}

	public EditException(String message, Throwable cause) {
		super(message, cause);
	}

    public EditException(int status, String message, Throwable cause) {
        super(message, cause);
        this.status = status;
        if (status == 500) {
            logger.error("error status {}, message: {}", status, message);
        }
    }
	
    public EditException(int status, String message) {
        super(message);
        this.status = status;
        if (status == 500) {
            logger.error("error status {}, message: {}", status, message);
        }
    }
    
	public EditException(String message) {
		super(message);
	}

	public EditException(Throwable cause) {
		super(cause);
	}
	
    public HttpStatus getHttpStatus() {
        return HttpStatus.resolve(status);
    }

}
