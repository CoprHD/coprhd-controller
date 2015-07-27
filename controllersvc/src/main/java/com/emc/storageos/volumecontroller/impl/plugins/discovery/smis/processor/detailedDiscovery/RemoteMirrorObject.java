/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.plugins.discovery.smis.processor.detailedDiscovery;

import java.net.URI;

import com.emc.storageos.db.client.model.StringSet;

public class RemoteMirrorObject {

	private String copyMode;
	
	private URI raGroupUri;
	
	//list of target volume uris
	private StringSet targetVolumenativeGuids;
	
	//source Volume Native Guid
	private String sourceVolumeNativeGuid;
	
	private String type;
	
	public enum Types{
		SOURCE,
		TARGET
	}

	public String getCopyMode() {
		return copyMode;
	}

	public void setCopyMode(String copyMode) {
		this.copyMode = copyMode;
	}

	public URI getRaGroupUri() {
		return raGroupUri;
	}

	public void setRaGroupUri(URI raGroupUri) {
		this.raGroupUri = raGroupUri;
	}

	public StringSet getTargetVolumenativeGuids() {
		return targetVolumenativeGuids;
	}

	public void setTargetVolumenativeGuids(StringSet targetVolumenativeGuids) {
		this.targetVolumenativeGuids = targetVolumenativeGuids;
	}

	public String getSourceVolumeNativeGuid() {
		return sourceVolumeNativeGuid;
	}

	public void setSourceVolumeNativeGuid(String sourceVolumeNativeGuid) {
		this.sourceVolumeNativeGuid = sourceVolumeNativeGuid;
	}

	public String getType() {
		return type;
	}

	public void setType(String type) {
		this.type = type;
	}
	
	public String toString() {
		StringBuffer buffer = new StringBuffer();
		buffer.append("Remote Group :");
		buffer.append(raGroupUri);
		buffer.append(";Type :");
		buffer.append(type);
		buffer.append(";Mode :");
		buffer.append(copyMode);
		return buffer.toString();
	}
	
}
