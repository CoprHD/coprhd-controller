/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.model.vpool;

import javax.xml.bind.annotation.XmlRootElement;

/**
 * Parameter to Object VirtualPool update.
 */
@XmlRootElement(name = "object_vpool_update")
public class ObjectVirtualPoolUpdateParam extends VirtualPoolUpdateParam {

    public ObjectVirtualPoolUpdateParam() {
    }

}
