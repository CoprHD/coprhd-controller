/*
 * Copyright (c) 2012 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.vasa.fault;

public class SOSAuthenticationFailure extends Exception {

	private static final long serialVersionUID = 1L;

	public SOSAuthenticationFailure() {
		super();
	}

	public SOSAuthenticationFailure(String arg0, Throwable arg1) {
		super(arg0, arg1);
	}

	public SOSAuthenticationFailure(String arg0) {
		super(arg0);
	}

	public SOSAuthenticationFailure(Throwable arg0) {
		super(arg0);
	}

}
