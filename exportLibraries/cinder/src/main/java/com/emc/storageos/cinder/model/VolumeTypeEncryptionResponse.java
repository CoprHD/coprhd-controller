/* Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 *
 */
package com.emc.storageos.cinder.model;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;


/**
 * REST Response
 * {
 * "volume_type_id": "259f9eda-db02-40d5-a754-5f96d3780333", 
 * "control_location": "front-end", 
 * "deleted": false, 
 * "created_at": "2015-11-27T06:24:35.000000", 
 * "updated_at": null,
 * "key_size": 128, 
 * "provider": "LuksEncryptor",
 * "deleted_at": null,
 * "cipher": "aes-xts-plain64"
 * }

 *
 */
@XmlRootElement(name = "volume_type_encryption")
public class VolumeTypeEncryptionResponse {
    private VolumeTypeEncryption _encryption;

    @XmlElement(name="volume_type_encryption")
    public VolumeTypeEncryption getEncryption() {
        return _encryption;
    }

    public void setEncryption(VolumeTypeEncryption _encryption) {
        this._encryption = _encryption;
    }


}
