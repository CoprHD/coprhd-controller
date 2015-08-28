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

import com.emc.storageos.vplex.api.VPlexApiException;
import com.emc.storageos.vplex.api.clientdata.VolumeInfo;

/**
 * @{inheritDoc
 */
public class XtremioVplexVolumeNameFormatter extends
        DefaultVplexVolumeNameFormatter {

    /**
     * @{inheritDoc
     */
    public XtremioVplexVolumeNameFormatter(VolumeInfo volumeInfo) {
        super(volumeInfo);
    }

    /**
     * @{inheritDoc
     */
    @Override
    protected String shortenName(int shortenBy) throws VPlexApiException {

        s_logger.info("claimed volume name {} needs to be shortened by {} characters",
                _volumeInfo.getVolumeNativeId(), shortenBy);

        // in this case, lets shave off some of the front of the volume native id
        if (_volumeNativeId.length() > shortenBy) {
            return assembleDefaultName(_storageSystemSerialNumber, _volumeNativeId.substring(shortenBy));
        } else {
            s_logger.warn("the volume native id {} is not long enough to be "
                    + "used for shortening the volume name by {} characters, so we "
                    + "are just going to truncate the beginning of the whole name",
                    _volumeNativeId, shortenBy);
            String volumeName = assembleDefaultName(_storageSystemSerialNumber, _volumeNativeId);
            return volumeName.substring(shortenBy);
        }
    }

}
