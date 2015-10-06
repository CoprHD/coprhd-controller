/**
 *  Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */
package com.emc.storageos.cinder.model;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

//REST response for the Show volume type query
@XmlRootElement(name = "volume_type")
public class VolumeTypeRestResp {

	private VolumeType _type;
	
	@XmlElement(name="volume_type")
	public VolumeType getVolumeType(){
		if (_type == null){
			_type = new VolumeType();
		}
		return _type;
	}

	public void setVolumeType(VolumeType type){
		_type = type;
	}
	
}
