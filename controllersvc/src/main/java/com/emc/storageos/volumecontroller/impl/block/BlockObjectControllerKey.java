/**
* Copyright 2015 EMC Corporation
* All Rights Reserved
 */
/*
 * Copyright (c) 2013 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */

package com.emc.storageos.volumecontroller.impl.block;

import java.net.URI;

public class BlockObjectControllerKey {
	private URI storageControllerUri;
	private URI protectionControllerUri;
	
	public URI getStorageControllerUri() {
		return storageControllerUri;
	}
	public void setStorageControllerUri(URI storageControllerUri) {
		this.storageControllerUri = storageControllerUri;
	}
	public URI getProtectionControllerUri() {
		return protectionControllerUri;
	}
	public void setProtectionControllerUri(URI protectionControllerUri) {
		this.protectionControllerUri = protectionControllerUri;
	}
	
	public URI getController() {
		return (protectionControllerUri != null ? protectionControllerUri : storageControllerUri);
	}
	
	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime
				* result
				+ ((protectionControllerUri == null) ? 0
						: protectionControllerUri.hashCode());
		result = prime
				* result
				+ ((storageControllerUri == null) ? 0 : storageControllerUri
						.hashCode());
		return result;
	}
	
	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		BlockObjectControllerKey other = (BlockObjectControllerKey) obj;
		if (protectionControllerUri == null) {
			if (other.protectionControllerUri != null)
				return false;
		} else if (!protectionControllerUri
				.equals(other.protectionControllerUri))
			return false;
		if (storageControllerUri == null) {
			if (other.storageControllerUri != null)
				return false;
		} else if (!storageControllerUri.equals(other.storageControllerUri))
			return false;
		return true;
	}
}
