/**
* Copyright 2015 EMC Corporation
* All Rights Reserved
 */
/* 
Copyright (c) 2012 EMC Corporation
All Rights Reserved

This software contains the intellectual property of EMC Corporation
or is licensed to EMC Corporation from third parties.  Use of this
software and the intellectual property contained therein is expressly
imited to the terms and conditions of the License Agreement under which
it is provided by or on behalf of EMC.
*/
package com.emc.storageos.vasa.fault;

public class SOSFailure extends Exception {

	private static final long serialVersionUID = 1L;

	public SOSFailure() {

	}

	public SOSFailure(String message) {
		super(message);
	}

	public SOSFailure(String message, Throwable cause) {
		super(message, cause);
	}

	public SOSFailure(Throwable cause) {
		super(cause);
	}
}
