package io.bdrc.edit.txn.exceptions;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

public class EditException extends ResponseStatusException {

    int status = 500;
	private static final long serialVersionUID = 7440728387322903030L;
	private static final Logger logger = LoggerFactory.getLogger(EditException.class);

	public EditException(String message, Throwable cause, boolean enableSuppression,
			boolean writableStackTrace) {
		super(500, message, cause);
	}

	public EditException(String message, Throwable cause) {
		super(500, message, cause);
	}

    public EditException(int status, String message, Throwable cause) {
        super(status, message, cause);
        this.status = status;
        if (status == 500) {
            logger.error("error status {}, message: {}", status, message);
        }
    }
	
    public EditException(int status, String message) {
        super(status, message, null);
        this.status = status;
        if (status == 500) {
            logger.error("error status {}, message: {}", status, message);
        }
    }
    
	public EditException(String message) {
		super(500, message, null);
	}

	public EditException(Throwable cause) {
		super(500, "", cause);
	}
	
    public HttpStatus getHttpStatus() {
        return HttpStatus.resolve(status);
    }

}
