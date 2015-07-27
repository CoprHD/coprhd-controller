/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.model.file;

import javax.xml.bind.annotation.XmlRootElement;

@XmlRootElement(name = "snapshot_export_update")
public class SnapshotExportUpdateParams extends FileExportUpdateParams {
	
	/**
	 * 
	 */
	private static final long serialVersionUID = -4726646565521294076L;

	/**
	 * Default Constructor
	 */
	public SnapshotExportUpdateParams() {
	}


}