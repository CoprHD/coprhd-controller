/*
 * Copyright (c) 2013 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.vplex.api.clientdata;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.vplex.api.VPlexApiBackendSystemType;
import com.emc.storageos.vplex.api.VPlexApiConstants;
import com.emc.storageos.vplex.api.VPlexApiException;
import com.emc.storageos.vplex.api.clientdata.formatter.CinderVPlexVolumeNameFormatter;
import com.emc.storageos.vplex.api.clientdata.formatter.DefaultVplexVolumeNameFormatter;
import com.emc.storageos.vplex.api.clientdata.formatter.XtremioVplexVolumeNameFormatter;

/**
 * Bean specifying native volume information. Is passed from the client to
 * identify native backend volumes.
 */
public class VolumeInfo implements Serializable {
    private static final long serialVersionUID = 8156741332010435117L;

    // A logger reference.
    protected static Logger s_logger = LoggerFactory.getLogger(VolumeInfo.class);

    // The native Guid for the storage system that owns the volume.
    private String _storageSystemNativeGuid;

    // The device type of the storage system that owns the volume.
    private VPlexApiBackendSystemType _systemType;

    // The WWN for the volume.
    private String _volumeWWN;

    // The native volume id.
    private String _volumeNativeId;

    // The name for the claimed storage volume.
    private String _volumeName;

    // Whether or not the volume is thin provisioned.
    private boolean _isThin = false;

    // ITL List
    private List<String> _itls = new ArrayList<String>();

    /**
     * Constructor.
     * 
     * @param storageSystemNativeGuid The native guid for the storage system.
     * @param storageSystemType The device type of the storage system.
     * @param volumeWWN The WWN of the volume in all caps and no colons.
     * @param volumeNativeId The naive id of the backend volume.
     * @param true if the volume is thin provisioned, false otherwise
     */

    public VolumeInfo(String storageSystemNativeGuid, String storageSystemType,
            String volumeWWN, String volumeNativeId, boolean isThin,
            List<String> itls) {
        _storageSystemNativeGuid = storageSystemNativeGuid;
        setSystemType(storageSystemType);

        // 1- VPLEX trims the leading zeros in WWN
        // 2- IBM XIV volume nativeID contains "." which is not accepted during virtual volume create
        _volumeWWN = volumeWWN.replaceAll("^0+", "");
        _volumeNativeId = volumeNativeId.replace(VPlexApiConstants.DOT_OPERATOR,
                VPlexApiConstants.UNDERSCORE_OPERATOR);
        _isThin = isThin;
        _itls = itls;
    }

    /**
     * Getter for the ITL data
     * ITL item is of the format <Initiator Port WWN>-<Target Port WWN>-<LUN ID>
     * 
     * @return
     */
    public List<String> getITLs() {
        return _itls;
    }

    /**
     * Setter for the ITL data
     * ITL item is of the format <Initiator Port WWN>-<Target Port WWN>-<LUN ID>
     * 
     * @param iTLs
     */
    public void setITLs(List<String> iTLs) {
        _itls = iTLs;
    }

    /**
     * Getter for the storage system native guid.
     * 
     * @return The storage system native guid.
     */
    public String getStorageSystemNativeGuid() {
        return _storageSystemNativeGuid;
    }

    /**
     * Setter for the storage system native guid.
     * 
     * @param storageSystemNativeGuid The storage system native guid.
     */
    public void setStorageSystemNativeGuid(String storageSystemNativeGuid) {
        _storageSystemNativeGuid = storageSystemNativeGuid;
    }

    /**
     * Getter for the storage system device type.
     * 
     * @return the storage system device type.
     */
    public VPlexApiBackendSystemType getSystemType() {
        return _systemType;
    }

    /**
     * Setter for the storage system device type.
     * 
     * @param _systemType the storage system device type.
     */
    public void setSystemType(String systemType) {
        this._systemType = VPlexApiBackendSystemType.valueOfType(systemType);

        if (null == this._systemType) {
            s_logger.error("system type {} is not currently supported", systemType);
            throw VPlexApiException.exceptions.systemTypeNotSupported(systemType);
        }
    }

    /**
     * Getter for the volume WWN.
     * 
     * @return The volume WWN.
     */
    public String getVolumeWWN() {
        return _volumeWWN;
    }

    /**
     * Setter for the volume WWN.
     * 
     * @param volumeWWN The volume WWN in all caps and no colons.
     */
    public void setVolumeWWN(String volumeWWN) {
        _volumeWWN = volumeWWN;
    }

    /**
     * Getter for the volume native id.
     * 
     * @return The volume native id.
     */
    public String getVolumeNativeId() {
        return _volumeNativeId;
    }

    /**
     * Setter for the volume native id.
     * 
     * @param volumeName The volume native id.
     */
    public void setVolumeNativeId(String volumeNativeId) {
        _volumeNativeId = volumeNativeId;
    }

    /**
     * Getter for the volume name. When not set specifically, we generate the
     * name by appending the serial number of the array, extracted from the
     * array native guid, to the native volume id. This is the name given the
     * volume when it is claimed.
     * 
     * @return The volume name.
     */
    public String getVolumeName() {
        if ((_volumeName == null) || (_volumeName.length() == 0)) {
            switch (getSystemType()) {
                case XTREMIO:
                    _volumeName = new XtremioVplexVolumeNameFormatter(this).format();
                    break;
                case OPENSTACK:
                    _volumeName = new CinderVPlexVolumeNameFormatter(this).format();
                    break;
                default:
                    _volumeName = new DefaultVplexVolumeNameFormatter(this).format();
                    break;
            }
        }

        s_logger.info("claimed volume name is " + _volumeName);
        return _volumeName;
    }

    /**
     * Setter for the volume name.
     * 
     * @param volumeName The name to give the volume when claimed.
     * @throws VPlexApiException when the volumeName argument is too long.
     */
    public void setVolumeName(String volumeName) throws VPlexApiException {

        if (volumeName.length() > VPlexApiConstants.MAX_VOL_NAME_LENGTH) {
            throw VPlexApiException.exceptions.claimedVolumeNameIsTooLong(volumeName,
                    String.valueOf(VPlexApiConstants.MAX_VOL_NAME_LENGTH));
        }

        _volumeName = volumeName;
    }

    /**
     * Getter returns whether or not the volume is thin provisioned.
     * 
     * @return true if the volume is thin provisioned, false otherwise.
     */
    public boolean getIsThinProvisioned() {
        return _isThin;
    }

    /**
     * Setter for whether or not the volume is thin provisioned.
     * 
     * @param isThin true if the volume is thin provisioned, false otherwise.
     */
    public void setIsThinProvisioned(boolean isThin) {
        _isThin = isThin;
    }

}
