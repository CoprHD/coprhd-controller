/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.vnxe.models;

import javax.xml.bind.annotation.XmlRootElement;

@XmlRootElement
public class DeleteStorageResourceParam extends ParamBase {
    private boolean forceSnapDeletion;

    public boolean getForceSnapDeletion() {
        return forceSnapDeletion;
    }

    public void setForceSnapDeletion(boolean forceSnapDeletion) {
        this.forceSnapDeletion = forceSnapDeletion;
    }

}
