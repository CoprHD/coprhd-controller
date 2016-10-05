/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.model.docker;

import java.util.HashMap;
import java.util.Map;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;


@XmlRootElement(name = "options")
public class DockerVolumeCreateParams {

    private Map<String, String> opts;
    
    private String name;
    
    @XmlElement(name = "Name")
    public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public DockerVolumeCreateParams() {
    }

    public DockerVolumeCreateParams(Map<String, String> options) {
        this.opts = options;
    }

    /**
     * A map of options.
     * 
     */
    @XmlElement(name = "Opts")
    public Map<String, String> getOpts() {
        if (opts == null) {
            opts = new HashMap<String, String>();
        }
        return opts;
    }

    public void setOpts(Map<String, String> options) {
        this.opts = options;
    }

}
