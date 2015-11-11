/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package util;

import static com.emc.vipr.client.core.util.ResourceUtils.uri;
import static util.BourneUtil.getViprClient;

import java.net.URI;
import java.util.Collection;
import java.util.List;

import com.emc.storageos.model.compute.ComputeImageServerCreate;
import com.emc.storageos.model.compute.ComputeImageServerRestRep;
import com.emc.storageos.model.compute.ComputeImageServerUpdate;
import com.emc.vipr.client.Task;
import com.emc.vipr.client.exceptions.ViPRHttpException;

public class ComputeImageServerUtils {
    private static final String NAME_NOT_AVAILABLE = "ComputeImageServer.nameNotAvailable";

    public static ComputeImageServerRestRep getComputeImageServer(String id) {
        return getComputeImageServer(uri(id));
    }

    public static ComputeImageServerRestRep getComputeImageServer(URI id) {
        try {
            return getViprClient().computeImageServers().get(id);
        } catch (ViPRHttpException e) {
            if (e.getHttpCode() == 404) {
                return null;
            }
            throw e;
        }
    }

    public static ComputeImageServerRestRep getComputeImageServerByName(String name) {
        try {
            ComputeImageServerRestRep computeImageServer = null;
            List<ComputeImageServerRestRep> computeImageServersList = getComputeImageServers();
            for (ComputeImageServerRestRep cisrr : computeImageServersList) {
                if (cisrr.getName().equalsIgnoreCase(name)) {
                    computeImageServer = cisrr;
                }
            }
            return computeImageServer;
        } catch (ViPRHttpException e) {
            if (e.getHttpCode() == 404) {
                return null;
            }
            throw e;
        }
    }

    public static List<ComputeImageServerRestRep> getComputeImageServers() {
        return getViprClient().computeImageServers().getAll();
    }

    public static List<ComputeImageServerRestRep> getComputeImageServers(Collection<URI> ids) {
        return getViprClient().computeImageServers().getByIds(ids);
    }

    public static Task<ComputeImageServerRestRep> create(ComputeImageServerCreate param) {
        return getViprClient().computeImageServers().create(param);
    }

    public static ComputeImageServerRestRep update(String id, ComputeImageServerUpdate param) {
        return getViprClient().computeImageServers().update(uri(id), param);
    }

    public static void deactivate(URI id) {
        getViprClient().computeImageServers().deactivate(id);
    }

}
