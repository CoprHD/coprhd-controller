/**
 *  Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */
package com.emc.storageos.cinder.model;

public class ConsistencyGroupDeleteRequest {
	
	/**
	 * JSON representation for consistency group delete request
	 * {"consistencygroup": {"force": true}}
	 */

	public Consistencygroup consistencygroup = new Consistencygroup();
	
	public class Consistencygroup {
		
		public boolean force;
	}
}
