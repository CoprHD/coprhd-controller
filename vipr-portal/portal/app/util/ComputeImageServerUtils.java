/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package util;

import static com.emc.vipr.client.core.util.ResourceUtils.uri;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import com.emc.storageos.model.compute.ComputeImageServerRestRep;
import com.emc.vipr.client.exceptions.ViPRHttpException;

public class ComputeImageServerUtils {
    private static final String NAME_NOT_AVAILABLE = "ComputeImageServer.nameNotAvailable";

    /*
     * public static CachedResources<ComputeImageServerRestRep> createCache() {
     * return new CachedResources<ComputeImageServerRestRep>(getViprClient().computeImageServers());
     * }
     */
    public static ComputeImageServerRestRep getComputeImageServer(String id) {
        return getComputeImageServer(uri(id));
    }

    public static ComputeImageServerRestRep getComputeImageServer(URI id) {
        try {
            ComputeImageServerRestRep cisrr = new ComputeImageServerRestRep();
            cisrr.setName("Dummy");
            return cisrr;
            // return getViprClient().computeImageServers().get(id);
        } catch (ViPRHttpException e) {
            if (e.getHttpCode() == 404) {
                return null;
            }
            throw e;
        }
    }

    public static List<ComputeImageServerRestRep> getComputeImageServers() {
        ComputeImageServerRestRep cisrr = new ComputeImageServerRestRep();
        cisrr.setName("Dummy");
        cisrr.setImageServerIp("10.247.87.195");
        cisrr.setId(uri("28798275"));
        ComputeImageServerRestRep cisrr2 = new ComputeImageServerRestRep();
        cisrr2.setName("Dummy2");
        cisrr2.setImageServerIp("10.247.87.196");
        cisrr2.setId(uri("287982752"));
        List<ComputeImageServerRestRep> cisrrl = new ArrayList();
        cisrrl.add(cisrr);
        cisrrl.add(cisrr2);
        return cisrrl;
        // return getViprClient().computeImageServers().getAll();
    }

    public static List<ComputeImageServerRestRep> getComputeImageServers(Collection<URI> ids) {
        ComputeImageServerRestRep cisrr = new ComputeImageServerRestRep();
        cisrr.setName("Dummy");
        cisrr.setImageServerIp("10.247.87.195");
        cisrr.setId(uri("28798275"));
        ComputeImageServerRestRep cisrr2 = new ComputeImageServerRestRep();
        cisrr2.setName("Dummy2");
        cisrr2.setImageServerIp("10.247.87.196");
        cisrr2.setId(uri("287982752"));
        List<ComputeImageServerRestRep> cisrrl = new ArrayList();
        cisrrl.add(cisrr);
        cisrrl.add(cisrr2);
        return cisrrl;
        // return getViprClient().computeImageServers().getByIds(ids);
    }

    /*
     * public static String getName(ComputeImageServerRestRep computeImageServer) {
     * if (StringUtils.isNotBlank(computeImageServer.getName())) {
     * return computeImageServer.getName();
     * }
     * else {
     * return MessagesUtils.get(NAME_NOT_AVAILABLE);
     * }
     * }
     * 
     * public static Task<ComputeImageServerRestRep> create(ComputeImageServerCreate param) {
     * return getViprClient().computeImageServers().create(param);
     * }
     * 
     * public static Task<ComputeImageServerRestRep> update(String id, ComputeImageServerUpdate param) {
     * return getViprClient().computeImageServers().update(uri(id), param);
     * }
     */
    public static void deactivate(URI id) {
        // getViprClient().computeImageServers().deactivate(id);
    }

    // public static Task<ComputeImageServerRestRep> cloneImageServer(ComputeImageServerCreate param) {
    // return getViprClient().computeImageServers().cloneImageServer(param);
    // }

}
