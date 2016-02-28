/*
 * Copyright (c) 2008-2013 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.model.block;

import java.net.URI;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

import com.emc.storageos.model.valid.Length;

/**
 * Volume migration parameters
 */
@XmlRootElement(name = "volume_migrate")
public class VolumeMigrate {
	
	
	private URI hostURI;
    private URI sourceVolumeURI;
    private URI targetVolumeURI;
    
    public VolumeMigrate(URI hostURI, URI sourceVolumeURI, URI targetVolumeURI) {
		
		this.hostURI = hostURI;
		this.sourceVolumeURI = sourceVolumeURI;
		this.targetVolumeURI = targetVolumeURI;
	}

    
    @XmlElement(required = true)
	public URI getHostURI() {
		return hostURI;
	}

	public void setHostURI(URI hostURI) {
		this.hostURI = hostURI;
	}

	
	@XmlElement(required = true)
	public URI getSourceVolumeURI() {
		return sourceVolumeURI;
	}

	public void setSourceVolumeURI(URI sourceVolumeURI) {
		this.sourceVolumeURI = sourceVolumeURI;
	}

	
	@XmlElement(required = true)
	public URI getTargetVolumeURI() {
		return targetVolumeURI;
	}

	public void setTargetVolumeURI(URI targetVolumeURI) {
		this.targetVolumeURI = targetVolumeURI;
	}
	
    
}