/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
package util;

import static com.emc.vipr.client.core.util.ResourceUtils.uri;
import static util.BourneUtil.getViprClient;

import java.net.URI;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import org.apache.commons.collections.CollectionUtils;

import com.emc.storageos.model.network.NetworkSystemCreate;
import com.emc.storageos.model.network.NetworkSystemRestRep;
import com.emc.storageos.model.network.NetworkSystemUpdate;
import com.emc.vipr.client.Task;
import com.emc.vipr.client.core.util.CachedResources;
import com.emc.vipr.client.exceptions.ViPRHttpException;
import com.google.common.collect.Lists;

public class NetworkSystemUtils {
    public static CachedResources<NetworkSystemRestRep> createCache() {
        return new CachedResources<NetworkSystemRestRep>(getViprClient().networkSystems());
    }

    public static NetworkSystemRestRep getNetworkSystem(String id) {
        return getNetworkSystem(uri(id));
    }

    public static NetworkSystemRestRep getNetworkSystem(URI id) {
        try {
            return getViprClient().networkSystems().get(id);
        }
        catch (ViPRHttpException e) {
            if (e.getHttpCode() == 404) {
                return null;
            }
            throw e;
        }
    }

    public static List<NetworkSystemRestRep> getNetworkSystems() {
        return getViprClient().networkSystems().getAll();
    }

    public static List<NetworkSystemRestRep> getNetworkSystems(List<URI> ids) {
        return getViprClient().networkSystems().getByIds(ids);
    }

    public static NetworkSystemRestRep register(URI id) {
        return getViprClient().networkSystems().register(id);
    }

    public static NetworkSystemRestRep deregister(URI id) {
        return getViprClient().networkSystems().deregister(id);
    }

    public static Task<NetworkSystemRestRep> deactivate(URI id) {
        return getViprClient().networkSystems().deactivate(id);
    }

    public static Task<NetworkSystemRestRep> discover(URI id) {
        return getViprClient().networkSystems().discover(id);
    }

    public static Task<NetworkSystemRestRep> create(NetworkSystemCreate networkSystem) {
        return getViprClient().networkSystems().create(networkSystem);
    }

    public static Task<NetworkSystemRestRep> update(String id, NetworkSystemUpdate networkSystem) {
        return getViprClient().networkSystems().update(uri(id), networkSystem);
    }
}
