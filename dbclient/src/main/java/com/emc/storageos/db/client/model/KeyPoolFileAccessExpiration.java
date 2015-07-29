/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.db.client.model;

import javax.xml.bind.annotation.XmlRootElement;
import java.net.URI;
import java.util.Calendar;

/**
 * store all of key pool which may have file access enabled
 */
@Cf("KeyPoolFileAccessExpiration")
@XmlRootElement(name = "key_pool_file_access_expiration")
public class KeyPoolFileAccessExpiration extends DataObject {
    public KeyPoolFileAccessExpiration() {
        super();
    }

    public KeyPoolFileAccessExpiration(URI id) {
        super();
        setId(id);
        setCreationTime(Calendar.getInstance());
    }
}
