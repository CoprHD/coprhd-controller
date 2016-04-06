/**
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.scaleio.api.restapi.response;

import java.util.List;

import org.codehaus.jackson.annotate.JsonIgnoreProperties;

import com.emc.storageos.scaleio.api.ParsePattern;
import com.emc.storageos.scaleio.api.ScaleIOConstants;

/**
 * Slaves attributes
 * 
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class Slaves {
	private String port;
	private String id;
	private String versionInfo;
	private String status;
	private String[] ips;
	private String name;	
	private String role;	
	private String[] managementIPs;
	
	public String getPort ()
	{
	    return port;
	}
	
	public void setPort (String port)
	{
	    this.port = port;
	}
	
	public String getId ()
	{
	    return id;
	}
	
	public void setId (String id)
	{
	    this.id = id;
	}
	
	public String getVersionInfo ()
	{
	    return versionInfo;
	}
	
	public void setVersionInfo (String versionInfo)
	{
	    this.versionInfo = versionInfo;
	}
	
	public String getStatus ()
	{
	    return status;
	}
	
	public void setStatus (String status)
	{
	    this.status = status;
	}
	
	public String[] getIps ()
	{
	    return ips;
	}
	
	public void setIps (String[] ips)
	{
	    this.ips = ips;
	}
	
	public String getName ()
	{
	    return name;
	}
	
	public void setName (String name)
	{
	    this.name = name;
	}
	
	public String getRole ()
	{
	    return role;
	}
	
	public void setRole (String role)
	{
	    this.role = role;
	}
	
	public String[] getManagementIPs ()
	{
	    return managementIPs;
	}
	
	public void setManagementIPs (String[] managementIPs)
	{
	    this.managementIPs = managementIPs;
	}
	
	@Override
	public String toString()
	{
	    return "ClassPojo [port = "+port+", id = "+id+", versionInfo = "+versionInfo+", status = "+status+", ips = "+ips+", name = "+name+", role = "+role+", managementIPs = "+managementIPs+"]";
	}
}
