/*
 * Copyright (c) 2013 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.model.protection;

import java.util.ArrayList;
import java.util.List;
import javax.xml.bind.annotation.*;

import com.emc.storageos.model.DataObjectRestRep;
import com.emc.storageos.model.RelatedResourceRep;

@XmlRootElement(name = "protection_set")
@XmlAccessorType(XmlAccessType.PROPERTY)
public class ProtectionSetRestRep extends DataObjectRestRep {
    private String protectionId;
    private RelatedResourceRep protectionSystem;
    private List<RelatedResourceRep> volumes;
    private String protectionStatus;
    private RelatedResourceRep project;

    public ProtectionSetRestRep() {
    }

    /**
     * The ID of the Protection System associated with this Protection Set.
     * 
     */
    @XmlElement(name = "protection_system")
    public RelatedResourceRep getProtectionSystem() {
        return protectionSystem;
    }

    public void setProtectionSystem(RelatedResourceRep protectionSystem) {
        this.protectionSystem = protectionSystem;
    }

    /**
     * The ID for this Protection Set.
     * 
     */
    @XmlElement(name = "protection_id")
    public String getProtectionId() {
        return protectionId;
    }

    public void setProtectionId(String protectionId) {
        this.protectionId = protectionId;
    }

    /**
     * The list of associated Volumes for this Protection Set.
     * 
     */
    @XmlElementWrapper(name = "volumes")
    @XmlElement(name = "volume")
    public List<RelatedResourceRep> getVolumes() {
        if (volumes == null) {
            volumes = new ArrayList<RelatedResourceRep>();
        }
        return volumes;
    }

    public void setVolumes(List<RelatedResourceRep> volumes) {
        this.volumes = volumes;
    }

    /**
     * The ID of the Project associated with this Protection Set.
     * 
     */
    @XmlElement(name = "project")
    public RelatedResourceRep getProject() {
        return project;
    }

    public void setProject(RelatedResourceRep project) {
        this.project = project;
    }

    /**
     * Current Protection Status for this Protection Set.
     * 
     */
    @XmlElement(name = "protection_status")
    public String getProtectionStatus() {
        return protectionStatus;
    }

    public void setProtectionStatus(String protectionStatus) {
        this.protectionStatus = protectionStatus;
    }
}
