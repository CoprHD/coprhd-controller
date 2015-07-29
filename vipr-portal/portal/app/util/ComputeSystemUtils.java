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

import org.apache.commons.lang.StringUtils;

import com.emc.storageos.model.compute.ComputeElementRestRep;
import com.emc.storageos.model.compute.ComputeSystemRestRep;

import com.emc.storageos.model.compute.ComputeSystemCreate;
import com.emc.storageos.model.compute.ComputeSystemUpdate;

import com.emc.vipr.client.Task;
import com.emc.vipr.client.core.util.CachedResources;

import com.emc.vipr.client.exceptions.ViPRHttpException;

public class ComputeSystemUtils {
    private static final String NAME_NOT_AVAILABLE = "ComputeSystems.nameNotAvailable";

    public static CachedResources<ComputeSystemRestRep> createCache() {
        return new CachedResources<ComputeSystemRestRep>(getViprClient().computeSystems());
    }

    public static ComputeSystemRestRep getComputeSystem(String id) {
        return getComputeSystem(uri(id));
    }

    public static ComputeSystemRestRep getComputeSystem(URI id) {
        try {
            return getViprClient().computeSystems().get(id);
        } catch (ViPRHttpException e) {
            if (e.getHttpCode() == 404) {
                return null;
            }
            throw e;
        }
    }

    public static List<ComputeSystemRestRep> getComputeSystems() {
        return getViprClient().computeSystems().getAll();
    }

    public static List<ComputeSystemRestRep> getComputeSystems(Collection<URI> ids) {
        return getViprClient().computeSystems().getByIds(ids);
    }

    public static String getName(ComputeSystemRestRep computeSystem) {
        if (StringUtils.isNotBlank(computeSystem.getName())) {
            return computeSystem.getName();
        }
        else {
            return MessagesUtils.get(NAME_NOT_AVAILABLE);
        }
    }

    public static Task<ComputeSystemRestRep> create(ComputeSystemCreate param) {
        return getViprClient().computeSystems().create(param);
    }

    public static Task<ComputeSystemRestRep> update(String id, ComputeSystemUpdate param) {
        return getViprClient().computeSystems().update(uri(id), param);
    }

    public static ComputeSystemRestRep register(URI id) {
        return getViprClient().computeSystems().register(id);
    }

    public static ComputeSystemRestRep deregister(URI id) {
        return getViprClient().computeSystems().deregister(id);
    }

    public static Task<ComputeSystemRestRep> deactivate(URI id) {
        return getViprClient().computeSystems().deactivate(id);
    }

    public static Task<ComputeSystemRestRep> discover(URI id) {
        return getViprClient().computeSystems().discover(id);
    }

    public static List<ComputeElementRestRep> getComputeElements(String id) {
        return getViprClient().computeSystems().getComputeElements(uri(id));
    }

    public static ComputeElementRestRep getComputeElement(String id) {
        return getViprClient().computeElements().get(uri(id));
    }

    public static ComputeElementRestRep rediscoverElement(URI id) {
        return getViprClient().computeElements().rediscover(id);
    }

    public static ComputeElementRestRep registerElement(URI id) {
        return getViprClient().computeElements().register(id);
    }

    public static ComputeElementRestRep deregisterElement(URI id) {
        return getViprClient().computeElements().deregister(id);
    }

    public static List<ComputeElementRestRep> getAllComputeElements() {
        return getViprClient().computeElements().getAll();
    }
}
