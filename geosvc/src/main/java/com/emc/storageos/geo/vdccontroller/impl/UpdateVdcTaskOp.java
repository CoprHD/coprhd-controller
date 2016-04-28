/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.geo.vdccontroller.impl;

import java.security.KeyStore;
import java.security.cert.Certificate;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.net.URI;
import java.util.Properties;

import com.emc.storageos.security.geo.GeoServiceJob;
import com.emc.storageos.security.ipsec.IPsecConfig;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.coordinator.common.Service;
import com.emc.storageos.db.client.model.VirtualDataCenter;
import com.emc.storageos.security.geo.exceptions.GeoException;
import com.emc.storageos.geo.service.impl.util.VdcConfigHelper;
import com.emc.storageos.geomodel.VdcConfigSyncParam;
import com.emc.storageos.geomodel.VdcConfig;
import com.emc.storageos.geomodel.VdcPreCheckResponse;
import com.emc.storageos.geomodel.*;
import com.emc.storageos.security.authentication.InternalApiSignatureKeyGenerator;
import com.emc.storageos.security.geo.GeoClientCacheManager;
import com.emc.storageos.security.geo.GeoServiceHelper;

import static com.emc.storageos.db.client.model.VirtualDataCenter.ConnectionStatus;

/*
 * Detail implementation of vdc update operation
 */
public class UpdateVdcTaskOp extends AbstractVdcTaskOp {

    private final static Logger log = LoggerFactory.getLogger(UpdateVdcTaskOp.class);
    private Properties updateInfo;

    List<Object> params = null;
    private InternalApiSignatureKeyGenerator apiSignatureKeyGenerator;

    public UpdateVdcTaskOp(InternalDbClient dbClient, GeoClientCacheManager geoClientCache,
            VdcConfigHelper helper, Service serviceInfo, VirtualDataCenter vdc,
            String taskId, List<Object> taskParams, InternalApiSignatureKeyGenerator generator, KeyStore keystore, IPsecConfig ipsecConfig) {
        super(dbClient, geoClientCache, helper, serviceInfo, vdc, taskId, null, keystore, ipsecConfig);
        params = taskParams;
        updateInfo = (Properties) taskParams.get(0);
        apiSignatureKeyGenerator = generator;
    }

    /**
     * Precheck if vdc update is permitted, then sync the vdc config to all sites to
     * update an existing vdc
     */
    public void checkAndSync() {
        lockHelper.acquire(operatedVdc.getShortId());

        geoClientCache.clearCache();
        loadVdcInfo();

        if (StringUtils.isNotEmpty(updateInfo.getProperty(GeoServiceJob.VDC_CERTIFICATE_CHAIN)) &&
                (operatedVdc.getId().compareTo(myVdc.getId()) != 0)) {
            String errMsg = "could not update key certchain from remote VDC.";
            log.error(errMsg);
            throw GeoException.fatals.updateVdcPrecheckFail(errMsg);
        }

        VdcPreCheckResponse operatedVdcInfo = preCheck();

        GeoServiceHelper.backupOperationVdc(dbClient, GeoServiceJob.JobType.VDC_UPDATE_JOB, operatedVdcInfo.getId(), params.toString());
        failedVdcStatus = ConnectionStatus.UPDATE_FAILED;
        updateOperatedVdc();
        operatedVdc.setConnectionStatus(VirtualDataCenter.ConnectionStatus.UPDATING);
        dbClient.updateAndReindexObject(operatedVdc);
        loadVdcInfo();

        VdcConfigSyncParam mergedVdcInfo = mergeConfig(operatedVdcInfo);
        if (mergedVdcInfo == null) {
            log.error("merge the vdc config of all sites failed");
            throw GeoException.fatals.mergeConfigFail();
        }

        try {
            syncConfig(mergedVdcInfo);
        } catch (GeoException ex) {
            throw ex;
        } catch (Exception e) {
            log.error("Failed to sync vdc config to all sites : {}", e);
            throw GeoException.fatals.syncConfigFail(e);
        }

        String cert = updateInfo.getProperty(GeoServiceJob.VDC_CERTIFICATE_CHAIN);
        if (StringUtils.isNotEmpty(cert)) {
            VdcCertListParam certListParam = genCertOperationParam(VdcCertListParam.CMD_UPDATE_CERT);
            syncCerts(VdcCertListParam.CMD_UPDATE_CERT, certListParam);

            // set key and cert in local keystore
            Boolean selfsigned = (Boolean) params.get(1);
            byte[] key = (byte[]) params.get(2);
            Certificate[] certchain = (Certificate[]) params.get(3);
            helper.setKeyCertchain(selfsigned, key, certchain);
        }

        // lock is released in error handling code if an exception is thrown before we get
        // here. note that since there is no post processing for update, there is no way
        // to know if the sync operation is complete; lock must be released here before
        // sync is done.
        lockHelper.release(operatedVdc.getShortId());
    }

    private VdcPreCheckResponse preCheck() {
        log.info("Starting precheck on vdc update ...");

        // Step 0: Get remote vdc version before send preCheck, since we modify the preCheckParam
        // avoid to send preCheck from v2.3 or higher to v2.2 v2.1, v2.0
        if (!isRemoteVdcVersionCompatible(vdcInfo)) {
            throw GeoException.fatals.updateVdcPrecheckFail("Software version from remote vdc is lower than v2.3.");
        }

        // BZ:
        // TODO It appears that this code assumes that update node is a remote node.
        // we need to modify it to make it simpler when updated node is local.
        log.info("Send vdc precheck to remote vdc");
        VdcPreCheckResponse vdcResp =
                sendVdcPrecheckRequest(vdcInfo, false);

        log.info("Check vdc stable");
        // check if the cluster is stable
        URI unstable = checkAllVdcStable(false, true);
        if (unstable != null) {
            VirtualDataCenter vdc = dbClient.queryObject(VirtualDataCenter.class, unstable);
            String vdcName = (vdc != null) ? vdc.getLabel() : "";
            throw GeoException.fatals.unstableVdcFailure(vdcName);
        }

        log.info("vdc config retrieved: {}, {} {}",
                new Object[] { vdcResp.getApiEndpoint(), vdcResp.getHostIPv4AddressesMap(), vdcResp.getHostIPv6AddressesMap() });
        return vdcResp;
    }

    private VdcConfigSyncParam mergeConfig(VdcPreCheckResponse operatedVdcInfo) {
        // step 2: merge the vdc config info of all sites, as the initiator
        // we should have all current vdc config info
        VdcConfigSyncParam vdcConfigList = new VdcConfigSyncParam();
        List<VdcConfig> list = vdcConfigList.getVirtualDataCenters();

        for (VirtualDataCenter vdc : getAllVdc()) {
            log.info("add {} to the merged vdc config", vdc.getShortId());
            VdcConfig vdcConfig = helper.toConfigParam(vdc);
            list.add(vdcConfig);
        }
        mergeVdcInfo(list, operatedVdc);
        return vdcConfigList;
    }

    private void syncConfig(VdcConfigSyncParam mergedVdcInfo) {
        // step 3: sync merged vdc config info to all sites in which property change triggered
        // all conf files updated after reboot.
        // the vdc to be connected will reset the db and update the network strategy when
        // startup.

        // loop all current connected VDCs with latest vdc config info, shall be moved into geoclient
        // geoclient shall responsible to retry all retryable errors, we have no need retry here
        log.info("sync vdc config to all sites, total vdc entries {}", mergedVdcInfo.getVirtualDataCenters().size());
        List<VirtualDataCenter> vdcList = getToBeSyncedVdc();
        for (VirtualDataCenter vdc : vdcList) {
            log.info("Loop vdc {}:{} to sync the latest vdc cert info", vdc.getShortId(), vdc.getApiEndpoint());
            if (vdc.getApiEndpoint() != null) {
                mergedVdcInfo.setAssignedVdcId(null);
                mergedVdcInfo.setConfigChangeType(changeType().toString());
                geoClientCache.getGeoClient(vdc.getShortId()).syncVdcConfig(mergedVdcInfo, vdc.getLabel());
                log.info("Sync vdc info succeed");
            } else {
                log.error("Fatal error: try to sync with a vdc without endpoint");
            }
        }
        // notify local vdc to apply the new vdc config info
        helper.syncVdcConfig(mergedVdcInfo.getVirtualDataCenters(), null,
                mergedVdcInfo.getVdcConfigVersion(), mergedVdcInfo.getIpsecKey());

    }

    private void mergeVdcInfo(List<VdcConfig> list, VirtualDataCenter vdc) {
        log.info("add to be updated vdc {} to the merged vdc config", vdc.getShortId());
        Iterator<VdcConfig> it = list.iterator();
        while (it.hasNext()) {
            VdcConfig vdcSyncParam = it.next();
            if (vdcSyncParam.getId().compareTo(vdc.getId()) == 0) {
                // update vdc

                // if this is a local,isolated vdc, update local vdc only, no other sites
                boolean isolated = isLocalIsolatedVdc();
                log.info("Checking if this is a local isolated vdc->{}", isolated);
                if (isolated) {
                    vdcSyncParam.setConnectionStatus(ConnectionStatus.ISOLATED.toString());
                } else {
                    vdcSyncParam.setConnectionStatus(ConnectionStatus.CONNECTED.toString());
                }

                Date updateDate = new Date();
                vdcSyncParam.setVersion(updateDate.getTime());
                if ((vdc.getLabel() != null) && (!vdc.getLabel().isEmpty())) {
                    vdcSyncParam.setName(vdc.getLabel());
                }
                if ((vdc.getDescription() != null) && (!vdc.getDescription().isEmpty())) {
                    vdcSyncParam.setDescription(vdc.getDescription());
                }
                if ((vdc.getGeoCommandEndpoint() != null) && (!vdc.getGeoCommandEndpoint().isEmpty())) {
                    vdcSyncParam.setGeoCommandEndpoint(vdc.getGeoCommandEndpoint());
                }
                if ((vdc.getGeoDataEndpoint() != null) && (!vdc.getGeoDataEndpoint().isEmpty())) {
                    vdcSyncParam.setGeoDataEndpoint(vdc.getGeoDataEndpoint());
                }
                // TODO: set apiendpoint and seckey
                return;
            }
        }

        return;
    }

    @Override
    protected void process() {
        String errMsg;
        switch (operatedVdcStatus) {
            case UPDATE_FAILED:
            case ISOLATED:
            case CONNECTED:
            case UPDATING:
                checkAndSync();
                break;
            default:
                errMsg = "Vdc to be updated in unexpected status, skip all other steps";
                log.error(errMsg);
                log.info("target vdc status: {}", operatedVdcStatus);
                throw GeoException.fatals.updateVdcInvalidStatus(errMsg);
        }
    }

    /**
     * Verify if this is a local and isolated vdc
     * 
     * @return true if status is isolated
     */
    private boolean isLocalIsolatedVdc() {
        log.info("Checking if local vdc and operated vdc ids are the same ->{}.", operatedVdc.getId().equals(myVdc.getId()));
        return (operatedVdc.getId().equals(myVdc.getId()) && ((myVdc.getConnectionStatus() == VirtualDataCenter.ConnectionStatus.ISOLATED) || (myVdc
                .getRepStatus() == VirtualDataCenter.GeoReplicationStatus.REP_NONE)));
    }

    private void updateOperatedVdc() {

        String name = updateInfo.getProperty(GeoServiceJob.VDC_NAME);
        if (StringUtils.isNotEmpty(name)) {
            operatedVdc.setLabel(name);
        }
        String description = updateInfo.getProperty(GeoServiceJob.VDC_DESCRIPTION);
        if (StringUtils.isNotEmpty(description)) {
            operatedVdc.setDescription(description);
        }
        String geocommand = updateInfo.getProperty(GeoServiceJob.VDC_GEOCOMMAND_ENDPOINT);
        if (StringUtils.isNotEmpty(geocommand)) {
            operatedVdc.setGeoCommandEndpoint(geocommand);
        }
        String geodata = updateInfo.getProperty(GeoServiceJob.VDC_GEODATA_ENDPOINT);
        if (StringUtils.isNotEmpty(geodata)) {
            operatedVdc.setGeoDataEndpoint(geodata);
        }
        String certchain = updateInfo.getProperty(GeoServiceJob.VDC_CERTIFICATE_CHAIN);
        if (StringUtils.isNotEmpty(certchain)) {
            operatedVdc.setCertificateChain(certchain);
        }
    }

    @Override
    public VdcConfig.ConfigChangeType changeType() {
        return VdcConfig.ConfigChangeType.UPDATE_VDC;
    }

}
