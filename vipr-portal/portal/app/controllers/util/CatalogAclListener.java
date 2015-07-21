/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */

package controllers.util;


import com.emc.storageos.coordinator.client.model.Constants;
import com.emc.storageos.coordinator.client.service.NodeListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import play.cache.Cache;

public class CatalogAclListener implements NodeListener {
    private static Logger log = LoggerFactory.getLogger(CatalogAclListener.class);

    public String getPath() {
        String path = String.format("/config/%s/%s",
                Constants.CATALOG_CONFIG,
                Constants.CATALOG_ACL_CHANGE);
        return path;
    }

    /**
     * called when category/service acl changed from sasvc.
     * it will clear play.Cache entirely.
     *
     * since there are objects other than catalog list cached in play.Cache, this
     * may have some performance impact to VDC/Varray/license/userinfo. since all
     * other objects are live relative short and not that heavy, the impact should
     * small.
     *
     */
    @Override
    public void nodeChanged() {
        log.info("category or service acl changed, clearing play.Cache");
        Cache.clear();
    }

    /**
     * called when connection state changed.
     */
    @Override
    public void connectionStateChanged(State state) {
        // do nothing
    }
}
