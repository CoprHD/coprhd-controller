/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.geo.service.impl.resource;

import com.emc.storageos.coordinator.client.model.Site;
import com.emc.storageos.coordinator.client.model.SiteInfo;
import com.emc.storageos.coordinator.client.service.CoordinatorClient;
import com.emc.storageos.coordinator.client.service.DrUtil;
import com.emc.storageos.geomodel.VdcIpsecPropertiesResponse;
import com.emc.storageos.model.ipsec.IpsecParam;
import com.emc.storageos.security.geo.GeoServiceClient;
import com.emc.storageos.security.ipsec.IPsecConfig;
import com.emc.storageos.security.ipsec.IpUtils;
import com.emc.storageos.systemservices.impl.upgrade.LocalRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import java.util.Map;

import static com.emc.storageos.coordinator.client.model.Constants.IPSEC_KEY;
import static com.emc.storageos.coordinator.client.model.Constants.IPSEC_STATUS;
import static com.emc.storageos.coordinator.client.model.Constants.VDC_CONFIG_VERSION;

@Path(value = GeoServiceClient.INTERVDC_IPSEC_SERVICE)
public class IpsecService {

    private final static Logger log = LoggerFactory.getLogger(IpsecService.class);

    @Autowired
    private CoordinatorClient coordinator;

    @Autowired
    private IPsecConfig ipsecConfig;

    @POST
    @Produces({MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON})
    public void changeIpsecStatus(@QueryParam("status") String status,
                                  @QueryParam("vdc_config_version") String vdcConfigVersion) {
        log.info("Processing a request for changing ipsec status: status="
                + status + ", vdc_config_version=" + vdcConfigVersion);

        ipsecConfig.setIpsecStatus(status);
        log.info("Saved the ipsec status to ZK");

        updateTargetSiteInfo(Long.parseLong(vdcConfigVersion));
    }

    @POST
    @Produces({MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON})
    @Path("/key")
    public void rotateKey(IpsecParam ipsecParam) {
        log.info("Getting a request for ipsec key rotation");

        ipsecConfig.setPreSharedKey(ipsecParam.getIpsecKey());
        log.info("Saved the ipsec key to ZK");

        updateTargetSiteInfo(ipsecParam.getVdcConfigVersion());
    }

    @GET
    @Produces({MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON})
    @Path("/properties")
    public VdcIpsecPropertiesResponse getIpsecProperties() {
        log.info("in getIpsecProperties()");
        VdcIpsecPropertiesResponse response = new VdcIpsecPropertiesResponse();
        try {
            String localIP = IpUtils.getLocalIPAddress();
            Map<String, String> localIpsecProp = LocalRepository.getInstance().getIpsecProperties(localIP);
            response.setIpsecKey(localIpsecProp.get(IPSEC_KEY));
            response.setIpsecStatus(localIpsecProp.get(IPSEC_STATUS));
            response.setVdcConfigVersion(localIpsecProp.get(VDC_CONFIG_VERSION));
        } catch (Exception e) {
            log.error("retrieve local ipsec properties error: " + e.getMessage());

        }
        return response;
    }

    private void updateTargetSiteInfo(long vdcConfigVersion) {
        DrUtil drUtil = new DrUtil(coordinator);

        for (Site site : drUtil.listSites()) {
            SiteInfo siteInfo;
            String siteId = site.getUuid();

            SiteInfo currentSiteInfo = coordinator.getTargetInfo(siteId, SiteInfo.class);
            if (currentSiteInfo != null) {
                siteInfo = new SiteInfo(vdcConfigVersion, SiteInfo.IPSEC_OP_ROTATE_KEY, currentSiteInfo.getTargetDataRevision(), SiteInfo.ActionScope.VDC);
            } else {
                siteInfo = new SiteInfo(vdcConfigVersion, SiteInfo.IPSEC_OP_ROTATE_KEY, SiteInfo.ActionScope.VDC);
            }
            coordinator.setTargetInfo(siteId, siteInfo);
            log.info("VDC target version updated to {} for site {}", siteInfo.getVdcConfigVersion(), siteId);
        }
    }
}
