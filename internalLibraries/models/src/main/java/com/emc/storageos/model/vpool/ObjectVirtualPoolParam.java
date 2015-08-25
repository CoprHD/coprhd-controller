/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.model.vpool;

import javax.xml.bind.annotation.XmlRootElement;

/**
 * Parameter to Object VirtualPool creation
 */
@XmlRootElement(name = "object_vpool_create")
public class ObjectVirtualPoolParam extends VirtualPoolCommonParam {


    public ObjectVirtualPoolParam() {
    }
}
