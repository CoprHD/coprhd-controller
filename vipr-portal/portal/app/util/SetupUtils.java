/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package util;

import com.emc.storageos.coordinator.client.service.CoordinatorClient;
import com.emc.storageos.coordinator.common.Configuration;
import com.emc.storageos.coordinator.common.impl.ConfigurationImpl;
import com.emc.storageos.services.util.PlatformUtils;
import org.apache.commons.lang.StringUtils;
import play.Play;
import plugin.StorageOsPlugin;

import static com.emc.storageos.db.client.model.uimodels.InitialSetup.*;

public class SetupUtils {
    private static boolean complete = false;

    public static boolean isSetupComplete() {
        if (StorageOsPlugin.isEnabled()) {
            CoordinatorClient coordinatorClient = StorageOsPlugin.getInstance().getCoordinatorClient();
            Configuration setupConfig = coordinatorClient.queryConfiguration(CONFIG_KIND, CONFIG_ID);
            complete = (setupConfig != null) &&
                    StringUtils.equals(setupConfig.getConfig(COMPLETE), Boolean.TRUE.toString());
        }
        // In Dev mode we don't have coordinator so assume always setup
        else if (Play.mode.isDev()) {
            complete = true;
        }
        else {
            complete = false;
        }

        return complete;
    }

    public static void markSetupComplete() {
        if (complete) {
            return;
        }

        if (StorageOsPlugin.isEnabled()) {
            CoordinatorClient coordinatorClient = StorageOsPlugin.getInstance().getCoordinatorClient();
            ConfigurationImpl config = new ConfigurationImpl();
            config.setKind(CONFIG_KIND);
            config.setId(CONFIG_ID);
            config.setConfig(COMPLETE, Boolean.TRUE.toString());
            coordinatorClient.persistServiceConfiguration(config);
            complete = true;
        } else if (Play.mode.isDev()) {
            complete = true;
        }
    }

    /**
     * Checks if the build is a open source build or emc enterprise build
     * 
     * @return true if it is an open source build otherwise false.
     */
    public static boolean isOssBuild() {
        return PlatformUtils.isOssBuild();
    }

    /**
     * Check if current deployment is an appliance
     * @return true if it is an appliance, otherwise false(e.g.: devkit)
     */

    public static boolean isAppliance() {
        return PlatformUtils.isAppliance();
    }
}
