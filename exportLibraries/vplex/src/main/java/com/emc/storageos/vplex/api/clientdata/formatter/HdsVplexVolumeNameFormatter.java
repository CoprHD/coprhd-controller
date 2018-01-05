package com.emc.storageos.vplex.api.clientdata.formatter;

import com.emc.storageos.vplex.api.VPlexApiConstants;
import com.emc.storageos.vplex.api.clientdata.VolumeInfo;

public class HdsVplexVolumeNameFormatter extends DefaultVplexVolumeNameFormatter {

	public HdsVplexVolumeNameFormatter(VolumeInfo volumeInfo) {
		super(volumeInfo);
		//replacing underscores with hyphen (check COP-33663)
		_storageSystemSerialNumber = _storageSystemSerialNumber.replace(VPlexApiConstants.UNDERSCORE_OPERATOR,
				VPlexApiConstants.HYPHEN_OPERATOR);				
	}

}
