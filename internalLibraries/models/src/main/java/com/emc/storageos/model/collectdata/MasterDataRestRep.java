/**
 * Copyright (c) 2017 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.model.collectdata;

import java.util.Arrays;

/**
 * Master attributes
 * 
 */

public class MasterDataRestRep {
	private String port;
    private String id;
    private String versionInfo;
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

    public String[] getIps ()
    {
        if(null == ips){
            return null;
        }
        return Arrays.copyOf(ips,ips.length);
    }

    public void setIps (String[] ips)
    {
        if(null == ips){
            return;
        }
        this.ips = Arrays.copyOf(ips,ips.length);
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
        if(null == managementIPs){
            return null;
        }
        return Arrays.copyOf(managementIPs,managementIPs.length);
    }

    public void setManagementIPs (String[] managementIPs)
    {
        if(null == managementIPs){
            return;
        }
        this.managementIPs = Arrays.copyOf(managementIPs,managementIPs.length);
    }

}
