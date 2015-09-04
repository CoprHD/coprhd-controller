/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */

package util;

import com.emc.storageos.db.client.model.VcenterDataCenter;
import com.emc.storageos.model.host.vcenter.VcenterDataCenterRestRep;
import com.emc.storageos.model.host.vcenter.VcenterDataCenterUpdate;
import com.emc.storageos.model.host.vcenter.VcenterRestRep;
import com.emc.vipr.client.exceptions.ViPRHttpException;

import java.net.URI;
import java.util.List;

import static util.BourneUtil.getViprClient;

public class VcenterDataCenterUtils {
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

    public static VcenterDataCenterRestRep updateDataCenter(URI id, URI tenantId) {
        VcenterDataCenterUpdate update = new VcenterDataCenterUpdate();
        VcenterDataCenterRestRep dataCenter = getDataCenter(id);
        if (dataCenter != null) {
            update.setName(dataCenter.getName());
            update.setTenant(tenantId);
            return getViprClient().vcenterDataCenters().update(id, update);
        }

        return null;
    }
}
