/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.model.block;

import java.net.URI;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElementWrapper;
import javax.xml.bind.annotation.XmlRootElement;

import org.codehaus.jackson.annotate.JsonProperty;

import com.emc.storageos.model.valid.Length;
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
    private Set<String> extensionParams;
    // Optional fields for migration
    private boolean migrationSuspendBeforeCommit = false;
    private boolean migrationSuspendBeforeDeleteSource = false;
    boolean forceFlag = false;

    // A list of implemented extension parameter values.  See the getter method for more info.
    public static String EXTENSION_PARAM_KNOWN_RDFGROUP = "rdfGroup";

    public VolumeVirtualPoolChangeParam() {
    }

    public VolumeVirtualPoolChangeParam(List<URI> volumes, URI virtualPool,
            BlockVirtualPoolProtectionParam protection) {
        this.volumes = volumes;
        this.virtualPool = virtualPool;
        this.protection = protection;
        this.extensionParams = new HashSet<>();
    }

    /*
     * Extension parameters gives additional flexibility to volume
     * creation requests without changing the hard schema of the request
     * object by providing a name/value set that can be sent down to any
     * device implementation as needed.
     * 
     * Currently Supported:
     * 
     * rdfGroup=<RemoteDirectorGroup URI> // Select a specific RDF Group to place the volume into.
     */
    @XmlElement(name = "extension_parameters")
    @Length(min = 2, max = 128)
    public Set<String> getExtensionParams() {
        if (extensionParams == null) {
            extensionParams = new LinkedHashSet<String>();
        }
        return extensionParams;
    }

    public void setExtensionParams(Set<String> extensionParams) {
        this.extensionParams = extensionParams;
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

    /**
     * Does the user want us to suspend the workflow before migration commit().
     * 
     * @return if migration suspend before commit
     */
    @XmlElement(required = false, name = "migration_suspend_before_commit")
    public boolean isMigrationSuspendBeforeCommit() {
        return migrationSuspendBeforeCommit;
    }

    public void setMigrationSuspendBeforeCommit(boolean migrationSuspendBeforeCommit) {
        this.migrationSuspendBeforeCommit = migrationSuspendBeforeCommit;
    }

    /**
     * Does the user want us to suspend before deleting the original source volume during the workflow.
     * 
     * @return if migration suspend before deleting the original source volume
     */
    @XmlElement(required = false, name = "migration_suspend_before_delete_source")
    public boolean isMigrationSuspendBeforeDeleteSource() {
        return migrationSuspendBeforeDeleteSource;
    }

    public void setMigrationSuspendBeforeDeleteSource(boolean migrationSuspendBeforeDeleteSource) {
        this.migrationSuspendBeforeDeleteSource = migrationSuspendBeforeDeleteSource;
    }

    /*
     * Force Flag used to operate on internal objects.
     * 
     * @return True if force flag should be enabled
     */
    @XmlElement(name = "forceFlag")
    public boolean getForceFlag() {
        return forceFlag;
    }

    public void setForceFlag(boolean forceFlag) {
        this.forceFlag = forceFlag;
    }
}
