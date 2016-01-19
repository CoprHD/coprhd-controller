package com.emc.storageos.svcs.errorhandling.resources;

import java.net.URI;

public class MigrationCallbackException extends Exception {
	private String callbackName;
	private String cfName;
	private URI id;
	private String desc;
	private static final long serialVersionUID = 1L;
	
	public MigrationCallbackException(final String callbackName, final String cfName, final URI id,
			final String desc,final Throwable cause) {
		super(cause);
		this.callbackName = callbackName;
		this.cfName = cfName;
		this.id = id;
		this.desc = desc;
		
	}
	
	public String getMsg() {
		StringBuffer sb = new StringBuffer();
		sb.append(this.callbackName).append(" failed:").append(this.desc)
		  .append(" ").append(this.cfName).append("(").append(this.id).append(")");
		return sb.toString();
	}
}
