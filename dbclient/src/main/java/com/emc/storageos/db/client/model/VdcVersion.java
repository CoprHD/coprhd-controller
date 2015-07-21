/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.db.client.model;

import java.net.URI;

@SuppressWarnings("serial")
@Cf("VdcVersion")
@DbKeyspace(DbKeyspace.Keyspaces.GLOBAL)
public class VdcVersion extends DataObject{
	private URI vdcId;
	private String version;

	@Name("vdcId")
	public URI getVdcId() {
		return vdcId;
	}

	public void setVdcId(URI vdcId) {
		this.vdcId = vdcId;
		this.setChanged("vdcId");
	}

	@Name("version")
	public String getVersion() {
		return version;
	}

	public void setVersion(String version) {
		this.version = version;
		this.setChanged("version");
	}
}
