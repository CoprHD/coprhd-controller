/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package util;

import static com.emc.vipr.client.core.util.ResourceUtils.uri;
import static util.BourneUtil.getViprClient;

import java.net.URI;
import java.util.List;

import com.emc.storageos.model.block.tier.StorageTierRestRep;
import com.emc.vipr.client.exceptions.ViPRHttpException;

public class StorageTierUtils {
    public static StorageTierRestRep getStorageTier(String id) {
        return getStorageTier(uri(id));
    }

    public static StorageTierRestRep getStorageTier(URI id) {
        try {
            return getViprClient().storageTiers().get(id);
        } catch (ViPRHttpException e) {
            if (e.getHttpCode() == 404) {
                return null;
            }
            throw e;
        }
    }

    public static List<StorageTierRestRep> getStorageTiers(URI autoTierPolicyId) {
        return getViprClient().storageTiers().getByAutoTieringPolicy(autoTierPolicyId);
    }
}
