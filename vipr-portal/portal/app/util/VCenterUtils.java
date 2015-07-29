/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
package util;

import com.emc.storageos.model.host.cluster.ClusterRestRep;
import com.emc.storageos.model.host.vcenter.VcenterCreateParam;
import com.emc.storageos.model.host.vcenter.VcenterDataCenterRestRep;
import com.emc.storageos.model.host.vcenter.VcenterRestRep;
import com.emc.storageos.model.host.vcenter.VcenterUpdateParam;
import com.emc.vipr.client.Task;
import com.emc.vipr.client.exceptions.ViPRHttpException;

import java.net.URI;
import java.util.List;

import static com.emc.vipr.client.core.util.ResourceUtils.id;
import static com.emc.vipr.client.core.util.ResourceUtils.uri;
import static util.BourneUtil.getViprClient;

public class VCenterUtils {

    public static List<VcenterRestRep> getVCenters(String tenantId) {
        return getViprClient().vcenters().getByTenant(uri(tenantId));
    }

    public static VcenterRestRep getVCenter(URI id) {
        try {
            return (id != null) ? getViprClient().vcenters().get(id) : null;
        } catch (ViPRHttpException e) {
            if (e.getHttpCode() == 404) {
                return null;
            }
            throw e;
        }
    }

    public static VcenterDataCenterRestRep getDataCenter(URI id) {
        try {
            return (id != null) ? getViprClient().vcenterDataCenters().get(id) : null;
        } catch (ViPRHttpException e) {
            if (e.getHttpCode() == 404) {
                return null;
            }
            throw e;
        }
    }

    public static VcenterRestRep getVCenter(VcenterDataCenterRestRep dataCenter) {
        return (dataCenter != null) ? getVCenter(id(dataCenter.getVcenter())) : null;
    }

    public static Task<VcenterRestRep> createVCenter(URI tenantId, VcenterCreateParam vcenterCreateParam, boolean validateConnection) {
        return getViprClient().vcenters().create(tenantId, vcenterCreateParam, validateConnection);
    }

    public static Task<VcenterRestRep> updateVCenter(URI vcenterId, VcenterUpdateParam vcenterUpdateParam, boolean validateConnection) {
        return getViprClient().vcenters().update(vcenterId, vcenterUpdateParam, validateConnection);
    }

    public static Task<VcenterRestRep> deactivateVCenter(URI id) {
        return getViprClient().vcenters().deactivate(id);
    }

    public static Task<VcenterRestRep> deactivateVCenter(URI vcenterId, boolean detachStorage) {
        return getViprClient().vcenters().deactivate(vcenterId, detachStorage);
    }

    public static Task<VcenterRestRep> detachStorage(URI vcenterId) {
        return getViprClient().vcenters().detachStorage(vcenterId);
    }

    public static List<VcenterDataCenterRestRep> getDataCentersInVCenter(VcenterRestRep vcenter) {
        return getViprClient().vcenterDataCenters().getByVcenter(id(vcenter));
    }

    public static List<ClusterRestRep> getClustersInDataCenter(VcenterDataCenterRestRep vcenterDataCenter) {
        return getViprClient().clusters().getByDataCenter(id(vcenterDataCenter));
    }

    public static Task<VcenterRestRep> discover(URI id) {
        return getViprClient().vcenters().discover(id);
    }

    public static boolean checkCompatibleVDCVersion(String expectedVersion) {
        return getViprClient().vdcs().isCompatibleVDCVersion(expectedVersion);
    }
}
