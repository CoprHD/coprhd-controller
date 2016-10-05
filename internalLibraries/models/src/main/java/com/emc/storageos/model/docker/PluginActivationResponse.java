/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.model.docker;

import java.util.ArrayList;
import java.util.List;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

@XmlRootElement(name = "plugin")
public class PluginActivationResponse {
    
	private List<String> plugins;
	
	@XmlElement(name = "Implements")
    public List<String> getPlugins() {
		return plugins;
	}

	public void setPlugins(List<String> plugins) {
		this.plugins = plugins;
	}


    public PluginActivationResponse() {
    }

    public PluginActivationResponse(String implementsPlugin) {
    	plugins = new ArrayList<String>();
    	plugins.add(implementsPlugin);
    }


}
