/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
/**
 *  Copyright (c) 2013 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
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

    public ProtectionSetRestRep() {}
    
    /**
     * The ID of the Protection System associated with this Protection Set.
     * @valid example: urn:storageos:ProtectionSystem:af627636-c65e-40e0-a613-323786131a62:
     */
    @XmlElement(name="protection_system")
    public RelatedResourceRep getProtectionSystem() {
        return protectionSystem;
    }

    public void setProtectionSystem(RelatedResourceRep protectionSystem) {
        this.protectionSystem = protectionSystem;
    }

    /**
     * The ID for this Protection Set.
     * @valid example: 103467
     */
    @XmlElement(name="protection_id")
    public String getProtectionId() {
        return protectionId;
    }

    public void setProtectionId(String protectionId) {
        this.protectionId = protectionId;
    }

    /**
     * The list of associated Volumes for this Protection Set.
     * @valid 0 or more Volume IDs
     * @valid example: urn:storageos:Volume:62cc6fe2-c373-469a-bec2-2e851b3a8177:
     */
    @XmlElementWrapper(name="volumes")
    @XmlElement(name="volume")
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
     * @valid example: urn:storageos:Project:31a8c875-2056-40ad-b847-30bf166f8c3b:
     */
    @XmlElement(name="project")
    public RelatedResourceRep getProject() {
        return project;
    }

    public void setProject(RelatedResourceRep project) {
        this.project = project;
    }

    /**
     * Current Protection Status for this Protection Set.
     * @valid ENABLED = Protection is enabled.
     * @valid DISABLED = Protection is disabled.
     * @valid DELETED = Protection no longer exists on Protection System.
     * @valid MIXED = Protection copies are in various states (enabled, disabled, paused), not representable by one status.
     * @valid PAUSED = Protection has been paused.
     */
    @XmlElement(name="protection_status")
    public String getProtectionStatus() {
        return protectionStatus;
    }

    public void setProtectionStatus(String protectionStatus) {
        this.protectionStatus = protectionStatus;
    }
}
