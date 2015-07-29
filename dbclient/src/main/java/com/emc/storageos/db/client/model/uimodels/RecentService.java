/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.db.client.model.uimodels;

import com.emc.storageos.db.client.model.*;

import java.net.URI;

@Cf("RecentService")
public class RecentService extends ModelObject {

    public static final String USER_ID = "userId";
    public static final String CATALOG_SERVICE_ID = "catalogServiceId";

    private String userId;

    private URI catalogServiceId;

    @AlternateId("UserToRecentService")
    @Name(USER_ID)
    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
        setChanged(USER_ID);
    }

    @RelationIndex(cf = "RelationIndex", type = CatalogService.class)
    @Name(CATALOG_SERVICE_ID)
    public URI getCatalogServiceId() {
        return catalogServiceId;
    }

    public void setCatalogServiceId(URI catalogServiceId) {
        this.catalogServiceId = catalogServiceId;
        setChanged(CATALOG_SERVICE_ID);
    }

}
