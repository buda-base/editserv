package io.bdrc.edit.txn.exceptions;

public class ServiceSequenceException extends Exception {

	/**
	 * 
	 */
	private static final long serialVersionUID = 3223576635064066067L;

	public ServiceSequenceException() {
		super();
	}

	public ServiceSequenceException(String message, Throwable cause, boolean enableSuppression,
			boolean writableStackTrace) {
		super(message, cause, enableSuppression, writableStackTrace);
	}

	public ServiceSequenceException(String message, Throwable cause) {
		super(message, cause);
	}

	public ServiceSequenceException(String message) {
		super(message);
	}

	public ServiceSequenceException(Throwable cause) {
		super(cause);
	}

}
