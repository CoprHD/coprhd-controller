/**
* Copyright 2015 EMC Corporation
* All Rights Reserved
 */
package util;

import static com.emc.vipr.client.core.util.ResourceUtils.createNamedRef;
import static com.emc.vipr.client.core.util.ResourceUtils.id;
import static com.emc.vipr.client.core.util.ResourceUtils.refNames;
import static com.emc.vipr.client.core.util.ResourceUtils.uri;
import static com.emc.vipr.client.core.util.ResourceUtils.mapNames;
import static util.BourneUtil.getViprClient;

import java.net.URI;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

import models.RegistrationStatus;
import models.ConnectivityTypes;

import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang.text.StrBuilder;

import com.emc.storageos.model.NamedRelatedResourceRep;
import com.emc.storageos.model.RelatedResourceRep;
import com.emc.storageos.model.pools.StoragePoolRestRep;
import com.emc.storageos.model.ports.StoragePortRestRep;
import com.emc.storageos.model.smis.StorageProviderRestRep;
import com.emc.storageos.model.systems.StorageSystemConnectivityRestRep;
import com.emc.storageos.model.systems.StorageSystemRequestParam;
import com.emc.storageos.model.systems.StorageSystemRestRep;
import com.emc.storageos.model.systems.StorageSystemUpdateRequestParam;
import com.emc.storageos.model.varray.NetworkRestRep;
import com.emc.vipr.client.Task;
import com.emc.vipr.client.core.util.CachedResources;
import com.emc.vipr.client.core.util.RelatedResourceComparator;
import com.emc.vipr.client.exceptions.ViPRHttpException;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;

import controllers.security.Security;

public class StorageSystemUtils {
    private static final String NAME_NOT_AVAILABLE = "StorageSystems.nameNotAvailable";

    public static CachedResources<StorageSystemRestRep> createCache() {
        return new CachedResources<StorageSystemRestRep>(getViprClient().storageSystems());
    }

    public static StorageSystemRestRep getStorageSystem(String id) {
        return getStorageSystem(uri(id));
    }

    public static StorageSystemRestRep getStorageSystem(URI id) {
        try {
            return getViprClient().storageSystems().get(id);
        }
        catch (ViPRHttpException e) {
            if (e.getHttpCode() == 404) {
                return null;
            }
            throw e;
        }
    }

    public static NamedRelatedResourceRep getStorageSystemRef(RelatedResourceRep ref) {
        return getStorageSystemRef(id(ref));
    }

    public static NamedRelatedResourceRep getStorageSystemRef(URI id) {
        if (Security.hasAnyRole(Security.SYSTEM_ADMIN, Security.SYSTEM_MONITOR)) {
            return createNamedRef(getViprClient().storageSystems().get(id));
        }
        else {
            // No other alternative
            return null;
        }
    }

    public static Map<URI, String> getStorageSystemNames() {
        return mapNames(getViprClient().storageSystems().list());
    }

    public static List<StorageSystemRestRep> getStorageSystems() {
        return getViprClient().storageSystems().getAll();
    }

    public static List<StorageSystemRestRep> getStorageSystemsByVirtualArray(String virtualArrayId) {
        Set<URI> storageSystemIds = Sets.newLinkedHashSet();
        for (StoragePortRestRep port : StoragePortUtils.getStoragePortsByVirtualArray(uri(virtualArrayId))) {
            if (port.getStorageDevice() != null) {
                storageSystemIds.add(port.getStorageDevice().getId());
            }
        }
        for (StoragePoolRestRep pool : StoragePoolUtils.getStoragePoolsAssignedToVirtualArray(virtualArrayId)) {
            if (pool.getStorageSystem() != null) {
                storageSystemIds.add(pool.getStorageSystem().getId());
            }
        }
        return getViprClient().storageSystems().getByIds(storageSystemIds);
    }

    public static List<StorageSystemRestRep> getStorageSystemsByNetwork(NetworkRestRep network,
            CachedResources<StorageSystemRestRep> storageSystemsCache) {
        Set<URI> storageSystemIds = Sets.newLinkedHashSet();
        for (StoragePortRestRep port : getViprClient().storagePorts().getByNetwork(network.getId())) {
            if (port.getStorageDevice() != null) {
                storageSystemIds.add(port.getStorageDevice().getId());
            }
        }
        return storageSystemsCache.getByIds(storageSystemIds);
    }

    public static List<StorageSystemRestRep> getStorageSystems(Collection<URI> ids) {
        return getViprClient().storageSystems().getByIds(ids);
    }

    public static List<StorageSystemConnectivityRestRep> getConnectivity(StorageSystemRestRep storageSystem) {
        if (RegistrationStatus.isRegistered(storageSystem.getRegistrationStatus())) {
            return getViprClient().storageSystems().getConnectivity(id(storageSystem));
        }
        else {
            return Lists.newArrayList();
        }
    }

    public static Map<String, Set<NamedRelatedResourceRep>> getProtectionConnectivityMap(
            StorageSystemRestRep storageSystem) {
        Map<String, Set<NamedRelatedResourceRep>> connectivityMap = Maps.newTreeMap();
        for (StorageSystemConnectivityRestRep connectivity : getConnectivity(storageSystem)) {
            for (String type : connectivity.getConnectionTypes()) {
                Set<NamedRelatedResourceRep> resources = connectivityMap.get(type);
                if (resources == null) {
                    resources = Sets.newTreeSet(new NamedRelatedResourceComparator());
                    connectivityMap.put(type, resources);
                }
                resources.add(connectivity.getProtectionSystem());
            }
        }
        return connectivityMap;
    }
    
    public static StorageProviderRestRep getStorageProvider(StorageSystemRestRep storageSystem) {
        if (isSMISManaged(storageSystem)) {
            return getViprClient().storageProviders().get(storageSystem.getActiveProvider());
        }
        else {
            return null;
        }
    }

    public static boolean isSMISManaged(StorageSystemRestRep storageSystem) {
        return StringUtils.isNotBlank(storageSystem.getSmisProviderIP());
    }

    public static String getName(StorageSystemRestRep storageSystem) {
        if (StringUtils.isNotBlank(storageSystem.getName())) {
            return storageSystem.getName();
        }
        else if (StringUtils.isNotBlank(storageSystem.getSerialNumber())) {
            return storageSystem.getSerialNumber();
        }
        else {
            return MessagesUtils.get(NAME_NOT_AVAILABLE);
        }
    }

    public static String formatProtectionSystemConnectivity(List<StorageSystemConnectivityRestRep> connectivityList) {
        Map<String, Set<NamedRelatedResourceRep>> protectionSystems = mapProtectionSystemConnectivity(connectivityList);

        StrBuilder result = new StrBuilder();
        for (Map.Entry<String, Set<NamedRelatedResourceRep>> entry : protectionSystems.entrySet()) {
            if (CollectionUtils.size(entry.getValue()) == 0) {
                continue;
            }

            String connectionType = ConnectivityTypes.getDisplayValue(entry.getKey());
            List<String> names = refNames(entry.getValue());
            Collections.sort(names);

            result.appendSeparator("; ");
            result.append(connectionType);
            result.append(" (");
            result.append(StringUtils.join(names, ", "));
            result.append(")");
        }
        return result.toString();
    }

    private static Map<String, Set<NamedRelatedResourceRep>> mapProtectionSystemConnectivity(
            List<StorageSystemConnectivityRestRep> connectivityList) {
        Map<String, Set<NamedRelatedResourceRep>> results = Maps.newTreeMap();
        for (StorageSystemConnectivityRestRep connectivity : connectivityList) {
            for (String connectionType : connectivity.getConnectionTypes()) {
                if (connectivity.getProtectionSystem() == null) {
                    continue;
                }
                Set<NamedRelatedResourceRep> values = results.get(connectionType);
                if (values == null) {
                    values = Sets.newTreeSet(new RelatedResourceComparator());
                    results.put(connectionType, values);
                }
                values.add(connectivity.getProtectionSystem());
            }
        }
        return results;
    }

    public static Task<StorageSystemRestRep> create(StorageSystemRequestParam param) {
        return getViprClient().storageSystems().create(param);
    }

    public static Task<StorageSystemRestRep> update(String id, StorageSystemUpdateRequestParam param) {
        return getViprClient().storageSystems().update(uri(id), param);
    }

    public static StorageSystemRestRep register(URI id) {
        return getViprClient().storageSystems().register(id);
    }

    public static StorageSystemRestRep deregister(URI id) {
        return getViprClient().storageSystems().deregister(id);
    }

    public static Task<StorageSystemRestRep> deactivate(URI id) {
        return getViprClient().storageSystems().deactivate(id);
    }

    public static Task<StorageSystemRestRep> discover(URI id) {
        return getViprClient().storageSystems().discover(id, "ALL");
    }
}
