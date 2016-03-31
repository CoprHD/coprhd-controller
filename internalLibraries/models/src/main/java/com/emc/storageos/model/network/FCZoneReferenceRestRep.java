/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.model.network;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import java.net.URI;

@XmlRootElement(name = "fc_zone_reference")
public class FCZoneReferenceRestRep {
    private String pwwnKey;
    private String zoneName;
    private URI networkSystemUri;
    private String fabricId;
    private URI volumeUri;
    private URI groupUri;
    private boolean createdByUser;

    public FCZoneReferenceRestRep() {
    }

    public FCZoneReferenceRestRep(String pwwnKey, String zoneName,
            URI networkSystemUri, String fabricId, URI volumeUri, URI groupUri) {
        this.pwwnKey = pwwnKey;
        this.zoneName = zoneName;
        this.networkSystemUri = networkSystemUri;
        this.fabricId = fabricId;
        this.volumeUri = volumeUri;
        this.groupUri = groupUri;
    }

    /**
     * The VSAN (Virtual Storage Area Network) ID used for zoning.
     * 
     */
    @XmlElement
    public String getFabricId() {
        return fabricId;
    }

    public void setFabricId(String fabricId) {
        this.fabricId = fabricId;
    }

    /**
     * The URI of the export group.
     * 
     */
    @XmlElement
    public URI getGroupUri() {
        return groupUri;
    }

    public void setGroupUri(URI groupUri) {
        this.groupUri = groupUri;
    }

    /**
     * The URI of the FC (Fibre Channel) switch last used for zoning.
     * 
     */
    @XmlElement
    public URI getNetworkSystemUri() {
        return networkSystemUri;
    }

    public void setNetworkSystemUri(URI networkSystemUri) {
        this.networkSystemUri = networkSystemUri;
    }

    /**
     * The port WWPN (World Wide Port Name) key.
     * 
     */
    @XmlElement
    public String getPwwnKey() {
        return pwwnKey;
    }

    public void setPwwnKey(String pwwnKey) {
        this.pwwnKey = pwwnKey;
    }

    /**
     * The URI of the volume used by the export group.
     * 
     */
    @XmlElement
    public URI getVolumeUri() {
        return volumeUri;
    }

    public void setVolumeUri(URI volumeUri) {
        this.volumeUri = volumeUri;
    }

    /**
     * The name of the zone.
     * 
     */
    @XmlElement
    public String getZoneName() {
        return zoneName;
    }

    public void setZoneName(String zoneName) {
        this.zoneName = zoneName;
    }

    /**
     * A flag that indicates if the zone was created by the user or by the system.
     * This flag is true when the zone is found on the network system , in other words,
     * created by the user, and re-used by the system, false when the zone is created
     * by the system
     * 
     */
    @XmlElement
    public boolean getCreatedByUser() {
        return createdByUser;
    }

    public void setCreatedByUser(boolean createdByUser) {
        this.createdByUser = createdByUser;
    }
}
