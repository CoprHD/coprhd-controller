/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.vnxe.models;

public class DedupParam {
    private Boolean isDeduplicationEnabled = true;

    public Boolean getIsDeduplicationEnabled() {
        return isDeduplicationEnabled;
    }

    public void setIsDeduplicationEnabled(Boolean isDeduplicationEnabled) {
        this.isDeduplicationEnabled = isDeduplicationEnabled;
    }

}
