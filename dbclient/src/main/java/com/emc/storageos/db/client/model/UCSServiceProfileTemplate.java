/**
* Copyright 2015 EMC Corporation
* All Rights Reserved
 */
/**
 * 
 */
package com.emc.storageos.db.client.model;

import java.net.URI;

/**
 * @author Prabhakara,Janardhan
 * 
 */
@Cf("UCSServiceProfileTemplate")
public class UCSServiceProfileTemplate extends DiscoveredSystemObject {

    private URI computeSystem;

    private String dn;

    private Integer numberOfVNICS;
    private Integer numberOfVHBAS;

    /**
     * Updating or non-updating
     */
    private Boolean updating;

    /**
     * This is the dn of the Associated Server Pool
     */
    private String associatedServerPool;

    private String templateType;

    @RelationIndex(cf = "ComputeRelationIndex", type = ComputeSystem.class)
    @Name("computeSystem")
    public URI getComputeSystem() {
        return computeSystem;
    }

    public void setComputeSystem(URI computeSystem) {
        this.computeSystem = computeSystem;
        setChanged("computeSystem");
    }

    /**
     * This is the dn of the Associated Boot Policy
     */
    private String associatedBootPolicy;

    @Name("numberOfVNICS")
    public Integer getNumberOfVNICS() {
        return numberOfVNICS;
    }

    public void setNumberOfVNICS(Integer numberOfVNICS) {
        this.numberOfVNICS = numberOfVNICS;
        setChanged("numberOfVNICS");
    }

    @Name("numberOfVHBAS")
    public Integer getNumberOfVHBAS() {
        return numberOfVHBAS;
    }

    public void setNumberOfVHBAS(Integer numberOfVHBAS) {
        this.numberOfVHBAS = numberOfVHBAS;
        setChanged("numberOfVHBAS");
    }

    @Name("updating")
    public Boolean getUpdating() {
        return updating;
    }

    public void setUpdating(Boolean updating) {
        this.updating = updating;
        setChanged("updating");
    }

    @Name("associatedServerPool")
    public String getAssociatedServerPool() {
        return associatedServerPool;
    }

    public void setAssociatedServerPool(String associatedServerPool) {
        this.associatedServerPool = associatedServerPool;
        setChanged("associatedServerPool");
    }

    @Name("associatedBootPolicy")
    public String getAssociatedBootPolicy() {
        return associatedBootPolicy;
    }

    public void setAssociatedBootPolicy(String associatedBootPolicy) {
        this.associatedBootPolicy = associatedBootPolicy;
        setChanged("associatedBootPolicy");
    }

    @Name("dn")
    public String getDn() {
        return dn;
    }

    public void setDn(String dn) {
        this.dn = dn;
        setChanged("dn");
    }

    @Name("templateType")
    public String getTemplateType() {
        return templateType;
    }

    public void setTemplateType(String templateType) {
        this.templateType = templateType;
        setChanged("templateType");
    }
}
