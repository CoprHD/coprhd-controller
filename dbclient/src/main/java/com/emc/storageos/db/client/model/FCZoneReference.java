/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.db.client.model;

import java.net.URI;
import java.util.Iterator;
import java.util.List;
import java.util.TreeSet;

import org.apache.commons.lang.StringUtils;

@Cf("FCZoneReference")
public class FCZoneReference extends DataObject {
    private static String NULL_KEY = "_no_pwwns_";

    private String _pwwnKey;			// the port wwpn key (concatenated xxxx_yyyy
    private String _zoneName;			// the zone name
    private URI _networkSystemUri;		// network system last used for zoning
    private String _fabricId;			// the fabric or Vsan ID
    private URI volumeUri;				// a volume reference
    private URI _groupUri;				// export group reference
    private Boolean _existingZone = false;  // true when the zone was found on the switch and not created by the application.

    @Name("pwwnKey")
    @AlternateId("KeyAltIdIndex")
    public String getPwwnKey() {
        return _pwwnKey;
    }

    public void setPwwnKey(String pwwnKey) {
        setChanged("pwwnKey");
        this._pwwnKey = pwwnKey;
    }

    @Name("networkSystemUri")
    @AlternateId("NSAltIdIndex")
    public URI getNetworkSystemUri() {
        return _networkSystemUri;
    }

    public void setNetworkSystemUri(URI networkSystemUri) {
        setChanged("networkSystemUri");
        this._networkSystemUri = networkSystemUri;
    }

    @Name("volumeUri")
    public URI getVolumeUri() {
        return volumeUri;
    }

    public void setVolumeUri(URI volumeUri) {
        setChanged("volumeUri");
        this.volumeUri = volumeUri;
    }

    @Name("groupUri")
    public URI getGroupUri() {
        return _groupUri;
    }

    public void setGroupUri(URI groupUri) {
        setChanged("groupUri");
        this._groupUri = groupUri;
    }

    @Name("fabricId")
    @AlternateId("FabricAltIdIndex")
    public String getFabricId() {
        return _fabricId;
    }

    public void setFabricId(String fabricId) {
        setChanged("fabricId");
        this._fabricId = fabricId;
    }

    @Name("zoneName")
    public String getZoneName() {
        return _zoneName;
    }

    public void setZoneName(String _zoneName) {
        setChanged("zoneName");
        this._zoneName = _zoneName;
    }

    @Name("existingZone")
    public Boolean getExistingZone() {
        return _existingZone == null ? Boolean.FALSE : _existingZone;
    }

    public void setExistingZone(Boolean existingZone) {
        _existingZone = existingZone;
        setChanged("existingZone");
    }

    /**
     * This will make a key string consisting of the endPoints in sorted order.
     * This is used for the FCZoneReferenceKey structure.
     * 
     * @return the zone reference key or "_no_pwwns_" when endPoints is empty.
     */
    public static String makeEndpointsKey(List<String> endpoints) {
        StringBuilder key = new StringBuilder();
        if (endpoints.isEmpty()) {
            return NULL_KEY;
        }
        TreeSet<String> set = new TreeSet<String>();
        set.addAll(endpoints);
        Iterator<String> iter = set.iterator();

        key.append(iter.next().replaceAll(":", "").toUpperCase());
        while (iter.hasNext()) {
            key.append("_").append(iter.next().replaceAll(":", "").toUpperCase());
        }
        return key.toString();
    }

    /**
     * This will make a key string consisting of the endPoints in sorted order.
     * This is used for the FCZoneReferenceKey structure.
     * 
     * @return the zone reference key or "_no_pwwns_" when endPoints is empty.
     */
    public static String makeEndpointsKey(String ep1, String ep2) {
        StringBuilder key = new StringBuilder();
        if (StringUtils.isEmpty(ep2) && StringUtils.isEmpty(ep1)) {
            return NULL_KEY;
        }
        TreeSet<String> set = new TreeSet<String>();
        set.add(ep1);
        set.add(ep2);
        Iterator<String> iter = set.iterator();

        key.append(iter.next().replaceAll(":", "").toUpperCase());
        while (iter.hasNext()) {
            key.append("_").append(iter.next().replaceAll(":", "").toUpperCase());
        }
        return key.toString();
    }

    /**
     * Generate a label
     * 
     * @param asList
     * @return a label that is unique
     */
    public static String makeLabel(List<String> asList) {
        return FCZoneReference.makeEndpointsKey(asList.get(0), asList.get(1)) + "_" + asList.get(2);
    }

    /**
     * Generate a label
     * 
     * @param pwwnKey - endpoint key
     * @param volId - block object id
     * @return a label that is unique
     */
    public static String makeLabel(String pwwnKey, String volId) {
        return pwwnKey + "_" + volId;
    }
}
