/*
 *
 *  * Copyright (c) 2016 EMC Corporation
 *  * All Rights Reserved
 *
 */

package jobs.vipr;

import com.emc.storageos.coordinator.client.service.ConnectionStateListener;
import com.emc.storageos.coordinator.client.service.CoordinatorClient;
import com.emc.storageos.coordinator.client.service.NodeListener;
import com.emc.storageos.security.ssl.ViPRX509TrustManager;
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

    private CoordinatorClient coordinator;

    public void doJob() {
        try {
            Cache.set(Security.AUTH_MODE, AuthSourceType.oidc.name());

            coordinator = StorageOsPlugin.getInstance().getCoordinatorClient();
            coordinator.addNodeListener(new AuthModeListener());

            Logger.info("AuthModeListener gets started. The current auth mode is ", Cache.get(Security.AUTH_MODE));
        } catch (Exception e) {
            Logger.error("Error to start AuthModeListener.", e);
        }
    }

    private class AuthModeListener implements NodeListener {
        @Override
        public String getPath() {
            return null;
        }

        @Override
        public void connectionStateChanged(State state) {

        }

        @Override
        public void nodeChanged() throws Exception {
            getAuthModeFromZKandUpdateToCache();
        }

        private void getAuthModeFromZKandUpdateToCache() {
            Cache.set("authmode", "oidc");
        }
    }
}
