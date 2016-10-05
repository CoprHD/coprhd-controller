/*
 *
 *  * Copyright (c) 2016 EMC Corporation
 *  * All Rights Reserved
 *
 */

package jobs.vipr;

import com.emc.storageos.coordinator.client.service.ConfigurationUtil;
import com.emc.storageos.coordinator.client.service.CoordinatorClient;
import com.emc.storageos.coordinator.client.service.NodeListener;
import controllers.security.Security;
import play.Logger;
import play.cache.Cache;
import play.jobs.Job;
import play.jobs.OnApplicationStart;
import plugin.StorageOsPlugin;
import util.AuthSourceType;


/**
 *
 */
@OnApplicationStart
public class AuthModeBootstrap extends Job {

    private final static String AUTH_MODE_ZK_PATH = "/config/auth/auth";
    private static String AUTH_KIND = "auth";
    private static String AUTH_ID = "auth";

    private CoordinatorClient coordinator;
    private ConfigurationUtil configurationUtil;

    public void doJob() {
        try {
            coordinator = StorageOsPlugin.getInstance().getCoordinatorClient();
            configurationUtil = new ConfigurationUtil(coordinator);
	    getAuthModeFromZKandUpdateToCache(); 
            coordinator.addNodeListener(new AuthModeListener());

            Logger.info("AuthModeListener gets started. The current auth mode is %s", Cache.get(Security.AUTH_MODE_CACHE_KEY));
        } catch (Exception e) {
            Logger.error(e, "Error to start AuthModeListener.");
        }
    }

    private void getAuthModeFromZKandUpdateToCache() {
        Logger.info("Reading authmode from ZK");
        try {
            String authMode = configurationUtil.read(AUTH_KIND, AUTH_ID, Security.AUTH_MODE_CACHE_KEY);
            Cache.set(Security.AUTH_MODE_CACHE_KEY, authMode);
        } catch (Exception e) {
            Logger.warn(e, "Fail to read authmode from ZK");
        }

        if (Cache.get(Security.AUTH_MODE_CACHE_KEY) == null) {
            Logger.info("There is no Authmode set in cache, setting default one.");
            Cache.set(Security.AUTH_MODE_CACHE_KEY, Security.AuthModeType.normal.name());
        }

        Logger.info("Authmode is %s", Cache.get(Security.AUTH_MODE_CACHE_KEY));
    }

    private class AuthModeListener implements NodeListener {
        @Override
        public String getPath() {
            String path = String.format(AUTH_MODE_ZK_PATH);
            return path;
        }

        @Override
        public void connectionStateChanged(State state) {
            // Nothing required
        }

        @Override
        public void nodeChanged() throws Exception {
            getAuthModeFromZKandUpdateToCache();
        }
    }
}
