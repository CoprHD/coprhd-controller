/*
 * Copyright (c) 2018 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.vplex.api.clientdata.formatter;

import com.emc.storageos.vplex.api.VPlexApiConstants;
import com.emc.storageos.vplex.api.clientdata.VolumeInfo;

/**
 * @{inheritDoc
 */
public class HdsVplexVolumeNameFormatter extends DefaultVplexVolumeNameFormatter {
	/**
	 * @{inheritDoc
	 */
	public HdsVplexVolumeNameFormatter(VolumeInfo volumeInfo) {
		super(volumeInfo);
		//replacing underscores with hyphen as underscores are being used as separator between storage system serial 
		//number and the volume native id when volume name is created. (check COP-33663)
		_storageSystemSerialNumber = _storageSystemSerialNumber.replace(VPlexApiConstants.UNDERSCORE_OPERATOR,
				VPlexApiConstants.HYPHEN_OPERATOR);				
	}

}
