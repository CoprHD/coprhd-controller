/*
 * Copyright (c) 2008-2011 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.networkcontroller;

import java.io.Serializable;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

import com.emc.storageos.db.client.model.FCZoneReference;
import com.emc.storageos.exceptions.DeviceControllerException;

/**
 * This is a communication structure that carries the context for a SAN Zoning Request.
 * It identifies the fabric, network system, zone name, task, etc. to be used.
 * It serves as the communication vehicle between NetworkScheduler and NetworkDeviceController.
 */
@XmlRootElement
public class NetworkFCZoneInfo implements Serializable {

    @XmlElement
    private URI _networkDeviceId;			// The network device to be used

    @XmlElement
    private String _fabricId;				// Vsan or Fabric id

    @XmlElement
    private String _fabricWwn;

    @XmlElement
    private String _task;

    @XmlElement
    private List<String> _endPoints = new ArrayList<String>();		// Array of pwwns

    @XmlElement
    private String _zoneName;				// Existing zone name

    @XmlElement
    private URI _fcZoneReferenceId;			// URI to the FCZoneReference object for this request

    @XmlElement
    public boolean _isLastReference = false;		// only when true can zone be deleted

    @XmlElement
    private boolean _canBeRolledBack = false;		// this item eligible for rollback

    @XmlElement
    public URI _altNetworkDeviceId;		// An alternate network device to be used only if the primary fails

    @XmlElement
    public URI _volumeId;					// URI for the volume (this value only used in NetworkDeviceController)

    private boolean existingZone = false;   // a flag that indicates if the zone was found on the network system
    
    private URI _exportGroup;        // Export Group, used when zones for different Export Groups are in same zoning operations (like path adjustment)

    public boolean isExistingZone() {
        return existingZone;
    }

    public void setExistingZone(boolean existingZone) {
        this.existingZone = existingZone;
    }

    public NetworkFCZoneInfo(URI _networkDeviceId, String _fabricId, String _fabricWwn, String _task) {
        this(_networkDeviceId, _fabricId, _fabricWwn);
        this._task = _task;
    }

    public NetworkFCZoneInfo(URI _networkDeviceId, String _fabricId, String _fabricWwn) {
        if (_networkDeviceId == null) {
            throw DeviceControllerException.exceptions.entityNullOrEmpty("_networkDeviceId");
        }
        if (_fabricId == null) {
            throw DeviceControllerException.exceptions.entityNullOrEmpty("_fabricId");
        }
        this._networkDeviceId = _networkDeviceId;
        this._fabricId = _fabricId;
        this._fabricWwn = _fabricWwn;
    }

    public NetworkFCZoneInfo(URI _networkDeviceId, String _fabricId, String _fabricWwn, URI _volumeId) {
        this(_networkDeviceId, _fabricId, _fabricWwn);
        this._volumeId = _volumeId;
    }

    public NetworkFCZoneInfo() {
        super();
    }

    public URI getNetworkDeviceId() {
        return _networkDeviceId;
    }

    public void setNetworkDeviceId(URI _networkDeviceId) {
        this._networkDeviceId = _networkDeviceId;
    }

    public String getFabricId() {
        return _fabricId;
    }

    public void setFabricId(String _fabricId) {
        this._fabricId = _fabricId;
    }

    public String getTask() {
        return _task;
    }

    public void setTask(String _task) {
        this._task = _task;
    }

    public URI getFcZoneReferenceId() {
        return _fcZoneReferenceId;
    }

    public void setFcZoneReferenceId(URI _fcZoneReferenceId) {
        this._fcZoneReferenceId = _fcZoneReferenceId;
    }

    public boolean isLastReference() {
        return _isLastReference;
    }

    public void setLastReference(boolean _isLastReference) {
        this._isLastReference = _isLastReference;
    }

    public List<String> getEndPoints() {
        return _endPoints;
    }

    public void setEndPoints(List<String> _endPoints) {
        this._endPoints = _endPoints;
    }

    /**
     * This will make a key string consisting of the endPoints in sorted order.
     * This is used for the FCZoneReferenceKey structure.
     * 
     * @return
     */
    public String makeEndpointsKey() {
        return FCZoneReference.makeEndpointsKey(_endPoints);
    }

    @Override
    public int hashCode() {
        return toString().hashCode();
    }

    @Override
    public boolean equals(Object paramObject) {
        if (paramObject instanceof NetworkFCZoneInfo) {
            return toString().equals(paramObject.toString());
        }
        return false;
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder(_fabricId == null ? "" : _fabricId);
        List<String> sorted = new ArrayList<String>(_endPoints);
        Collections.sort(sorted);
        for (String endPoint : sorted) {
            builder.append(",");
            builder.append(endPoint);
        }
        // I am not sure what to do when neither fabric nor end points are specified
        return builder.length() == 0 ? super.toString() : builder.toString();
    }

    public URI getAltNetworkDeviceId() {
        return _altNetworkDeviceId;
    }

    public void setAltNetworkDeviceId(URI _altNetworkDeviceId) {
        this._altNetworkDeviceId = _altNetworkDeviceId;
    }

    public String getZoneName() {
        return _zoneName;
    }

    public void setZoneName(String zoneName) {
        this._zoneName = zoneName;
    }

    public URI getVolumeId() {
        return _volumeId;
    }

    public void setVolumeId(URI volumeId) {
        this._volumeId = volumeId;
    }

    public String getFabricWwn() {
        return _fabricWwn;
    }

    public void setFabricWwn(String fabricWwn) {
        this._fabricWwn = fabricWwn;
    }

    public boolean canBeRolledBack() {
        return _canBeRolledBack;
    }

    public void setCanBeRolledBack(boolean b) {
        _canBeRolledBack = b;
    }

    public NetworkFCZoneInfo clone() {
        NetworkFCZoneInfo newInfo = new NetworkFCZoneInfo();
        newInfo.setAltNetworkDeviceId(_altNetworkDeviceId);
        newInfo.setEndPoints(_endPoints);
        newInfo.setExistingZone(existingZone);
        newInfo.setFabricId(_fabricId);
        newInfo.setFabricWwn(_fabricWwn);
        newInfo.setCanBeRolledBack(_canBeRolledBack);
        newInfo.setFcZoneReferenceId(_fcZoneReferenceId);
        newInfo.setLastReference(_isLastReference);
        newInfo.setNetworkDeviceId(_networkDeviceId);
        newInfo.setVolumeId(_volumeId);
        newInfo.setZoneName(_zoneName);
        newInfo.setExportGroup(_exportGroup);
        return newInfo;
    }

    public URI getExportGroup() {
        return _exportGroup;
    }

    public void setExportGroup(URI exportGroup) {
        this._exportGroup = exportGroup;
    }
}
