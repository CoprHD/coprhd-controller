package com.emc.storageos.svcs.errorhandling.resources;


public class MigrationCallbackException extends Exception {	
	public MigrationCallbackException(final String message,final Throwable cause) {
		super(message, cause);
		
	}
}
