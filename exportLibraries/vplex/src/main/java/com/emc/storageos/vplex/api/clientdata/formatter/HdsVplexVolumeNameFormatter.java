package com.emc.storageos.vplex.api.clientdata.formatter;

import com.emc.storageos.vplex.api.VPlexApiConstants;
import com.emc.storageos.vplex.api.clientdata.VolumeInfo;

public class HdsVplexVolumeNameFormatter extends DefaultVplexVolumeNameFormatter {

	public HdsVplexVolumeNameFormatter(VolumeInfo volumeInfo) {
		super(volumeInfo);
		this._volumeInfo = volumeInfo;
		String storageSystemNativeGuid = _volumeInfo.getStorageSystemNativeGuid();
		//replacing dot operator with hyphen(instead of underscore as done by
		//DefaultVplexVolumeNameFormatter) - check COP-33663
		storageSystemNativeGuid = storageSystemNativeGuid
				.replace(VPlexApiConstants.DOT_OPERATOR,
						VPlexApiConstants.HYPHEN_OPERATOR);
		_storageSystemSerialNumber = storageSystemNativeGuid.substring(
				storageSystemNativeGuid.indexOf(VPlexApiConstants.PLUS_OPERATOR) + 1);
		_volumeNativeId = _volumeInfo.getVolumeNativeId();

	}

}
