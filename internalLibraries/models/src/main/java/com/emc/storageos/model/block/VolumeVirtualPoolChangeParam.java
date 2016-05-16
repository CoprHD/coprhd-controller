/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.model.block;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElementWrapper;
import javax.xml.bind.annotation.XmlRootElement;

import org.codehaus.jackson.annotate.JsonProperty;

import com.emc.storageos.model.vpool.BlockVirtualPoolProtectionParam;

/**
 * Specifies the parameters to change the virtual pool for a list of volumes.
 */
@XmlRootElement(name = "volumes_vpool_change")
public class VolumeVirtualPoolChangeParam {

    private List<URI> volumes;
    private URI virtualPool;
    private BlockVirtualPoolProtectionParam protection;
    private URI consistencyGroup;
    private String transferSpeed; 
    private URI migrationHost;
    private boolean isHostMigration;

    public VolumeVirtualPoolChangeParam() {
    }

    public VolumeVirtualPoolChangeParam(List<URI> volumes, URI virtualPool,
            BlockVirtualPoolProtectionParam protection,
            URI migrationHost, boolean isHostMigration) {
        this.volumes = volumes;
        this.virtualPool = virtualPool;
        this.protection = protection;
        this.migrationHost = migrationHost;
        this.isHostMigration = isHostMigration;
    }

    @XmlElementWrapper(required = true, name = "volumes")
    /**
     * List of Volume IDs.
     */
    @XmlElement(required = true, name = "volume")
    public List<URI> getVolumes() {
        if (volumes == null) {
            volumes = new ArrayList<URI>();
        }
        return volumes;
    }

    public void setVolumes(List<URI> volumes) {
        this.volumes = volumes;
    }

    /**
     * ID of the new virtual pool.
     * 
     */
    @XmlElement(required = true, name = "vpool")
    @JsonProperty("vpool")
    public URI getVirtualPool() {
        return virtualPool;
    }

    public void setVirtualPool(URI virtualPool) {
        this.virtualPool = virtualPool;
    }

    /**
     * Virtual Pool (Mirror or RecoverPoint) protection
     * parameters.
     * 
     */
    @XmlElement(required = false, name = "protection")
    public BlockVirtualPoolProtectionParam getProtection() {
        return protection;
    }

    public void setProtection(BlockVirtualPoolProtectionParam protection) {
        this.protection = protection;
    }
    
    /**
     * Parameter for Transfer Speed. Optional parameter for virtual volume migration
     * from VPLEX Local to Distributed.
     */
    @XmlElement(required = false, name = "transfer_speed")
    public String getTransferSpeedParam() {
    	return transferSpeed; 
    }
    
    public void setTransferSpeedParam(String transferspeed) {
    	this.transferSpeed = transferspeed; 
    }
    
    

    /**
     * The ViPR consistency group to associate the volume with for
     * the creation of the RecoverPoint consistency group.
     * 
     * @return The Consistency Group to associate the volume during creation of RP consistency group
     */
    @XmlElement(name = "consistency_group")
    public URI getConsistencyGroup() {
        return consistencyGroup;
    }

    public void setConsistencyGroup(URI consistencyGroup) {
        this.consistencyGroup = consistencyGroup;
    }

    @XmlElement(required = false, name = "host")
    public URI getMigrationHost() {
        return migrationHost;
    }

    public void setMigrationHost(URI migrationHost) {
        this.migrationHost = migrationHost;
    }

    @XmlElement(required = false, name = "ishostmigration")
    public boolean getIsHostMigration() {
        return isHostMigration;
    }

    public void setIsHostMigration(boolean isHostMigration) {
        this.isHostMigration = isHostMigration;
    }

}
