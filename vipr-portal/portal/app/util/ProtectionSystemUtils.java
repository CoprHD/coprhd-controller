/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package util;

import static com.emc.vipr.client.core.util.ResourceUtils.id;
import static com.emc.vipr.client.core.util.ResourceUtils.uri;
import static util.BourneUtil.getViprClient;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.emc.storageos.model.protection.ProtectionSystemConnectivityRestRep;
import com.emc.storageos.model.protection.ProtectionSystemConnectivitySiteRestRep;
import com.emc.storageos.model.protection.ProtectionSystemRequestParam;
import com.emc.storageos.model.protection.ProtectionSystemRestRep;
import com.emc.storageos.model.protection.ProtectionSystemUpdateRequestParam;
import com.emc.storageos.model.systems.StorageSystemRestRep;
import com.emc.vipr.client.Task;
import com.emc.vipr.client.core.util.ResourceUtils;
import com.emc.vipr.client.exceptions.ViPRHttpException;
import com.google.common.collect.Sets;

public class ProtectionSystemUtils {
    public static ProtectionSystemRestRep getProtectionSystem(String id) {
        try {
            return getViprClient().protectionSystems().get(uri(id));
        } catch (ViPRHttpException e) {
            if (e.getHttpCode() == 404) {
                return null;
            }
            throw e;
        }
    }

    public static List<ProtectionSystemRestRep> getProtectionSystems() {
        return getViprClient().protectionSystems().getAll();
    }

    public static List<ProtectionSystemRestRep> getProtectionSystems(List<URI> ids) {
        return getViprClient().protectionSystems().getByIds(ids);
    }

    public static ProtectionSystemConnectivityRestRep getConnectivity(ProtectionSystemRestRep protectionSystem) {
        return getViprClient().protectionSystems().getConnectivity(id(protectionSystem));
    }

    public static Map<URI, StorageSystemRestRep> getStorageSystemMap(
            ProtectionSystemConnectivityRestRep protectionSystem) {
        Set<URI> ids = Sets.newHashSet();
        for (ProtectionSystemConnectivitySiteRestRep site : protectionSystem.getProtectionSites()) {
            ids.addAll(ResourceUtils.refIds(site.getStorageSystems()));
        }
        return ResourceUtils.mapById(getViprClient().storageSystems().getByIds(ids));
    }

    public static Task<ProtectionSystemRestRep> create(ProtectionSystemRequestParam protectionSystem) {
        return getViprClient().protectionSystems().create(protectionSystem);
    }

    public static Task<ProtectionSystemRestRep> update(String id, ProtectionSystemUpdateRequestParam protectionSystem) {
        return getViprClient().protectionSystems().update(uri(id), protectionSystem);
    }

    public static Task<ProtectionSystemRestRep> discover(URI id) {
        return getViprClient().protectionSystems().discover(id);
    }

    public static void deactivate(URI id) {
        getViprClient().protectionSystems().deactivate(id);
    }
}
