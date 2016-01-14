/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.geo.vdccontroller.impl;

import java.security.KeyStore;
import java.beans.BeanInfo;
import java.beans.Introspector;
import java.beans.PropertyDescriptor;
import java.net.URI;
import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.List;

import com.emc.storageos.security.ipsec.IPsecConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.coordinator.common.Service;
import com.emc.storageos.db.client.URIUtil;
import com.emc.storageos.db.client.impl.ColumnField;
import com.emc.storageos.db.client.impl.DataObjectType;
import com.emc.storageos.db.client.impl.TypeMap;
import com.emc.storageos.db.client.model.Cf;
import com.emc.storageos.db.client.model.DataObject;
import com.emc.storageos.db.client.model.GeoVisibleResource;
import com.emc.storageos.db.client.model.VdcVersion;
import com.emc.storageos.db.client.model.VirtualDataCenter;
import com.emc.storageos.db.client.model.VirtualDataCenter.ConnectionStatus;
import com.emc.storageos.db.client.model.VirtualDataCenter.GeoReplicationStatus;
import com.emc.storageos.db.client.model.VirtualDataCenterInUse;
import com.emc.storageos.db.client.util.KeyspaceUtil;
import com.emc.storageos.db.common.PackageScanner;
import com.emc.storageos.security.geo.exceptions.GeoException;
import com.emc.storageos.geo.service.impl.util.VdcConfigHelper;
import com.emc.storageos.geomodel.VdcConfig;
import com.emc.storageos.geomodel.VdcConfigSyncParam;
import com.emc.storageos.security.geo.GeoClientCacheManager;
import com.emc.storageos.security.geo.GeoServiceHelper;
import com.emc.storageos.security.geo.GeoServiceJob;
import com.emc.storageos.security.geo.exceptions.FatalGeoException;

/**
 * Remove VDC from geo system
 */
public class RemoveVdcTaskOp extends AbstractVdcTaskOp {
    private final static Logger log = LoggerFactory.getLogger(ConnectVdcTaskOp.class);

    private boolean isOperatedVdcDisconnected = false;

    public RemoveVdcTaskOp(InternalDbClient dbClient, GeoClientCacheManager geoClientCache,
            VdcConfigHelper helper, Service serviceInfo, VirtualDataCenter vdc,
            String taskId, KeyStore keystore, IPsecConfig ipsecConfig) {
        super(dbClient, geoClientCache, helper, serviceInfo, vdc, taskId, null, keystore, ipsecConfig);

        if (operatedVdc.getConnectionStatus() == ConnectionStatus.DISCONNECTED) {
            isOperatedVdcDisconnected = true;
        }
    }

    private void preCheck() {
        log.info("Pre check for {} before removal", operatedVdc.getShortId());
        lockHelper.acquire(operatedVdc.getShortId());

        geoClientCache.clearCache();
        loadVdcInfo();
        log.info("Load vdc info is done");

        checkVdcInUse();
        checkVdcDependency(operatedVdc.getShortId());

        // make sure all other vdc are up and running
        log.info("Check vdc stable");
        URI unstable = checkAllVdcStable(false, !isOperatedVdcDisconnected);
        if (unstable != null) {
            log.error("The vdc {} is not stable.", unstable);
            VirtualDataCenter vdc = dbClient.queryObject(VirtualDataCenter.class, unstable);
            String vdcName = (vdc != null) ? vdc.getLabel() : "";
            throw GeoException.fatals.unstableVdcFailure(vdcName);
        }

        log.info("Pre check for {} passed", operatedVdc.getShortId());
    }

    /**
     * Update the vdc config to all sites for vdc removal
     */
    public void syncConfig() {
        log.info("Start sync config for removing vdc {}", operatedVdc.getShortId());

        removeVdcFromStrategyOption(false);

        // update config for the site to be removed
        updateConfigForRemovedVdc(isOperatedVdcDisconnected);

        // update config for sites that are still connected
        try {
            updateConfigForConnectedVdc();
        } catch (GeoException ex) {
            throw ex;
        } catch (Exception e) {
            log.error("Failed to sync vdc config to all sites e=", e);
            throw GeoException.fatals.removeVdcSyncConfigFail(e);
        }

        // update the current progress of connect vdc. the site would reboot later.
        updateOpStatus(ConnectionStatus.REMOVE_SYNCED);

        // do not release the global lock here; lock is released during post processing
    }

    public void postCheck() {
        failedVdcStatus = ConnectionStatus.REMOVE_FAILED;
        log.info("remove vdc post check for {}", operatedVdc.getShortId());
        try {
            if (!isOperatedVdcDisconnected) {
                dbClient.waitVdcRemoveDone(operatedVdc.getShortId());
            }
            dbClient.waitAllSitesDbStable();
            removeVdcVersion(operatedVdc);
        } catch (Exception e) {
            log.error("wait for all sites db stable failed");
            throw GeoException.fatals.removeVdcPostcheckFail(e);
        }
        // lock is released in error handling code if an exception is thrown before we get here
        lockHelper.release(operatedVdc.getShortId());
    }

    /**
     * Update new vdc config for all sites - exclude the site to be removed
     */
    private void updateConfigForConnectedVdc() {
        // build new vdc config without operatedVdc
        List<VirtualDataCenter> newVdcList = new ArrayList<>();
        for (VirtualDataCenter vdc : getAllVdc()) {
            if (!vdc.getId().equals(operatedVdc.getId())) {
                newVdcList.add(vdc); // ignore the one to be removed
            }
        }

        log.info("number of vdc {} after removal", newVdcList.size());

        VdcConfigSyncParam syncParam = buildConfigParam(newVdcList);
        for (VirtualDataCenter vdc : connectedVdc) {
            if (vdc.getId().equals(myVdc.getId()) || vdc.getId().equals(operatedVdc.getId())) {
                continue; // skip my current vdc and operated vdc
            }
            geoClientCache.getGeoClient(vdc.getShortId()).syncVdcConfig(syncParam, vdc.getLabel());
        }

        dbClient.stopClusterGossiping();

        // set connection status to isolated if there is only one vdc in current geo system
        if (newVdcList.size() == 1) {
            if (!syncParam.getVirtualDataCenters().isEmpty()) {
                VdcConfig vdcConfig = syncParam.getVirtualDataCenters().get(0);
                vdcConfig.setConnectionStatus(ConnectionStatus.ISOLATED.toString());
                vdcConfig.setVersion(new Date().getTime());
            } else {
                log.error("Unexpected Vdc list size in sync config param");
            }
        }

        // update my local site with new config
        helper.syncVdcConfig(syncParam.getVirtualDataCenters(), null, syncParam.getVdcConfigVersion(), syncParam.getIpsecKey());
    }

    /**
     * Update new vdc config for the site to be remove - only include itself
     */
    private void updateConfigForRemovedVdc(boolean ignoreException) {
        operatedVdc.setConnectionStatus(ConnectionStatus.ISOLATED);
        operatedVdc.setRepStatus(GeoReplicationStatus.REP_NONE);
        operatedVdc.setVersion(new Date().getTime());

        List<VirtualDataCenter> localVdcList = new ArrayList<>(1);
        localVdcList.add(operatedVdc);
        VdcConfigSyncParam syncParam = buildConfigParam(localVdcList);

        log.info("send {} to removed vdc {}", syncParam, operatedVdc.getShortId());
        try {
            geoClientCache.getGeoClient(operatedVdc.getShortId()).syncVdcConfig(syncParam, operatedVdc.getLabel());
        } catch (FatalGeoException e) {
            if (!ignoreException) {
                throw e;
            }
        }
    }

    @Override
    protected void process() {
        String errMsg;
        switch (operatedVdcStatus) {
            case REMOVE_FAILED:
                errMsg = String.format("Removing vdc operation failed already on %s, skip all other steps", myVdc.getShortId());
                log.error(errMsg);
                throw GeoException.fatals.removeVdcInvalidStatus(errMsg);
            case CONNECTED:
            case CONNECT_FAILED:
            case DISCONNECTED:
            case UPDATE_FAILED:
            case REMOVING:
                preCheck();
                // from this point on, any errors will not be retryable and requires manual
                // recovery
                GeoServiceHelper.backupOperationVdc(dbClient, GeoServiceJob.JobType.VDC_REMOVE_JOB, operatedVdc.getId(), null);
                updateOpStatus(VirtualDataCenter.ConnectionStatus.REMOVING);
                failedVdcStatus = ConnectionStatus.REMOVE_FAILED;
                syncConfig();
            case REMOVE_SYNCED:
                postCheck();
                break;
            default:
                errMsg = "Vdc to be removed in unexpected status, skip all other steps";
                log.error(errMsg);
                log.info("target vdc status: {}", operatedVdcStatus);
                throw GeoException.fatals.removeVdcInvalidStatus(errMsg);
        }
    }

    private void checkVdcInUse() {
        VirtualDataCenterInUse vdcInUse = dbClient.queryObject(VirtualDataCenterInUse.class, operatedVdc.getId());
        if (vdcInUse != null && vdcInUse.getInUse()) {
            log.error("Refuse removal for vdc {} is inuse ", operatedVdc.getShortId());
            throw GeoException.fatals.removeVdcPrecheckFail(operatedVdc.getLabel(), "The vdc is in use");
        }
    }

    private void checkVdcDependency(final String vdcShortId) {
        try {
            VdcDependencyChecker checker = new VdcDependencyChecker(vdcShortId);
            checker.setPackages("com.emc.storageos.db.client.model");
            checker.scan(Cf.class);
        } catch (IllegalStateException ex) {
            log.error("vdc dependency check for removal error", ex);
            throw GeoException.fatals.removeVdcPrecheckFail(operatedVdc.getLabel(), ex.toString());
        }

    }

    private void removeVdcVersion(VirtualDataCenter operatedVdc) {
        List<URI> vdcVersionIds = dbClient.queryByType(VdcVersion.class, true);
        List<VdcVersion> vdcVersions = dbClient.queryObject(VdcVersion.class,
                vdcVersionIds);
        for (VdcVersion vdcVersion : vdcVersions) {
            if (vdcVersion.getVdcId().equals(operatedVdc.getId())) {
                log.info("The VdcVersion record {} will be removed.", vdcVersion);
                dbClient.markForDeletion(vdcVersion);
                return;
            }
        }
    }

    /**
     * Check data objects in geodb and check if it has reference to given vdc
     */
    class VdcDependencyChecker extends PackageScanner {
        String vdcShortId;

        VdcDependencyChecker(String shortId) {
            vdcShortId = shortId;
        }

        @Override
        protected void processClass(Class clazz) {
            if (!DataObject.class.isAssignableFrom(clazz)) {
                return;
            }
            if (!KeyspaceUtil.isGlobal(clazz)) {
                return;
            }
            DataObjectType doType = TypeMap.getDoType(clazz);
            Iterator<ColumnField> it = doType.getColumnFields().iterator();
            while (it.hasNext()) {
                ColumnField field = it.next();
                if (field.getIndex() == null) {
                    continue;
                }
                if (field.getIndexRefType() == null) {
                    continue;
                }
                Class refType = field.getIndexRefType();
                log.info("Geo data object {}, ref type {}", clazz, refType);
                if (!GeoVisibleResource.class.isAssignableFrom(refType)) {
                    continue;
                }
                try {
                    checkVdcReferenceForClass(clazz, field);
                } catch (GeoException ex) {
                    throw ex;
                } catch (Exception ex) {
                    throw new IllegalStateException(ex);
                }
            }
        }

        private void checkVdcReferenceForClass(Class clazz, ColumnField field) throws Exception {
            List<URI> ids = new ArrayList<URI>();
            for (Object id : dbClient.queryByType(clazz, true)) {
                ids.add((URI) id);
            }

            Iterator objs = dbClient.queryIterativeObjectField(clazz, field.getName(), ids);
            while (objs.hasNext()) {
                DataObject obj = (DataObject) objs.next();
                log.info("Geo data object Id {} field {}", obj.getId(), field.getName());
                checkVdcReferenceForObjectField(obj, field);
            }
        }

        private void checkVdcReferenceForObjectField(DataObject obj, ColumnField field) throws Exception {
            BeanInfo bInfo = Introspector.getBeanInfo(obj.getClass());

            PropertyDescriptor[] pds = bInfo.getPropertyDescriptors();
            for (int i = 0; i < pds.length; i++) {
                PropertyDescriptor pd = pds[i];
                if (!pd.getName().equals(field.getName())) {
                    continue;
                }
                Object value = pd.getReadMethod().invoke(obj);
                if (value == null) {
                    continue;
                }
                if (value instanceof URI) {
                    URI refId = (URI) value;
                    String refVdcShortId = URIUtil.parseVdcIdFromURI(refId);
                    log.info("Vdc short id {} for uri {}", refVdcShortId, refId);
                    if (refVdcShortId.equalsIgnoreCase(vdcShortId)) {
                        String msg = String.format("Vdc dependency check fail for %s ref %s", obj.getId(), refId);
                        throw GeoException.fatals.removeVdcPrecheckFail(operatedVdc.getLabel(), msg);
                    }
                } else {
                    String msg = String.format("Unexpected reference field in global data object %s value %s", obj.getId(), value);
                    throw GeoException.fatals.removeVdcPrecheckFail(operatedVdc.getLabel(), msg);
                }
            }
        }
    }

    @Override
    public VdcConfig.ConfigChangeType changeType() {
        return VdcConfig.ConfigChangeType.REMOVE_VDC;
    }
}
