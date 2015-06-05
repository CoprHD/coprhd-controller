/**
* Copyright 2015 EMC Corporation
* All Rights Reserved
 */
package util;

import static com.emc.vipr.client.core.util.ResourceUtils.uri;
import static util.BourneUtil.getViprClient;

import java.net.URI;
import java.util.List;

import com.emc.storageos.model.NamedRelatedResourceRep;
import com.emc.storageos.model.vdc.VirtualDataCenterAddParam;
import com.emc.storageos.model.vdc.VirtualDataCenterModifyParam;
import com.emc.storageos.model.vdc.VirtualDataCenterRestRep;
import com.emc.storageos.model.vdc.VirtualDataCenterSecretKeyRestRep;
import com.emc.vipr.client.Task;
import com.emc.vipr.client.Tasks;
import com.emc.vipr.client.exceptions.ViPRHttpException;

public class VirtualDataCenterUtils {
    public static VirtualDataCenterRestRep get(String id) {
        try {
            return getViprClient().vdcs().get(uri(id));
        }
        catch (ViPRHttpException e) {
            if (e.getHttpCode() == 404) {
                return null;
            }
            throw e;
        }
    }
    
    public static List<VirtualDataCenterRestRep> listByIds(List<URI> ids) {
        return getViprClient().vdcs().getByIds(ids);
    }
    
    public static List<NamedRelatedResourceRep> list() {
        return getViprClient().vdcs().list();
    }
    
    public static Task<VirtualDataCenterRestRep> delete(URI id) {
        return getViprClient().vdcs().delete(id);
    }
    
    public static Task<VirtualDataCenterRestRep> update(URI id, VirtualDataCenterModifyParam input) {
        return getViprClient().vdcs().update(id, input);
    }
    
    public static Task<VirtualDataCenterRestRep> disconnect(URI id) {
        return getViprClient().vdcs().disconnect(id);
    }
    
    public static Task<VirtualDataCenterRestRep> reconnect(URI id) {
        return getViprClient().vdcs().reconnect(id);
    }

    public static Task<VirtualDataCenterRestRep> create(VirtualDataCenterAddParam input) {
        return getViprClient().vdcs().create(input);
    }
    
    public static VirtualDataCenterSecretKeyRestRep getSecretKey() {
        return getViprClient().vdcs().getSecretKey();
    }

    public static List<VirtualDataCenterRestRep> getAllVDCs() {
        return getViprClient().vdcs().getAll();
    }
    
    public static Tasks<VirtualDataCenterRestRep> getTasks(URI id) {
        return getViprClient().vdcs().getTasks(id);
    }
}
