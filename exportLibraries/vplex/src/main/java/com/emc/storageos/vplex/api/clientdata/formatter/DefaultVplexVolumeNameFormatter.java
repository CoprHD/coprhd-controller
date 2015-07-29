/**
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */
package com.emc.storageos.vplex.api.clientdata.formatter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.vplex.api.VPlexApiConstants;
import com.emc.storageos.vplex.api.VPlexApiException;
import com.emc.storageos.vplex.api.clientdata.VolumeInfo;

/**
 * This class provides functionality for generating claimed
 * volume names formatted to fit within the VPLEX API's
 * maximum volume length for distributed volumes of 63
 * characters. A single volume name should take up no more
 * than half of this space so that a distributed volume name
 * consisting of two claimed volume names can be constructed.
 */
public class DefaultVplexVolumeNameFormatter {

    // A logger reference.
    protected static Logger s_logger = LoggerFactory.getLogger(DefaultVplexVolumeNameFormatter.class);

    // the VolumeInfo instance to work with, protected for use in child classes
    protected VolumeInfo _volumeInfo;

    // the storage system's serial number
    protected String _storageSystemSerialNumber;

    // the volume's native id
    protected String _volumeNativeId;

    /**
     * Constructor.
     * 
     * @param volumeInfo the VolumeInfo to work with for name formatting
     */
    public DefaultVplexVolumeNameFormatter(VolumeInfo volumeInfo) {
        this._volumeInfo = volumeInfo;

        String storageSystemNativeGuid = _volumeInfo.getStorageSystemNativeGuid();
        storageSystemNativeGuid = storageSystemNativeGuid
                .replace(VPlexApiConstants.DOT_OPERATOR,
                        VPlexApiConstants.UNDERSCORE_OPERATOR);
        _storageSystemSerialNumber = storageSystemNativeGuid.substring(
                storageSystemNativeGuid.indexOf(VPlexApiConstants.PLUS_OPERATOR) + 1);
        _volumeNativeId = _volumeInfo.getVolumeNativeId();
    }

    /**
     * Generates a claimed volume name string based on the
     * properties of the supplied VolumeInfo object. Can be
     * overridden by child classes to return a custom name format
     * for a non-default storage system type.
     * 
     * @param volumeInfo the VolumeInfo instance for the claimed volume
     * @return a formatted string
     */
    public String format() {
        s_logger.info("formatting claimed volume name " + _volumeNativeId);

        String volumeName = assembleDefaultName(_storageSystemSerialNumber, _volumeNativeId);
        if (volumeName.length() <= VPlexApiConstants.MAX_VOL_NAME_LENGTH) {
            return volumeName;
        } else {
            int shortenBy = volumeName.length() - VPlexApiConstants.MAX_VOL_NAME_LENGTH;
            return shortenName(shortenBy);
        }
    }

    /**
     * Creates a shortened claimed volume name string if the one generated
     * by default does not fit within the maximum volume name length. Can be
     * overridden by child classes to return a custom shortened name format.
     * 
     * @param volumeInfo the VolumeInfo instance for the claimed volume
     * @param shortenBy the number of characters by which to reduce the name's length
     * @return a formatted string that has been shortened
     *         to fit within the maximum volume name length
     * @throws VPlexApiException if the name cannot be shortened safely
     */
    protected String shortenName(int shortenBy) throws VPlexApiException {

        s_logger.info("claimed volume name {} needs to be shortened by {} characters",
                _volumeInfo.getVolumeNativeId(),
                shortenBy);

        // in this case, lets shave off some of the front of the storage system serial number
        if (_storageSystemSerialNumber.length() > shortenBy) {
            return assembleDefaultName(
                    _storageSystemSerialNumber.substring(shortenBy), _volumeNativeId);
        } else {
            s_logger.warn("the storage system serial number {} is not long enough to be "
                    + "used for shortening the volume name by {} characters, so we "
                    + "are just going to truncate the beginning of the whole name",
                    _storageSystemSerialNumber, shortenBy);
            String volumeName = assembleDefaultName(_storageSystemSerialNumber, _volumeNativeId);
            return volumeName.substring(shortenBy);
        }
    }

    /**
     * Assembles the volume name with the default format of the character
     * 'V' plus the storage system serial number plus a hyphen
     * plus the volume native id.
     * 
     * @param storageSystemSerialNumber the storage system's serial number
     * @param volumeNativeId the volume's native id
     * @return the volume name in the default format
     */
    protected String assembleDefaultName(String storageSystemSerialNumber, String volumeNativeId) {
        StringBuilder nameBuilder = new StringBuilder();
        // Note that we need to prepend the prefix because the VPlex does not
        // like the claimed storage volume name to start with a number, which
        // can be the case for Symmetrix volumes, whose serial numbers start
        // with a number.
        nameBuilder.append(VPlexApiConstants.VOLUME_NAME_PREFIX);
        nameBuilder.append(storageSystemSerialNumber);
        nameBuilder.append(VPlexApiConstants.HYPHEN_OPERATOR);
        nameBuilder.append(volumeNativeId);
        return nameBuilder.toString();
    }

}
