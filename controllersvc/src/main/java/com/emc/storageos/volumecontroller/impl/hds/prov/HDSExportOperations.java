/*
 * Copyright (c) 2013 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.hds.prov;

import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import com.emc.storageos.customconfigcontroller.CustomConfigConstants;
import com.emc.storageos.customconfigcontroller.DataSource;
import com.emc.storageos.customconfigcontroller.DataSourceFactory;
import com.emc.storageos.customconfigcontroller.impl.CustomConfigHandler;
import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.URIUtil;
import com.emc.storageos.db.client.constraint.AlternateIdConstraint;
import com.emc.storageos.db.client.constraint.ContainmentConstraint;
import com.emc.storageos.db.client.constraint.URIQueryResultList;
import com.emc.storageos.db.client.model.AutoTieringPolicy.HitachiTieringPolicy;
import com.emc.storageos.db.client.model.BlockObject;
import com.emc.storageos.db.client.model.ExportMask;
import com.emc.storageos.db.client.model.Host;
import com.emc.storageos.db.client.model.HostInterface;
import com.emc.storageos.db.client.model.Initiator;
import com.emc.storageos.db.client.model.StoragePort;
import com.emc.storageos.db.client.model.StorageProtocol.Transport;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.model.StringSet;
import com.emc.storageos.db.client.model.StringSetMap;
import com.emc.storageos.db.client.model.VirtualPool;
import com.emc.storageos.db.client.model.Volume;
import com.emc.storageos.db.client.util.CommonTransformerFunctions;
import com.emc.storageos.db.client.util.WWNUtility;
import com.emc.storageos.exceptions.DeviceControllerErrors;
import com.emc.storageos.exceptions.DeviceControllerException;
import com.emc.storageos.hds.HDSConstants;
import com.emc.storageos.hds.HDSException;
import com.emc.storageos.hds.api.HDSApiClient;
import com.emc.storageos.hds.api.HDSApiExportManager;
import com.emc.storageos.hds.api.HDSApiFactory;
import com.emc.storageos.hds.model.FreeLun;
import com.emc.storageos.hds.model.HDSHost;
import com.emc.storageos.hds.model.HostStorageDomain;
import com.emc.storageos.hds.model.ISCSIName;
import com.emc.storageos.hds.model.LDEV;
import com.emc.storageos.hds.model.LogicalUnit;
import com.emc.storageos.hds.model.Path;
import com.emc.storageos.hds.model.WorldWideName;
import com.emc.storageos.model.ResourceOperationTypeEnum;
import com.emc.storageos.networkcontroller.impl.NetworkDeviceController;
import com.emc.storageos.svcs.errorhandling.model.ServiceError;
import com.emc.storageos.util.NetworkLite;
import com.emc.storageos.util.NetworkUtil;
import com.emc.storageos.volumecontroller.TaskCompleter;
import com.emc.storageos.volumecontroller.impl.ControllerServiceImpl;
import com.emc.storageos.volumecontroller.impl.ControllerUtils;
import com.emc.storageos.volumecontroller.impl.VolumeURIHLU;
import com.emc.storageos.volumecontroller.impl.hds.prov.job.HDSJob;
import com.emc.storageos.volumecontroller.impl.hds.prov.job.HDSModifyVolumeJob;
import com.emc.storageos.volumecontroller.impl.hds.prov.utils.HDSUtils;
import com.emc.storageos.volumecontroller.impl.job.QueueJob;
import com.emc.storageos.volumecontroller.impl.smis.ExportMaskOperations;
import com.emc.storageos.volumecontroller.impl.utils.ExportMaskUtils;
import com.google.common.base.Joiner;
import com.google.common.base.Splitter;
import com.google.common.collect.Collections2;
import com.google.common.collect.Iterables;
import com.google.common.collect.Sets;
import com.google.gson.internal.Pair;

/**
 * This class is responsible to handle all export usecases like
 * 1. Creating an ExportMask
 * 2. Deleting an ExportMask
 * 3. Add volume to an ExportMask
 * 4. Delete Volume from an ExportMask
 * 5. Add Initiators to ExportMask
 * 6. Delete Initiators from ExportMask
 * 7. Refresh ExportMask
 * 
 */
public class HDSExportOperations implements ExportMaskOperations {

    private static Logger log = LoggerFactory.getLogger(HDSExportOperations.class);
    private DbClient dbClient;
    private HDSApiFactory hdsApiFactory;

    private static final List<String> hostModeSupportedModels = new ArrayList<String>();

    @Autowired
    private DataSourceFactory dataSourceFactory;
    @Autowired
    private CustomConfigHandler customConfigHandler;
    @Autowired
    private NetworkDeviceController _networkDeviceController;

    static {
        // When a new model supports host modes then we should add them to this list.
        hostModeSupportedModels.add(HDSConstants.HUSVM_ARRAYFAMILY_MODEL);
        hostModeSupportedModels.add(HDSConstants.VSP_ARRAYFAMILY_MODEL);
        hostModeSupportedModels.add(HDSConstants.USPV_ARRAYFAMILY_MODEL);
        hostModeSupportedModels.add(HDSConstants.USP_ARRAYFAMILY_MODEL);
        hostModeSupportedModels.add(HDSConstants.VSP_G1000_ARRAYFAMILY_MODEL);
    }

    public static enum HitachiHostMode {
        Windows("Windows Extension"), Linux("Standard"), Solaris("Solaris"), HPUX("HP"), Esx("VMware Extension"), AIX("AIX"), Netware(
                "Netware");

        private String _key;
        private static HitachiHostMode[] copyOfValues = values();

        HitachiHostMode(String key) {
            _key = key;
        }

        public String getKey() {
            return _key;
        }

        public static String getHostMode(String id) {
            for (HitachiHostMode type : copyOfValues) {
                if (type.name().equalsIgnoreCase(id)) {
                    return type.getKey();
                }
            }
            return null;
        }

        public static String getHostModeName(String key) {
            for (HitachiHostMode policyType : copyOfValues) {
                if (policyType.getKey().equalsIgnoreCase(key)) {
                    return policyType.name();
                }
            }
            return null;
        }
    }

    public void setDbClient(DbClient dbClient) {
        this.dbClient = dbClient;
    }

    /**
     * @param hdsApiFactory the hdsApiFactory to set
     */
    public void setHdsApiFactory(HDSApiFactory hdsApiFactory) {
        this.hdsApiFactory = hdsApiFactory;
    }

    /**
     * Creates a ExportMask with the given initiators & volumes.
     * 
     * Below are the steps to follow to create an Export Mask on Hitachi array.
     * Step 1: Register host with initiators.
     * Step 2: Based on the targetport type, create a Host Storage Domain.
     * Step 3: Add WWN/ISCSI names to the Host Storage Domain.
     * Step 4: Add Luns to the HostStorageDomain created in step 2.
     * 
     */
    @Override
    public void createExportMask(StorageSystem storage, URI exportMaskId,
            VolumeURIHLU[] volumeURIHLUs, List<URI> targetURIList,
            List<Initiator> initiatorList, TaskCompleter taskCompleter)
            throws DeviceControllerException {
        log.info("Export mask id :{}", exportMaskId);
        log.info("{} createExportMask START...", storage.getSerialNumber());
        log.info("createExportMask: volume-HLU pairs:  {}", volumeURIHLUs);
        log.info("createExportMask: assignments:    {}", targetURIList);
        log.info("createExportMask: initiators: {}", initiatorList);
        HDSApiClient hdsApiClient = null;
        String systemObjectID = null;
        ExportMask exportMask = null;
        List<HostStorageDomain> hsdsWithInitiators = null;
        List<HostStorageDomain> hsdsToCreate = null;
        try {
            hdsApiClient = hdsApiFactory.getClient(getHDSServerManagementServerInfo(storage),
                    storage.getSmisUserName(), storage.getSmisPassword());
            systemObjectID = HDSUtils.getSystemObjectID(storage);
            exportMask = dbClient.queryObject(ExportMask.class, exportMaskId);
            // Check whether host is already registered or not. If host is not
            // registered, add the host.
            registerHostsWithInitiators(initiatorList, hdsApiClient);

            List<StoragePort> storagePorts = dbClient.queryObject(StoragePort.class, targetURIList, true);
            if (checkIfMixedTargetPortTypeSelected(storagePorts)) {
                log.error("Unsupported Host as it has both FC & iSCSI Initiators");
                throw HDSException.exceptions.unsupportedConfigurationFoundInHost();
            }

            if (null != targetURIList && !targetURIList.isEmpty()) {
                String hostName = getHostNameForInitiators(initiatorList);
                String hostMode = null, hostModeOption = null;
                Pair<String, String> hostModeInfo = getHostModeInfo(storage, initiatorList);
                if (hostModeInfo != null) {
                    hostMode = hostModeInfo.first;
                    hostModeOption = hostModeInfo.second;
                }

                hsdsToCreate = processTargetPortsToFormHSDs(hdsApiClient, storage,
                        targetURIList, hostName, exportMask, hostModeInfo, systemObjectID);
                // Step 1: Create all HSD's using batch operation.
                List<HostStorageDomain> hsdResponseList = hdsApiClient
                        .getHDSBatchApiExportManager().addHostStorageDomains(
                                systemObjectID, hsdsToCreate, storage.getModel());

                if (null == hsdResponseList || hsdResponseList.isEmpty()) {
                    log.error("Batch HSD creation failed. Aborting operation...");
                    throw HDSException.exceptions.notAbleToAddHSD(storage
                            .getSerialNumber());
                }
                // Step 2: Add initiators to all HSD's.
                hsdsWithInitiators = executeBatchHSDAddInitiatorsCommand(hdsApiClient,
                        systemObjectID, hsdResponseList, storagePorts, initiatorList, storage.getModel());

                // Step 3: Add volumes to all HSD's.
                List<Path> allHSDPaths = executeBatchHSDAddVolumesCommand(hdsApiClient,
                        systemObjectID, hsdsWithInitiators, volumeURIHLUs, storage.getModel());

                if (null != allHSDPaths && !allHSDPaths.isEmpty()) {
                    updateExportMaskDetailInDB(hsdsWithInitiators, allHSDPaths,
                            exportMask, storage, volumeURIHLUs);
                }
            }
            taskCompleter.ready(dbClient);
        } catch (Exception ex) {
            // HSD creation failed before updating exportmask.
            // we should rollback them.
            // roll back HSDs when there is a failure during adding of
            // initiators/volumes.
            try {
                log.info("Exception occurred while processing exportmask due to: {}", ex.getMessage());
                if (null != hsdsWithInitiators && !hsdsWithInitiators.isEmpty()) {
                    hdsApiClient.getHDSBatchApiExportManager()
                            .deleteBatchHostStorageDomains(systemObjectID,
                                    hsdsWithInitiators, storage.getModel());
                } else {
                    if (null != hsdsToCreate && !hsdsToCreate.isEmpty()) {
                        List<HostStorageDomain> allHSDs = hdsApiClient
                                .getHDSApiExportManager()
                                .getHostStorageDomains(systemObjectID);
                        List<HostStorageDomain> partialHSDListToRemove = getPartialHSDListToDelete(
                                allHSDs, hsdsToCreate);
                        hdsApiClient.getHDSBatchApiExportManager()
                                .deleteBatchHostStorageDomains(systemObjectID,
                                        partialHSDListToRemove, storage.getModel());
                    }
                }
                log.error(String.format("createExportMask failed - maskName: %s",
                        exportMaskId.toString()), ex);
            } catch (Exception ex1) {
                log.error(
                        "Exception occurred while deleting unsuccessful HSDs on system: {}",
                        systemObjectID, ex1.getMessage());
            } finally {
                ServiceError serviceError = DeviceControllerException.errors
                        .jobFailed(ex);
                taskCompleter.error(dbClient, serviceError);
            }
        }
        log.info("{} createExportMask END...", storage.getSerialNumber());
    }

    /**
     * Updates ExportMask details like volumes, HSD's & target port details in DB.
     * 
     * @param hsdsWithInitiators : HSD's create successfully.
     * @param allHSDPaths : Volume LunPaths added successfully.
     * @param exportMask : ExportMask db object.
     * @param storage : StorageSystem db object.
     * @param volumeURIHLUs : volume-lun details.
     */
    private void updateExportMaskDetailInDB(List<HostStorageDomain> hsdsWithInitiators,
            List<Path> allHSDPaths, ExportMask exportMask, StorageSystem storage,
            VolumeURIHLU[] volumeURIHLUs) {
        StringSetMap deviceDataMap = new StringSetMap();
        for (HostStorageDomain hsd : hsdsWithInitiators) {
            StringSet targetPortSet = new StringSet();
            List<String> hsdPorts = Arrays.asList(hsd.getPortID());
            List<String> targetPortURIs = getStoragePortURIs(hsdPorts, storage);
            targetPortSet.addAll(targetPortURIs);
            deviceDataMap.put(hsd.getObjectID(), targetPortSet);
        }
        exportMask.addDeviceDataMap(deviceDataMap);
        updateVolumeHLUInfo(volumeURIHLUs, allHSDPaths, exportMask);
        dbClient.updateAndReindexObject(exportMask);
        log.info("ExportMask: {} details updated successfully.", exportMask.getId());
    }

    /**
     * Returns a list of HostStorageDomain objects created partially from a
     * batch operation.
     * 
     * @param allHSDs
     * @param hsdsToCreate
     * @return
     */
    private List<HostStorageDomain> getPartialHSDListToDelete(
            List<HostStorageDomain> allHSDs, List<HostStorageDomain> hsdsToCreate) {
        Collection<String> hsdNickNamesList = Collections2.transform(hsdsToCreate,
                HDSUtils.fctnHSDToNickName());
        List<HostStorageDomain> partialHSDsCreated = new ArrayList<HostStorageDomain>();
        if (null != allHSDs && !allHSDs.isEmpty()) {
            for (HostStorageDomain hsd : allHSDs) {
                if (hsdNickNamesList.contains(hsd.getNickname())) {
                    partialHSDsCreated.add(hsd);
                }
            }
        }
        return partialHSDsCreated;
    }

    /**
     * This routine will take care of following items.
     * 1. Prepares a batch of Path objects with volumes & HSD's to add.
     * 2. Executes the batch operation.
     * 
     * @param hdsApiClient
     * @param systemId
     * @param hsdsWithInitiators
     * @param volumeURIHLUs
     * @param model
     * @return
     * @throws Exception
     */
    private List<Path> executeBatchHSDAddVolumesCommand(HDSApiClient hdsApiClient,
            String systemId, List<HostStorageDomain> hsdsWithInitiators,
            VolumeURIHLU[] volumeURIHLUs, String model) throws Exception {
        if (null == hsdsWithInitiators || hsdsWithInitiators.isEmpty()) {
            log.error("Batch HSD creation failed. Aborting operation...");
            throw HDSException.exceptions.notAbleToAddHSD(systemId);
        }
        List<Path> pathList = new ArrayList<Path>();
        for (HostStorageDomain hsd : hsdsWithInitiators) {
            Map<String, String> volumeLunMap = getVolumeLunMap(systemId,
                    hsd.getObjectID(), volumeURIHLUs,
                    hdsApiClient.getHDSApiExportManager());
            for (Map.Entry<String, String> entry : volumeLunMap.entrySet()) {
                Path path = new Path(hsd.getPortID(), hsd.getDomainID(), null,
                        entry.getValue(), entry.getKey());
                pathList.add(path);
            }
        }
        return hdsApiClient.getHDSBatchApiExportManager().addLUNPathsToHSDs(systemId,
                pathList, model);
    }

    /**
     * This routine will take care of following items.
     * 1. Prepares a batch of HostStorageDomain objects with initiators to add.
     * 2. Executes the batch operation.
     * 
     * @param hdsApiClient
     * @param systemObjectID
     * @param createHsdsResponseList
     * @param storagePorts
     * @param initiators
     * @return
     * @throws Exception
     */
    private List<HostStorageDomain> executeBatchHSDAddInitiatorsCommand(HDSApiClient hdsApiClient, String systemObjectID,
            List<HostStorageDomain> createHsdsResponseList, List<StoragePort> storagePorts, List<Initiator> initiators, String model)
            throws Exception {

        List<HostStorageDomain> fcHsdsToAddInitiators = new ArrayList<HostStorageDomain>();
        List<HostStorageDomain> iSCSIHsdsToAddInitiators = new ArrayList<HostStorageDomain>();
        List<HostStorageDomain> hsdsWithAddIniResponseList = new ArrayList<HostStorageDomain>();
        // Considers the IVR Networks as well.
        Map<URI, Set<String>> networkInitiatorsMap = NetworkUtil.getNetworkToInitiators(dbClient, initiators);
        Map<HostStorageDomain, URI> networkToHsdObjectIdMap = getHostGroupNetworkIdMap(storagePorts, createHsdsResponseList, dbClient);
        log.info("networkInitiatorsMap: {}", networkInitiatorsMap);
        log.info("networkToHsdObjectIdMap :{}", networkToHsdObjectIdMap);

        // Step 2: Add initiators to all HSD's using batch operation
        for (Entry<HostStorageDomain, URI> hsdNetworkEntry : networkToHsdObjectIdMap.entrySet()) {
            HostStorageDomain hsd = hsdNetworkEntry.getKey();
            log.info("Processing hsd: {}", hsd.getObjectID());
            HostStorageDomain hsdToAddInitiators = new HostStorageDomain(hsdNetworkEntry.getKey());
            Set<String> initiatorsOnSameNetwork = networkInitiatorsMap.get(hsdNetworkEntry.getValue());
            // Get the initiators part of the storagePort's Network
            List<String> formattedInitiators = getFormattedInitiators(initiatorsOnSameNetwork);
            if (hsd.getDomainType().equalsIgnoreCase(HDSConstants.HOST_GROUP_DOMAIN_TYPE)) {
                List<WorldWideName> wwnList = new ArrayList(Collections2.transform(
                        formattedInitiators, HDSUtils.fctnPortWWNToWorldWideName()));
                hsdToAddInitiators.setWwnList(wwnList);
                fcHsdsToAddInitiators.add(hsdToAddInitiators);
            }
            if (hsd.getDomainType().equalsIgnoreCase(
                    HDSConstants.ISCSI_TARGET_DOMAIN_TYPE)) {
                List<ISCSIName> iscsiNameList = new ArrayList(Collections2.transform(
                        formattedInitiators, HDSUtils.fctnPortNameToISCSIName()));
                hsdToAddInitiators.setIscsiList(iscsiNameList);
                iSCSIHsdsToAddInitiators.add(hsdToAddInitiators);
            }
        }
        if (!fcHsdsToAddInitiators.isEmpty()) {
            hsdsWithAddIniResponseList.addAll(hdsApiClient.getHDSBatchApiExportManager()
                    .addWWNsToHostStorageDomain(systemObjectID, fcHsdsToAddInitiators, model));
        }

        if (!iSCSIHsdsToAddInitiators.isEmpty()) {
            hsdsWithAddIniResponseList.addAll(hdsApiClient.getHDSBatchApiExportManager()
                    .addISCSINamesToHostStorageDomain(systemObjectID,
                            iSCSIHsdsToAddInitiators, model));
        }

        if (null == hsdsWithAddIniResponseList || hsdsWithAddIniResponseList.isEmpty()) {
            log.error("Batch add initiators to HSD creation failed. Aborting operation...");
            throw HDSException.exceptions
                    .notAbleToAddInitiatorsToHostStorageDomain(systemObjectID);
        }

        return hsdsWithAddIniResponseList;
    }

    /**
     * Constructs a map of [Host Group => Network Id] for the given storage ports.
     * 
     * @param sports
     * @param hsds
     * @param dbClient
     * @return
     */
    private static Map<HostStorageDomain, URI> getHostGroupNetworkIdMap(
            List<StoragePort> sports, List<HostStorageDomain> hsds, DbClient dbClient) {
        Map<HostStorageDomain, URI> networkToHSDMap = new HashMap<HostStorageDomain, URI>();
        for (StoragePort sport : sports) {
            NetworkLite network = NetworkUtil.getEndpointNetworkLite(sport.getPortNetworkId(), dbClient);
            if (null != network) {
                if (network != null && network.getInactive() == false
                        && network.getTransportType().equals(sport.getTransportType())) {
                    HostStorageDomain hsd = findStoragePortOfHSD(hsds, sport);
                    if (null != hsd) {
                        log.info("Found a matching network {} for HSD {}", network.getLabel(), sport.getNativeGuid());
                        networkToHSDMap.put(hsd, network.getId());
                    } else {
                        log.error("Couldn't find the HSD configured for port: {}", sport.getNativeGuid());
                    }
                }
            }
        }
        return networkToHSDMap;
    }

    private List<String> getFormattedInitiators(Set<String> initiators) {
        List<String> formattedInitiators = new ArrayList<String>();
        if (null != initiators && !initiators.isEmpty()) {
            for (String initiator : initiators) {
                if (WWNUtility.isValidWWN(initiator)) {
                    formattedInitiators.add(initiator.replace(HDSConstants.COLON, HDSConstants.DOT_OPERATOR));
                } else {
                    formattedInitiators.add(initiator);
                }
            }
        }
        return formattedInitiators;
    }

    /**
     * Find the storagePort in which the HSD is created.
     * 
     * @param hsd
     * @param storagePorts
     * @return
     */
    private static HostStorageDomain findStoragePortOfHSD(List<HostStorageDomain> hsds, StoragePort storagePort) {
        for (HostStorageDomain hsd : hsds) {
            if (HDSUtils.getPortID(storagePort).equalsIgnoreCase(hsd.getPortID())) {
                log.info("Found matching port for the HSD: {} {}", storagePort.getNativeGuid(), hsd.getObjectID());
                return hsd;
            }
        }
        return null;
    }

    /**
     * This routine iterates through the target ports and prepares a batch of HostStorageDomain
     * Objects with required information.
     * 
     * @param storage
     * @param targetURIList
     * @param hostName
     * @param exportMask
     * @param hostModeInfo
     * @return
     */
    private List<HostStorageDomain> processTargetPortsToFormHSDs(
            HDSApiClient hdsApiClient, StorageSystem storage, List<URI> targetURIList,
            String hostName, ExportMask exportMask, Pair<String, String> hostModeInfo,
            String systemObjectID) throws Exception {
        List<HostStorageDomain> hsdList = new ArrayList<HostStorageDomain>();
        String hostMode = null, hostModeOption = null;
        if (hostModeInfo != null) {
            hostMode = hostModeInfo.first;
            hostModeOption = hostModeInfo.second;
        }

        for (URI targetPortURI : targetURIList) {
            StoragePort storagePort = dbClient.queryObject(StoragePort.class,
                    targetPortURI);
            String storagePortNumber = getStoragePortNumber(storagePort.getNativeGuid());
            DataSource dataSource = dataSourceFactory.createHSDNickNameDataSource(
                    hostName, storagePortNumber, storage);
            // Hitachi allows only 32 chars as nickname, we should trim the
            // length 32 chars.
            String hsdNickName = customConfigHandler.getComputedCustomConfigValue(
                    CustomConfigConstants.HDS_HOST_STORAGE_DOMAIN_NICKNAME_MASK_NAME,
                    storage.getSystemType(), dataSource);
            // If there are any iSCSI initiators then create iSCSI
            // HostStorageDomain.
            if (Transport.IP.name().equalsIgnoreCase(storagePort.getTransportType())) {
                log.info("Populating iSCSI HSD for storage: {}",
                        storage.getSerialNumber());
                HostStorageDomain hostGroup = new HostStorageDomain(storagePortNumber,
                        exportMask.getMaskName(), HDSConstants.ISCSI_TARGET_DOMAIN_TYPE,
                        hsdNickName);
                hostGroup.setHostMode(hostMode);
                hostGroup.setHostModeOption(hostModeOption);
                hsdList.add(hostGroup);
            }
            // If there are FC initiators then create a FC HostStorageDomain.
            if (Transport.FC.name().equalsIgnoreCase(storagePort.getTransportType())) {
                log.info("Populating FC HSD for storage: {}", storage.getSerialNumber());
                HostStorageDomain hostGroup = new HostStorageDomain(storagePortNumber,
                        exportMask.getMaskName(), HDSConstants.HOST_GROUP_DOMAIN_TYPE,
                        hsdNickName);
                hostGroup.setHostMode(hostMode);
                hostGroup.setHostModeOption(hostModeOption);
                hsdList.add(hostGroup);
            }
        }
        return hsdList;
    }

    /**
     * 
     * @param portURIList
     * @return
     */
    private boolean checkIfMixedTargetPortTypeSelected(List<StoragePort> ports) {
        boolean isFC = false;
        boolean isIP = false;
        for (StoragePort port : ports) {
            if (port.getPortType().equalsIgnoreCase(Transport.FC.name())) {
                isFC = true;
            } else if (port.getPortType().equalsIgnoreCase(Transport.IP.name())) {
                isIP = true;
            }
            if (isFC && isIP) {
                return true;
            }
        }
        return false;
    }

    /**
     * return the hostName in which the initiator belongs to.
     * 
     * @param initiatorList
     * @return
     */
    private String getHostNameForInitiators(List<Initiator> initiatorList) {
        Initiator initiator = initiatorList.get(0);
        return initiator.getHostName();
    }

    /**
     * Return the host mode & host mode option pair based on the host operating system.
     * Only for HUS VM, VSP, USPV, USP, VSP G1000, different host modes
     * are supported. Refer API reference guide for details.
     * 
     * @param initiatorList
     * @return
     */
    private Pair<String, String> getHostModeInfo(StorageSystem storage, List<Initiator> initiatorList) {
        // For AMS, WMS, HUS series, only Standard mode is applicable.
        // Return null will default to Standard mode.
        if (hostModeSupportedModels.contains(storage.getModel())) {
            String hostMode = null;
            String hostModeOption = null;
            Initiator initiator = initiatorList.get(0);
            // We don't define Host for vplex initiators connected to backend storage.
            // Hence we should check for not null.
            if (null != initiator.getHost()) {
                Host host = dbClient.queryObject(Host.class, initiator.getHost());
                hostMode = HitachiHostMode.getHostMode(host.getType());
                if (isHostModeOptionSupported(host.getType())) {
                    hostModeOption = customConfigHandler.getComputedCustomConfigValue(
                            CustomConfigConstants.HDS_HOST_STORAGE_HOST_MODE_OPTION, host.getType(), null);
                }
            }
            return new Pair<String, String>(hostMode, hostModeOption);
        }
        return null;
    }

    /**
     * Return true if host mode option is supported for the given host type.
     * Currently supported host models are Windows, Linux, ESX, AIX
     * 
     * @param hostType
     * @return
     */
    private boolean isHostModeOptionSupported(String hostType) {
        return hostType.equalsIgnoreCase(Host.HostType.Windows.name()) ||
                hostType.equalsIgnoreCase(Host.HostType.Linux.name()) ||
                hostType.equalsIgnoreCase(Host.HostType.AIX.name()) ||
                hostType.equalsIgnoreCase(Host.HostType.Esx.name()) ||
                hostType.equalsIgnoreCase(Host.HostType.HPUX.name());
    }

    /**
     * Registers all the hosts and its initiators with Device Manager.
     * 
     * @param initiatorList
     * @param hdsApiClient
     * @param exportMgr
     * @return
     * @throws Exception
     */
    private void registerHostsWithInitiators(List<Initiator> initiatorList,
            HDSApiClient hdsApiClient) throws Exception {
        Map<HDSHost, List<String>> fcHostsToRegister = new HashMap<HDSHost, List<String>>();
        Map<HDSHost, List<String>> iSCSIHostsToRegister = new HashMap<HDSHost, List<String>>();
        HDSApiExportManager exportMgr = hdsApiClient.getHDSApiExportManager();
        if (null != initiatorList) {
            for (Initiator initiator : initiatorList) {
                if (null != initiator.getHost()) {
                    Host host = dbClient.queryObject(Host.class, initiator.getHost());
                    if (null != host) {
                        // Get host type and OS type of the host
                        Pair<String, String> hostTypeAndOsType = getHostTypeAndOSTypeToRegisterHost(host);
                        HDSHost hdshost = new HDSHost(host.getHostName());
                        hdshost.setHostType(hostTypeAndOsType.first);
                        hdshost.setOsType(hostTypeAndOsType.second);
                        String portWWN = initiator.getInitiatorPort().replace(
                                HDSConstants.COLON, HDSConstants.DOT_OPERATOR);
                        if (initiator.getProtocol().equals(HostInterface.Protocol.FC.name())) {
                            if (!fcHostsToRegister.containsKey(hdshost)) {
                                List<String> initiatorsList = new ArrayList<String>();
                                initiatorsList.add(portWWN);
                                fcHostsToRegister.put(hdshost, initiatorsList);
                            } else {
                                fcHostsToRegister.get(hdshost).add(portWWN);
                            }
                        } else if (initiator.getProtocol().equals(HostInterface.Protocol.iSCSI.name())) {
                            if (!iSCSIHostsToRegister.containsKey(hdshost)) {
                                List<String> initiatorsList = new ArrayList<String>();
                                initiatorsList.add(portWWN);
                                iSCSIHostsToRegister.put(hdshost, initiatorsList);
                            } else {
                                iSCSIHostsToRegister.get(hdshost).add(portWWN);
                            }
                        }
                    }
                }
            }
        }
        if (!fcHostsToRegister.isEmpty()) {
            for (Map.Entry<HDSHost, List<String>> entry : fcHostsToRegister.entrySet()) {
                HDSHost hdshost = entry.getKey();
                // Try registering the host, if host already exists, we will get an exception.
                // Validating host and its portwwns is costly hence directly adding the host to Device Manager.
                HDSHost registeredHost = exportMgr.registerHost(hdshost, entry.getValue(), HDSConstants.FC);
                if (null != registeredHost) {
                    log.info("Host: {} added successfully.", registeredHost.getName());
                }
            }
        }
        if (!iSCSIHostsToRegister.isEmpty()) {
            for (Map.Entry<HDSHost, List<String>> entry : iSCSIHostsToRegister.entrySet()) {
                HDSHost hdshost = entry.getKey();
                // Try registering the host, if host already exists, we will get an exception.
                // Validating host and its portwwns is costly hence directly adding the host to Device Manager.
                HDSHost registeredHost = exportMgr.registerHost(hdshost, entry.getValue(), HDSConstants.ISCSI);
                if (null != registeredHost) {
                    log.info("Host: {} added successfully.", registeredHost.getName());
                }
            }
        }
    }

    /**
     * Returns the host type and OS type for the given host
     * 
     * @param host
     * @return
     */
    private Pair<String, String> getHostTypeAndOSTypeToRegisterHost(Host host) {
        String hostType = null, osType = null;
        if (Host.HostType.Windows.name().equalsIgnoreCase(host.getType()) ||
                Host.HostType.Linux.name().equalsIgnoreCase(host.getType()) ||
                Host.HostType.AIX.name().equalsIgnoreCase(host.getType())) {
            osType = host.getType();
        } else if (Host.HostType.HPUX.name().equalsIgnoreCase(host.getType())) {
            osType = HDSConstants.HICOMMAND_OS_TYPE_HPUX;
        } else if (Host.HostType.Esx.name().equalsIgnoreCase(host.getType())) {
            hostType = HDSConstants.HICOMMAND_HOST_TYPE_VMWARE;
        }
        return new Pair<String, String>(hostType, osType);
    }

    /**
     * Updates the HLU information in the exportmask.
     * 
     * @param volumeURIHLUs
     * @param pathList
     * @param exportMask
     */
    private void updateVolumeHLUInfo(VolumeURIHLU[] volumeURIHLUs, List<Path> pathList, ExportMask exportMask) {
        if (null != exportMask.getVolumes() && !exportMask.getVolumes().isEmpty()) {
            Map<String, URI> deviceIdToURI = new HashMap<String, URI>();
            Map<URI, Integer> hluMap = new HashMap<URI, Integer>();
            for (VolumeURIHLU vuh : volumeURIHLUs) {
                BlockObject volume = BlockObject.fetch(dbClient, vuh.getVolumeURI());
                exportMask.addToUserCreatedVolumes(volume);
                deviceIdToURI.put(volume.getNativeId(), volume.getId());
            }
            if (!deviceIdToURI.isEmpty()) {
                for (Path path : pathList) {
                    if (deviceIdToURI.containsKey(path.getDevNum())) {
                        URI volumeURI = deviceIdToURI.get(path.getDevNum());
                        log.info("updating volume {} info in exportmask.", volumeURI);
                        hluMap.put(volumeURI, Integer.parseInt(path.getLun()));
                    }
                }
                exportMask.addVolumes(hluMap);
            } else {
                log.error("No HLU's found for the volumes.");
            }
        }
    }

    /**
     * 
     * @param volumeURIHLUs
     * @return
     * @throws Exception
     */
    private Map<String, String> getVolumeLunMap(String systemId, String hsdObjectID, VolumeURIHLU[] volumeURIHLUs,
            HDSApiExportManager exportMgr) throws Exception {
        List<FreeLun> freeLunList = exportMgr.getFreeLUNInfo(systemId, hsdObjectID);
        log.info("There are {} freeLUN's available for HSD Id {}", freeLunList.size(), hsdObjectID);
        Map<String, String> volumeHLUMap = new HashMap<String, String>();
        int i = 0;
        String lun = null;
        for (VolumeURIHLU volumeURIHLU : volumeURIHLUs) {
            BlockObject volume = BlockObject.fetch(dbClient, volumeURIHLU.getVolumeURI());
            if (null == volumeURIHLU.getHLU() || "-1".equalsIgnoreCase(volumeURIHLU.getHLU())) {
                if (i < freeLunList.size()) {
                    FreeLun freeLun = freeLunList.get(i);
                    lun = freeLun.getLun();
                    log.info("HLU is unassigned for volume {} and assinging lun {} from array.", volumeURIHLU.getVolumeURI(), lun);
                    i++;
                } else {
                    // received request to create more volumes than the free luns available on the array.
                    log.info("No free LUN's available on HSD {}", hsdObjectID);
                    throw HDSException.exceptions.unableToProcessRequestDueToUnavailableFreeLUNs();
                }

            } else {
                log.info("HLU {} is assigned for volume {} and using the free lun from array.", volumeURIHLU.getHLU(),
                        volumeURIHLU.getVolumeURI());
                lun = volumeURIHLU.getHLU();
            }
            volumeHLUMap.put(volume.getNativeId(), lun);
        }
        return volumeHLUMap;
    }

    /**
     * Return the PortId from port nativeGuid.
     * 
     * @param nativeGuid
     * @return
     */
    private String getStoragePortNumber(String nativeGuid) {
        Iterable<String> portNativeGuidSplitter = Splitter.on(HDSConstants.DOT_OPERATOR).limit(6).split(nativeGuid);
        return Iterables.getLast(portNativeGuidSplitter);
    }

    @Override
    public void deleteExportMask(StorageSystem storage, URI exportMaskURI,
            List<URI> volumeURIList, List<URI> targetURIList,
            List<Initiator> initiatorList, TaskCompleter taskCompleter)
            throws DeviceControllerException {
        log.info("{} deleteExportMask START...", storage.getSerialNumber());
        List<HostStorageDomain> hsdToDeleteList = new ArrayList<HostStorageDomain>();
        try {
            ExportMask exportMask = dbClient.queryObject(ExportMask.class, exportMaskURI);
            HDSApiClient hdsApiClient = hdsApiFactory.getClient(
                    getHDSServerManagementServerInfo(storage), storage.getSmisUserName(),
                    storage.getSmisPassword());
            HDSApiExportManager exportMgr = hdsApiClient.getHDSApiExportManager();
            String systemObjectId = HDSUtils.getSystemObjectID(storage);
            StringSetMap deviceDataMap = exportMask.getDeviceDataMap();
            if (null != deviceDataMap && !deviceDataMap.isEmpty()) {
                Set<String> hsdObjectIdList = deviceDataMap.keySet();
                for (String hsdObjectIdFromDb : hsdObjectIdList) {
                    HostStorageDomain hsdObj = exportMgr
                            .getHostStorageDomain(systemObjectId, hsdObjectIdFromDb);
                    if (null != hsdObj) {
                        hsdToDeleteList.add(hsdObj);
                    }
                }
                if (!hsdToDeleteList.isEmpty()) {
                    hdsApiClient.getHDSBatchApiExportManager()
                            .deleteBatchHostStorageDomains(systemObjectId,
                                    hsdToDeleteList, storage.getModel());
                }
                // By this time, we have removed all HSD's created in this mask.
                taskCompleter.ready(dbClient);
            } else {
                String message = String.format("ExportMask %s does not have a configured HSD's, "
                        + "indicating that this export may not have been created "
                        + "successfully. Marking the delete operation ready.",
                        exportMaskURI.toString());
                log.info(message);
                taskCompleter.ready(dbClient);
                return;
            }

        } catch (Exception e) {
            log.error("Unexpected error: deleteExportMask failed.", e);
            ServiceError error = DeviceControllerErrors.hds.methodFailed("deleteExportMask", e.getMessage());
            taskCompleter.error(dbClient, error);
        }
        log.info("{} deleteExportMask END...", storage.getSerialNumber());
    }

    @Override
    public void addVolume(StorageSystem storage, URI exportMaskURI,
            VolumeURIHLU[] volumeURIHLUs, TaskCompleter taskCompleter)
            throws DeviceControllerException {
        log.info("{} addVolume START...", storage.getSerialNumber());
        log.info("{} addVolume START...", storage.getSerialNumber());
        HDSApiClient hdsApiClient = null;
        String systemObjectID = null;
        try {
            hdsApiClient = hdsApiFactory.getClient(
                    getHDSServerManagementServerInfo(storage),
                    storage.getSmisUserName(), storage.getSmisPassword());
            HDSApiExportManager exportMgr = hdsApiClient
                    .getHDSApiExportManager();
            systemObjectID = HDSUtils.getSystemObjectID(storage);
            ExportMask exportMask = dbClient.queryObject(ExportMask.class,
                    exportMaskURI);

            StringSetMap deviceDataMap = exportMask.getDeviceDataMap();
            Set<String> hsdList = deviceDataMap.keySet();
            if (null == hsdList || hsdList.isEmpty()) {
                throw HDSException.exceptions
                        .notAbleToFindHostStorageDomain(systemObjectID);
            }
            if (null != exportMask && !exportMask.getInactive()
                    && !hsdList.isEmpty()) {
                List<Path> pathList = new ArrayList<Path>();
                for (String hsdObjectID : hsdList) {
                    // Query the provider to see whether the HSD exists or not.
                    HostStorageDomain hsd = exportMgr.getHostStorageDomain(
                            systemObjectID, hsdObjectID);
                    if (null != hsd) {
                        Map<String, String> volumeLunMap = getVolumeLunMap(
                                systemObjectID, hsd.getObjectID(),
                                volumeURIHLUs, exportMgr);
                        for (Map.Entry<String, String> entry : volumeLunMap
                                .entrySet()) {
                            if (!checkIfVolumeAlreadyExistsOnHSD(
                                    entry.getKey(), hsd)) {
                                Path path = new Path(hsd.getPortID(),
                                        hsd.getDomainID(), null,
                                        entry.getValue(), entry.getKey());
                                pathList.add(path);
                            }
                        }
                    }
                }

                List<Path> pathResponseList = hdsApiClient
                        .getHDSBatchApiExportManager().addLUNPathsToHSDs(
                                systemObjectID, pathList, storage.getModel());
                if (null != pathResponseList && !pathResponseList.isEmpty()) {
                    // update volume-lun relationship to exportmask.
                    updateVolumeHLUInfo(volumeURIHLUs, pathResponseList,
                            exportMask);
                    dbClient.updateAndReindexObject(exportMask);
                } else {
                    log.error(
                            String.format("addVolume failed - maskURI: %s",
                                    exportMaskURI.toString()),
                            new Exception(
                                    "Not able to parse the response of addLUN from server"));
                    ServiceError serviceError = DeviceControllerException.errors
                            .jobFailedOpMsg(
                                    ResourceOperationTypeEnum.ADD_EXPORT_VOLUME
                                            .getName(),
                                    "Not able to parse the response of addLUN from server");
                    taskCompleter.error(dbClient, serviceError);
                    return;
                }
                taskCompleter.ready(dbClient);
            }
        } catch (Exception e) {
            log.error(String.format("addVolume failed - maskURI: %s", exportMaskURI.toString()), e);
            ServiceError serviceError = DeviceControllerException.errors.jobFailed(e);
            taskCompleter.error(dbClient, serviceError);
        }
        log.info("{} addVolume END...", storage.getSerialNumber());
    }

    /**
     * Verify whether volume already exists on the HostGroup.
     * 
     * @param devNum
     * @param hsd
     * @return
     */
    private boolean checkIfVolumeAlreadyExistsOnHSD(String devNum, HostStorageDomain hsd) {
        boolean isVolumeFound = false;
        List<Path> pathList = hsd.getPathList();
        if (null != pathList && !pathList.isEmpty()) {
            for (Path path : pathList) {
                if (path.getDevNum().equalsIgnoreCase(devNum)) {
                    isVolumeFound = true;
                    break;
                }
            }
        }
        return isVolumeFound;
    }

    @Override
    public void removeVolume(StorageSystem storage, URI exportMaskURI, List<URI> volumes,
            TaskCompleter taskCompleter) throws DeviceControllerException {
        log.info("{} removeVolume START...", storage.getSerialNumber());
        try {
            HDSApiClient hdsApiClient = hdsApiFactory.getClient(
                    getHDSServerManagementServerInfo(storage), storage.getSmisUserName(),
                    storage.getSmisPassword());
            HDSApiExportManager exportMgr = hdsApiClient.getHDSApiExportManager();
            String systemObjectID = HDSUtils.getSystemObjectID(storage);
            ExportMask exportMask = dbClient.queryObject(ExportMask.class, exportMaskURI);
            StringSetMap deviceDataMap = exportMask.getDeviceDataMap();
            Set<String> hsdList = deviceDataMap.keySet();
            List<Path> pathObjectIdList = new ArrayList<Path>();
            if (null == hsdList || hsdList.isEmpty()) {
                throw HDSException.exceptions
                        .notAbleToFindHostStorageDomain(systemObjectID);
            }
            if (null != exportMask && !exportMask.getInactive() && !hsdList.isEmpty()) {
                for (String hsdObjectId : hsdList) {
                    HostStorageDomain hsd = exportMgr.getHostStorageDomain(
                            systemObjectID, hsdObjectId);
                    if (null == hsd) {
                        log.warn("Couldn't find the HSD {} to remove volume from ExportMask", hsdObjectId);
                        continue;
                    }
                    if (null != hsd.getPathList() && !hsd.getPathList().isEmpty()) {
                        pathObjectIdList.addAll(getPathObjectIdsFromHsd(hsd,
                                volumes));
                    }
                }
                if (!pathObjectIdList.isEmpty()) {
                    hdsApiClient.getHDSBatchApiExportManager()
                            .deleteLUNPathsFromStorageSystem(systemObjectID,
                                    pathObjectIdList, storage.getModel());

                } else {
                    log.info("No volumes found on system: {}", systemObjectID);
                }
                // Update the status after deleting the volume from all HSD's.
                taskCompleter.ready(dbClient);
            }
        } catch (Exception e) {
            log.error(
                    String.format("removeVolume failed - maskURI: %s",
                            exportMaskURI.toString()), e);
            ServiceError serviceError = DeviceControllerException.errors.jobFailed(e);
            taskCompleter.error(dbClient, serviceError);
        }
        log.info("{} removeVolume END...", storage.getSerialNumber());
    }

    /**
     * Get the LUN Path objectId list from HSD.
     * Since we are getting volume URI, we should query to get its nativeId.
     * 
     * @param hsd
     * @param volumes
     * @return
     */
    private List<Path> getPathObjectIdsFromHsd(HostStorageDomain hsd, List<URI> volumes) {
        List<Path> pathObjectIdList = new ArrayList<Path>();
        if (null != hsd.getPathList()) {
            for (URI volumeURI : volumes) {
                BlockObject volume = BlockObject.fetch(dbClient, volumeURI);
                for (Path path : hsd.getPathList()) {
                    log.info("verifying existence of volume {} on HSD to remove",
                            volume.getNativeId());
                    if (volume.getNativeId().equals(path.getDevNum())) {
                        log.debug("Found volume {} on HSD {}", volume.getNativeId(), hsd.getObjectID());
                        pathObjectIdList.add(path);
                    }
                }
            }
        }
        return pathObjectIdList;
    }

    @Override
    public void addInitiator(StorageSystem storage, URI exportMaskURI,
            List<Initiator> initiators, List<URI> targetURIList, TaskCompleter taskCompleter)
            throws DeviceControllerException {
        log.info("{} addInitiator START...", storage.getSerialNumber());
        HDSApiClient hdsApiClient = null;
        String systemObjectID = null;
        List<HostStorageDomain> hsdsToCreate = null;
        List<HostStorageDomain> hsdsWithInitiators = null;
        try {
            hdsApiClient = hdsApiFactory.getClient(
                    getHDSServerManagementServerInfo(storage), storage.getSmisUserName(),
                    storage.getSmisPassword());
            systemObjectID = HDSUtils.getSystemObjectID(storage);
            ExportMask exportMask = dbClient.queryObject(ExportMask.class, exportMaskURI);

            List<StoragePort> ports = dbClient.queryObject(StoragePort.class, targetURIList, true);
            if (checkIfMixedTargetPortTypeSelected(ports)) {
                log.error("Unsupported Host as it has both FC & iSCSI Initiators");
                throw HDSException.exceptions.unsupportedConfigurationFoundInHost();
            }
            // @TODO register new initiators by adding them to host on HiCommand DM.
            // Currently, HiCommand is not supporting this. Need to see how we can handle.

            String hostName = getHostNameForInitiators(initiators);
            String hostMode = null, hostModeOption = null;
            Pair<String, String> hostModeInfo = getHostModeInfo(storage, initiators);
            if (hostModeInfo != null) {
                hostMode = hostModeInfo.first;
                hostModeOption = hostModeInfo.second;
            }

            // Case 1 is handled here for the new initiators & new target ports.
            if (targetURIList != null && !targetURIList.isEmpty() &&
                    !exportMask.hasTargets(targetURIList)) {
                // If the HLU's are already configured on this target port, then an exception is thrown.
                // User should make sure that all volumes should have same HLU across all target HSD's.
                VolumeURIHLU[] volumeURIHLUs = getVolumeURIHLUFromExportMask(exportMask);
                if (0 < volumeURIHLUs.length) {
                    hsdsToCreate = processTargetPortsToFormHSDs(hdsApiClient, storage,
                            targetURIList, hostName, exportMask, hostModeInfo, systemObjectID);
                    // Step 1: Create all HSD's using batch operation.
                    List<HostStorageDomain> hsdResponseList = hdsApiClient
                            .getHDSBatchApiExportManager().addHostStorageDomains(
                                    systemObjectID, hsdsToCreate, storage.getModel());

                    if (null == hsdResponseList || hsdResponseList.isEmpty()) {
                        log.error("Batch HSD creation failed to add new initiators. Aborting operation...");
                        throw HDSException.exceptions.notAbleToAddHSD(storage
                                .getSerialNumber());
                    }
                    // Step 2: Add initiators to all HSD's.
                    hsdsWithInitiators = executeBatchHSDAddInitiatorsCommand(hdsApiClient,
                            systemObjectID, hsdResponseList, ports, initiators, storage.getModel());

                    // Step 3: Add volumes to all HSD's.
                    List<Path> allHSDPaths = executeBatchHSDAddVolumesCommand(hdsApiClient,
                            systemObjectID, hsdsWithInitiators, volumeURIHLUs, storage.getModel());

                    if (null != allHSDPaths && !allHSDPaths.isEmpty()) {
                        updateExportMaskDetailInDB(hsdsWithInitiators, allHSDPaths,
                                exportMask, storage, volumeURIHLUs);
                    }

                } else {
                    log.info("There are no volumes on this exportmask: {} to add to new initiator", exportMaskURI);
                }
            }
            taskCompleter.ready(dbClient);
        } catch (Exception ex) {
            try {
                log.info("Exception occurred while adding new initiators: {}", ex.getMessage());
                if (null != hsdsWithInitiators && !hsdsWithInitiators.isEmpty()) {
                    hdsApiClient.getHDSBatchApiExportManager()
                            .deleteBatchHostStorageDomains(systemObjectID,
                                    hsdsWithInitiators, storage.getModel());
                } else {
                    if (null != hsdsToCreate && !hsdsToCreate.isEmpty()) {
                        List<HostStorageDomain> allHSDs = hdsApiClient
                                .getHDSApiExportManager()
                                .getHostStorageDomains(systemObjectID);
                        List<HostStorageDomain> partialHSDListToRemove = getPartialHSDListToDelete(
                                allHSDs, hsdsToCreate);
                        hdsApiClient.getHDSBatchApiExportManager()
                                .deleteBatchHostStorageDomains(systemObjectID,
                                        partialHSDListToRemove, storage.getModel());
                    }
                }
                log.error(String.format("addInitiator failed - maskURI: %s",
                        exportMaskURI.toString()), ex);
            } catch (Exception ex1) {
                log.error(
                        "Exception occurred while deleting unsuccessful HSDs on system: {}",
                        systemObjectID, ex1.getMessage());
            } finally {
                ServiceError serviceError = DeviceControllerException.errors.jobFailedOpMsg(
                        ResourceOperationTypeEnum.ADD_EXPORT_INITIATOR.getName(),
                        ex.getMessage());
                taskCompleter.error(dbClient, serviceError);
            }
        }
        log.info("{} addInitiator END...", storage.getSerialNumber());
    }

    /**
     * Return VolumeURIHLU from the exportMask userAddedVolume.
     * should we add existing volumes also in case if the environment is not green field?
     * 
     * @param exportMask
     * @return
     */
    private VolumeURIHLU[] getVolumeURIHLUFromExportMask(ExportMask exportMask) {
        VolumeURIHLU[] volumeURIHLU = null;
        if (null != exportMask && null != exportMask.getUserAddedVolumes() && !exportMask.getUserAddedVolumes().isEmpty()) {
            Set<String> userAddedVolumeSet = new HashSet<String>(exportMask.getUserAddedVolumes().values());
            volumeURIHLU = new VolumeURIHLU[userAddedVolumeSet.size()];
            int index = 0;
            for (String userAddedVolume : userAddedVolumeSet) {
                URI userAddedVolumeURI = URI.create(userAddedVolume);
                BlockObject volume = BlockObject.fetch(dbClient, userAddedVolumeURI);
                if (null != volume && !volume.getInactive()) {
                    String hlu = exportMask.getVolumes().get(userAddedVolume);
                    volumeURIHLU[index++] = new VolumeURIHLU(userAddedVolumeURI, hlu, null, volume.getLabel());
                }

            }
        }
        return volumeURIHLU;
    }

    @Override
    public void removeInitiator(StorageSystem storage, URI exportMaskURI,
            List<Initiator> initiators, List<URI> targets, TaskCompleter taskCompleter)
            throws DeviceControllerException {
        long startTime = System.currentTimeMillis();
        log.info("{} removeInitiator START...", storage.getSerialNumber());
        try {
            HDSApiClient hdsApiClient = hdsApiFactory.getClient(
                    getHDSServerManagementServerInfo(storage), storage.getSmisUserName(),
                    storage.getSmisPassword());
            HDSApiExportManager exportMgr = hdsApiClient.getHDSApiExportManager();
            String systemObjectID = HDSUtils.getSystemObjectID(storage);
            ExportMask exportMask = dbClient.queryObject(ExportMask.class, exportMaskURI);
            StringSetMap deviceDataMap = exportMask.getDeviceDataMap();
            if (null != deviceDataMap && !deviceDataMap.isEmpty()) {
                Set<String> hsdObjectIDSet = deviceDataMap.keySet();
                for (String hsdObjectID : hsdObjectIDSet) {
                    HostStorageDomain hsd = exportMgr.getHostStorageDomain(
                            systemObjectID, hsdObjectID);
                    if (null == hsd) {
                        log.warn("Not able to remove initiators as HSD {} couldn't find on array.", hsdObjectID);
                        continue;
                    }
                    List<String> fcInitiators = getFCInitiatorsExistOnHSD(hsd, initiators);
                    List<String> iSCSIInitiators = getISCSIInitiatorsExistOnHSD(hsd, initiators);
                    boolean isLastFCInitiator = (fcInitiators.size() == 1 && null != hsd.getWwnList() && hsd
                            .getWwnList().size() == fcInitiators.size());
                    boolean isLastISCSIInitiator = (iSCSIInitiators.size() == 1 && null != hsd.getIscsiList() && hsd
                            .getIscsiList().size() == iSCSIInitiators.size());
                    // If Initiator is last one, remove the HSD
                    if (isLastFCInitiator || isLastISCSIInitiator) {
                        exportMgr.deleteHostStorageDomain(systemObjectID, hsd.getObjectID(), storage.getModel());
                        exportMask.getDeviceDataMap().remove(hsd.getObjectID());
                    } else {
                        if (null != fcInitiators && !fcInitiators.isEmpty()) {
                            // remove FC initiators from HSD.
                            exportMgr.deleteWWNsFromHostStorageDomain(systemObjectID, hsd.getObjectID(),
                                    fcInitiators, storage.getModel());
                        }
                        if (null != iSCSIInitiators && !iSCSIInitiators.isEmpty()) {
                            // remove ISCSInames from HSD.
                            exportMgr.deleteISCSIsFromHostStorageDomain(systemObjectID, hsd.getObjectID(),
                                    iSCSIInitiators, storage.getModel());
                        }
                    }
                }
                dbClient.updateObject(exportMask);
                // update the task status after processing all HSD's.
                taskCompleter.ready(dbClient);
            } else {
                log.info("No Host groups configured on exportMask {}", exportMaskURI);
                // No HSD's found in exportMask.
                taskCompleter.ready(dbClient);
            }

        } catch (Exception e) {
            log.error(
                    String.format("removeInitiator failed - maskURI: %s",
                            exportMaskURI.toString()), e);
            ServiceError serviceError = DeviceControllerException.errors.jobFailedOpMsg(
                    ResourceOperationTypeEnum.DELETE_EXPORT_INITIATOR.getName(),
                    e.getMessage());
            taskCompleter.error(dbClient, serviceError);
        } finally {
            long totalTime = System.currentTimeMillis() - startTime;
            log.info(String.format("findExportMasks took %f seconds", (double) totalTime / (double) 1000));
        }
        log.info("{} removeInitiator END...", storage.getSerialNumber());
    }

    /**
     * Return the list of WWN's to be removed from the HSD.
     * 
     * @param hsd
     * @param allInitiatorsToRemove
     * @return
     */
    private List<String> getFCInitiatorsExistOnHSD(HostStorageDomain hsd, List<Initiator> allInitiatorsToRemove) {
        List<WorldWideName> hsdWWNList = hsd.getWwnList();
        List<String> fcInitiatorsToRemove = new ArrayList<String>();
        Collection<String> portNames = Collections2.transform(allInitiatorsToRemove,
                CommonTransformerFunctions.fctnInitiatorToPortName());
        if (null != hsdWWNList && !hsdWWNList.isEmpty()) {
            for (WorldWideName wwn : hsdWWNList) {
                if (portNames.contains(wwn.getWwn().replace(
                        HDSConstants.DOT_OPERATOR, HDSConstants.EMPTY_STR).toUpperCase())) {
                    fcInitiatorsToRemove.add(wwn.getWwn());
                }
            }
        }
        return fcInitiatorsToRemove;
    }

    /**
     * Return the list of ISCSINames to be removed from the HSD.
     * 
     * @param hsd
     * @param allInitiatorsToRemove
     * @return
     */
    private List<String> getISCSIInitiatorsExistOnHSD(HostStorageDomain hsd, List<Initiator> allInitiatorsToRemove) {
        List<ISCSIName> hsdISCSIList = hsd.getIscsiList();
        List<String> iSCSIInitiatorsToRemove = new ArrayList<String>();
        Collection<String> portNames = Collections2.transform(allInitiatorsToRemove,
                CommonTransformerFunctions.fctnInitiatorToPortName());
        if (null != hsdISCSIList && !hsdISCSIList.isEmpty()) {
            for (ISCSIName iSCSIName : hsdISCSIList) {
                if (portNames.contains(iSCSIName.getiSCSIName())) {
                    iSCSIInitiatorsToRemove.add(iSCSIName.getiSCSIName());
                }
            }
        }
        return iSCSIInitiatorsToRemove;
    }

    @Override
    public Map<String, Set<URI>> findExportMasks(StorageSystem storage,
            List<String> initiatorNames, boolean mustHaveAllPorts) {
        Map<String, Set<URI>> matchingMasks = new HashMap<String, Set<URI>>();
        log.info("finding export masks for storage {}", storage.getId());
        HDSApiClient client = hdsApiFactory.getClient(
                getHDSServerManagementServerInfo(storage),
                storage.getSmisUserName(), storage.getSmisPassword());
        HDSApiExportManager exportManager = client.getHDSApiExportManager();
        String systemObjectID = HDSUtils.getSystemObjectID(storage);
        Map<URI, Set<HostStorageDomain>> matchedHostHSDsMap = new HashMap<URI, Set<HostStorageDomain>>();
        Map<URI, Set<URI>> hostToInitiatorMap = new HashMap<URI, Set<URI>>();
        Map<URI, Set<String>> matchedHostInitiators = new HashMap<URI, Set<String>>();
        try {
            List<HostStorageDomain> hsdList = exportManager
                    .getHostStorageDomains(systemObjectID);
            for (HostStorageDomain hsd : hsdList) {
                List<String> initiatorsExistsOnHSD = getInitiatorsExistsOnHSD(
                        hsd.getWwnList(), hsd.getIscsiList());
                // Find out if the port is in this masking container
                for (String initiatorName : initiatorNames) {
                    String normalizedName = initiatorName.replace(
                            HDSConstants.COLON, HDSConstants.DOT_OPERATOR);
                    if (initiatorsExistsOnHSD.contains(normalizedName)) {
                        log.info("Found a matching HSD for {}", initiatorName);
                        Initiator initiator = fetchInitiatorByName(initiatorName);
                        Set<HostStorageDomain> matchedHSDs = matchedHostHSDsMap
                                .get(initiator.getHost());
                        if (matchedHSDs == null) {
                            matchedHSDs = new HashSet<HostStorageDomain>();
                            matchedHostHSDsMap.put(initiator.getHost(),
                                    matchedHSDs);
                        }
                        matchedHSDs.add(hsd);
                        Set<String> matchedInitiators = matchedHostInitiators
                                .get(initiator.getHost());
                        if (null == matchedInitiators) {
                            matchedInitiators = new HashSet<String>();
                            matchedHostInitiators.put(initiator.getHost(),
                                    matchedInitiators);
                        }
                        matchedInitiators.add(initiatorName);
                    }
                }
            }
            hsdList.clear();
            log.info("matchedHSDs: {}", matchedHostHSDsMap);
            log.info("initiatorURIToNameMap: {}", matchedHostInitiators);
            processInitiators(initiatorNames, hostToInitiatorMap);
            List<URI> activeMaskIdsInDb = dbClient.queryByType(
                    ExportMask.class, true);
            List<ExportMask> activeMasks = dbClient.queryObject(
                    ExportMask.class, activeMaskIdsInDb);
            if (null != matchedHostHSDsMap && !matchedHostHSDsMap.isEmpty()) {
                // Iterate through each host
                for (URI hostURI : hostToInitiatorMap.keySet()) {
                    Set<URI> hostInitiators = hostToInitiatorMap.get(hostURI);
                    boolean isNewExportMask = false;
                    // Create single ExportMask for each host
                    ExportMask exportMask = fetchExportMaskFromDB(activeMasks,
                            hostInitiators, storage);
                    if (null == exportMask) {
                        isNewExportMask = true;
                        exportMask = new ExportMask();
                        exportMask.setId(URIUtil.createId(ExportMask.class));
                        exportMask.setStorageDevice(storage.getId());
                        exportMask.setCreatedBySystem(false);
                    }
                    updateHSDInfoInExportMask(exportMask, hostInitiators,
                            matchedHostHSDsMap.get(hostURI), storage,
                            matchingMasks);
                    if (isNewExportMask) {
                        dbClient.createObject(exportMask);
                    } else {
                        ExportMaskUtils.sanitizeExportMaskContainers(dbClient, exportMask);
                        dbClient.updateAndReindexObject(exportMask);
                    }
                    updateMatchingMasksForHost(
                            matchedHostInitiators.get(hostURI), exportMask,
                            matchingMasks);
                }
            }

        } catch (Exception e) {
            log.error("Error when attempting to query LUN masking information",
                    e);
            throw HDSException.exceptions.queryExistingMasksFailure(e
                    .getMessage());
        }
        log.info("Found matching masks: {}", matchingMasks);
        return matchingMasks;
    }

    /**
     * Updates MatchingMasks and return to the Orchestrator to act on on the matchingMasks.
     * Orchestrator will decide whether to add new volumes/initiators based on this collection.
     * 
     * @param matchedHostInitiators
     * @param exportMask
     * @param matchingMasks
     */
    private void updateMatchingMasksForHost(Set<String> matchedHostInitiators,
            ExportMask exportMask, Map<String, Set<URI>> matchingMasks) {
        if (null != matchedHostInitiators && !matchedHostInitiators.isEmpty()) {
            for (String initiatorName : matchedHostInitiators) {
                Set<URI> maskURIs = matchingMasks.get(initiatorName);
                if (maskURIs == null) {
                    maskURIs = new HashSet<URI>();
                    matchingMasks.put(initiatorName, maskURIs);
                }
                // Assuming all initiators are configured
                maskURIs.add(exportMask.getId());
            }
        }

    }

    /**
     * Updates the HSD information in the ExportMask.
     * 
     * @param exportMask
     * @param hostInitiators
     * @param matchedHSDs
     */
    private void updateHSDInfoInExportMask(ExportMask exportMask,
            Set<URI> hostInitiators, Set<HostStorageDomain> matchedHSDs,
            StorageSystem storage, Map<String, Set<URI>> matchingMasks) {
        StringBuilder builder = new StringBuilder();
        if (null != matchedHSDs && !matchedHSDs.isEmpty()) {
            StringSetMap deviceDataMapEntries = new StringSetMap();
            for (HostStorageDomain matchedHSD : matchedHSDs) {
                List<String> initiatorsExistsOnHSD = getInitiatorsExistsOnHSD(
                        matchedHSD.getWwnList(), matchedHSD.getIscsiList());
                // volume Map['volume Id'] = lun
                Map<String, Integer> volumesExistsOnHSD = getExportedVolumes(
                        matchedHSD.getPathList(), storage);
                log.info("Current list of Volumes exists on this HSD: {}",
                        volumesExistsOnHSD);
                builder.append(String.format("XM:%s I:{%s} V:{%s}%n",
                        matchedHSD.getObjectID(),
                        Joiner.on(',').join(initiatorsExistsOnHSD),
                        Joiner.on(',').join(volumesExistsOnHSD.keySet())));
                // Update deviceDataMap in ExportMask with HSD information.
                updateDeviceDataMapInExportMask(exportMask, matchedHSD, storage, deviceDataMapEntries);
                List<String> storagePorts = Arrays.asList(matchedHSD
                        .getPortID());

                List<String> storagePortURIs = getStoragePortURIs(storagePorts,
                        storage);

                if (null != exportMask.getStoragePorts()
                        && !exportMask.getStoragePorts().isEmpty()) {
                    // Add the storage ports if they already exist on
                    // exportmask.
                    exportMask.getStoragePorts().addAll(storagePortURIs);
                } else {
                    exportMask.setStoragePorts(storagePortURIs);
                }
                String maskName = (null == matchedHSD.getName()) ? matchedHSD
                        .getNickname() : matchedHSD.getName();
                exportMask.setMaskName(maskName);
                exportMask.addToExistingVolumesIfAbsent(volumesExistsOnHSD);
                exportMask
                        .addToExistingInitiatorsIfAbsent(initiatorsExistsOnHSD);

                builder.append(String
                        .format("XM is matching. " + "EI: { %s }, EV: { %s }\n",
                                Joiner.on(',').join(
                                        exportMask.getExistingInitiators()),
                                Joiner.on(',').join(
                                        exportMask.getExistingVolumes()
                                                .keySet())));

            }
            if (null == exportMask.getDeviceDataMap()
                    || exportMask.getDeviceDataMap().isEmpty()) {
                exportMask.addDeviceDataMap(deviceDataMapEntries);
            } else {
                exportMask.replaceDeviceDataMapEntries(deviceDataMapEntries);
            }
        }

        log.info(builder.toString());
    }

    /**
     * Fetches ExportMask from DB based on the given initiators.
     * 
     * @param hostInitiators
     * @param storage
     * @return
     */
    private ExportMask fetchExportMaskFromDB(List<ExportMask> activeMasks, Set<URI> hostInitiators, StorageSystem storage) {
        ExportMask exportMask = null;
        if (null != activeMasks && !activeMasks.isEmpty()) {
            for (ExportMask activeExportMask : activeMasks) {
                if (!activeExportMask.getStorageDevice().equals(storage.getId())) {
                    continue;
                }
                Set<URI> emInitiators = ExportMaskUtils.getAllInitiatorsForExportMask(dbClient, activeExportMask);
                Set<URI> matchingInitiators = Sets.intersection(emInitiators, hostInitiators);
                if (!matchingInitiators.isEmpty()) {
                    exportMask = activeExportMask;
                    break;
                }
            }
        }
        return exportMask;
    }

    /**
     * Process the initiators passed in to form a host => initiators data
     * structure.
     * 
     * @param initiatorNames
     * @param hostToInitiatorMap
     */
    private void processInitiators(List<String> initiatorNames, Map<URI, Set<URI>> hostToInitiatorMap) {
        for (String initiatorWWNStr : initiatorNames) {
            Initiator initiator = fetchInitiatorByName(initiatorWWNStr);
            if (null != initiator) {
                Set<URI> initiators = hostToInitiatorMap.get(initiator.getHost());
                if (initiators == null) {
                    initiators = new HashSet<URI>();
                    hostToInitiatorMap.put(initiator.getHost(), initiators);
                }
                initiators.add(initiator.getId());
            }
        }
    }

    /**
     * Fetches initiator from DB based on its WWN.
     * 
     * @param initiatorName
     * @return
     */
    private Initiator fetchInitiatorByName(String initiatorName) {
        URIQueryResultList initiatorResultList = new URIQueryResultList();
        Initiator initiator = null;
        dbClient.queryByConstraint(AlternateIdConstraint.Factory.getInitiatorPortInitiatorConstraint(WWNUtility
                .getWWNWithColons(initiatorName)), initiatorResultList);
        if (initiatorResultList.iterator().hasNext()) {
            initiator = dbClient.queryObject(Initiator.class, initiatorResultList.iterator().next());
        }
        return initiator;
    }

    /**
     * Updates the DeviceDataMap in the ExportMask for all HSD's.
     * 
     * @param exportMask
     * @param hsd
     * @param storage
     * @param deviceDataMap
     * @param deviceDataMapEntries
     */
    private void updateDeviceDataMapInExportMask(ExportMask exportMask,
            HostStorageDomain hsd, StorageSystem storage,
            StringSetMap deviceDataMapEntries) {
        List<String> storagePorts = Arrays.asList(hsd.getPortID());
        StringSet hsdTargetPortValues = new StringSet();
        List<String> storagePortURIs = getStoragePortURIs(storagePorts, storage);
        hsdTargetPortValues.addAll(storagePortURIs);
        deviceDataMapEntries.put(hsd.getObjectID(), hsdTargetPortValues);
    }

    /**
     * Since we get only port Ids from HSD, we should iterate thru all the ports of the system
     * and find its URIs.
     * 
     * @param storagePortsOnExistingHSD
     * @param storage
     * @return
     */
    private List<String> getStoragePortURIs(List<String> storagePortsOnExistingHSD, StorageSystem storage) {
        List<String> portURIs = new ArrayList<String>();
        URIQueryResultList portURIList = new URIQueryResultList();
        dbClient.queryByConstraint(ContainmentConstraint.Factory.getStorageDeviceStoragePortConstraint(storage.getId()), portURIList);
        List<StoragePort> storagePorts = dbClient.queryObject(StoragePort.class, portURIList);
        for (StoragePort portFromDB : storagePorts) {
            String portNativeIdFromDB = getStoragePortNumber(portFromDB.getNativeGuid());
            for (String portNativeIdOnHSD : storagePortsOnExistingHSD) {
                if (portNativeIdOnHSD.equals(portNativeIdFromDB)) {
                    portURIs.add(portFromDB.getId().toString());
                }
            }

        }
        return portURIs;
    }

    /**
     * Return the list of initiators that were exists on the HostStorageDomain.
     * 
     * @param wwnList
     * @param scsiList
     * @return
     */
    private List<String> getInitiatorsExistsOnHSD(List<WorldWideName> wwnList, List<ISCSIName> scsiList) {
        List<String> initiatorsExistsOnHSD = new ArrayList<String>();
        if (null != wwnList && !wwnList.isEmpty()) {
            for (WorldWideName wwn : wwnList) {
                String wwnWithOutDot = wwn.getWwn().replace(HDSConstants.DOT_OPERATOR, "");
                initiatorsExistsOnHSD.add(wwnWithOutDot);
            }
        }
        if (null != scsiList && !scsiList.isEmpty()) {
            for (ISCSIName scsiName : scsiList) {
                initiatorsExistsOnHSD.add(scsiName.getiSCSIName());
            }
        }
        return initiatorsExistsOnHSD;
    }

    /**
     * Return the [volume-lun] map that exists on the HSD.
     * 
     * @param pathList
     * @return
     */
    private Map<String, Integer> getExportedVolumes(List<Path> pathList, StorageSystem storage) {
        Map<String, Integer> volumeLunMap = new HashMap<String, Integer>();
        if (null != pathList && !pathList.isEmpty()) {
            for (Path path : pathList) {
                String volumeWWN = HDSUtils.generateHitachiVolumeWWN(storage, path.getDevNum());
                volumeLunMap.put(volumeWWN, Integer.valueOf(path.getLun()));
            }
        }
        return volumeLunMap;
    }

    @Override
    public ExportMask refreshExportMask(StorageSystem storage, ExportMask mask) {

        try {
            HDSApiClient client = hdsApiFactory.getClient(
                    getHDSServerManagementServerInfo(storage), storage.getSmisUserName(),
                    storage.getSmisPassword());
            HDSApiExportManager exportManager = client.getHDSApiExportManager();
            String systemObjectID = HDSUtils.getSystemObjectID(storage);
            if (null != mask.getDeviceDataMap() && !mask.getDeviceDataMap().isEmpty()) {
                Set<String> hsdList = mask.getDeviceDataMap().keySet();
                StringBuilder builder = new StringBuilder();
                List<String> discoveredInitiators = new ArrayList<String>();
                String maskName = null;
                Map<String, Integer> discoveredVolumes = new HashMap<String, Integer>();
                for (String hsdObjectIdFromDb : hsdList) {
                    HostStorageDomain hsd = exportManager.getHostStorageDomain(
                            systemObjectID, hsdObjectIdFromDb);
                    if (null == hsd) {
                        // If the HSD is removed using non-ViPR, update ViPR DB.
                        mask.getDeviceDataMap().remove(hsdObjectIdFromDb);
                        continue;
                    }
                    maskName = (null == hsd.getName()) ? hsd.getNickname() : hsd.getName();

                    // Get volumes and initiators for the masking instance
                    discoveredVolumes.putAll(getVolumesFromHSD(hsd, storage));
                    discoveredInitiators.addAll(getInitiatorsFromHSD(hsd));
                }
                Set existingInitiators = (mask.getExistingInitiators() != null) ?
                        mask.getExistingInitiators() : Collections.emptySet();
                Set existingVolumes = (mask.getExistingVolumes() != null) ?
                        mask.getExistingVolumes().keySet() : Collections.emptySet();

                builder.append(String.format("%nXM object: %s I{%s} V:{%s}%n", maskName,
                        Joiner.on(',').join(existingInitiators), Joiner.on(',').join(existingVolumes)));

                builder.append(String.format("XM discovered: %s I:{%s} V:{%s}%n", maskName,
                        Joiner.on(',').join(discoveredInitiators), Joiner.on(',').join(discoveredVolumes.keySet())));

                // Check the initiators and update the lists as necessary
                boolean addInitiators = false;
                List<String> initiatorsToAdd = new ArrayList<String>();
                for (String initiator : discoveredInitiators) {
                    if (!mask.hasExistingInitiator(initiator) && !mask.hasUserInitiator(initiator)) {
                        initiatorsToAdd.add(initiator);
                        addInitiators = true;
                    }
                }

                boolean removeInitiators = false;
                List<String> initiatorsToRemove = new ArrayList<String>();
                if (mask.getExistingInitiators() != null && !mask.getExistingInitiators().isEmpty()) {
                    initiatorsToRemove.addAll(mask.getExistingInitiators());
                    initiatorsToRemove.removeAll(discoveredInitiators);
                    removeInitiators = !initiatorsToRemove.isEmpty();
                }

                // Check the volumes and update the lists as necessary
                boolean addVolumes = false;
                Map<String, Integer> volumesToAdd = new HashMap<String, Integer>();
                for (Map.Entry<String, Integer> entry : discoveredVolumes.entrySet()) {
                    String normalizedWWN = BlockObject.normalizeWWN(entry.getKey());
                    if (!mask.hasExistingVolume(normalizedWWN) && !mask.hasUserCreatedVolume(normalizedWWN)) {
                        volumesToAdd.put(normalizedWWN, entry.getValue());
                        addVolumes = true;
                    }
                }

                boolean removeVolumes = false;
                List<String> volumesToRemove = new ArrayList<String>();
                if (mask.getExistingVolumes() != null && !mask.getExistingVolumes().isEmpty()) {
                    volumesToRemove.addAll(mask.getExistingVolumes().keySet());
                    volumesToRemove.removeAll(discoveredVolumes.keySet());
                    removeVolumes = !volumesToRemove.isEmpty();
                }

                builder.append(String.format("XM refresh: %s initiators; add:{%s} remove:{%s}%n", maskName,
                        Joiner.on(',').join(initiatorsToAdd), Joiner.on(',').join(initiatorsToRemove)));
                builder.append(String.format("XM refresh: %s volumes; add:{%s} remove:{%s}%n", maskName, Joiner.on(',')
                        .join(volumesToAdd.keySet()), Joiner.on(',').join(volumesToRemove)));

                // Any changes indicated, then update the mask and persist it
                if (addInitiators || removeInitiators || addVolumes || removeVolumes) {
                    builder.append("XM refresh: There are changes to mask, " + "updating it...\n");
                    mask.removeFromExistingInitiators(initiatorsToRemove);
                    mask.addToExistingInitiatorsIfAbsent(initiatorsToAdd);
                    mask.removeFromExistingVolumes(volumesToRemove);
                    mask.addToExistingVolumesIfAbsent(volumesToAdd);
                    ExportMaskUtils.sanitizeExportMaskContainers(dbClient, mask);
                    dbClient.updateAndReindexObject(mask);
                } else {
                    builder.append("XM refresh: There are no changes to the mask\n");
                }
                _networkDeviceController.refreshZoningMap(mask, initiatorsToRemove, Collections.EMPTY_LIST,
                        (addInitiators || removeInitiators), true);
                log.info(builder.toString());
            }
        } catch (Exception e) {
            log.error("Error when attempting to query HostStorageDomain information", e);
            throw HDSException.exceptions.refreshExistingMaskFailure(mask.getLabel());
        }
        return mask;

    }

    /**
     * Return the initiators from HSD passed in.
     * 
     * @param hsd
     * @return
     */
    private List<String> getInitiatorsFromHSD(HostStorageDomain hsd) {
        List<String> initiatorsList = new ArrayList<String>();
        if (null != hsd.getWwnList()) {
            for (WorldWideName wwn : hsd.getWwnList()) {
                String wwnName = wwn.getWwn();
                String normalizedPortWWN = wwnName.replace(HDSConstants.DOT_OPERATOR, "");
                initiatorsList.add(normalizedPortWWN);
            }
        }

        if (null != hsd.getIscsiList()) {
            for (ISCSIName iscsi : hsd.getIscsiList()) {
                initiatorsList.add(iscsi.getiSCSIName());
            }
        }
        return initiatorsList;
    }

    /**
     * Return a map[deviceId] => lun from the give HostStorageDomain.
     * 
     * @param hsd
     * @return
     */
    private Map<String, Integer> getVolumesFromHSD(HostStorageDomain hsd, StorageSystem storage) {
        Map<String, Integer> volumesFromHSD = new HashMap<String, Integer>();
        List<Path> pathList = hsd.getPathList();
        if (null != pathList) {
            for (Path path : pathList) {
                String volumeWWN = HDSUtils.generateHitachiVolumeWWN(storage, path.getDevNum());
                volumesFromHSD.put(volumeWWN, Integer.valueOf(path.getLun()));
            }
        }
        return volumesFromHSD;
    }

    /**
     * Generates the HiCommand Server URI.
     * 
     * @param system
     * @return
     */
    private URI getHDSServerManagementServerInfo(StorageSystem system) {
        String protocol;
        String providerIP;
        int port;
        if (Boolean.TRUE.equals(system.getSmisUseSSL())) {
            protocol = HDSConstants.HTTPS_URL;
        } else {
            protocol = HDSConstants.HTTP_URL;
        }
        providerIP = system.getSmisProviderIP();
        port = system.getSmisPortNumber();
        URI uri = URI.create(String.format("%1$s://%2$s:%3$d/service/StorageManager",
                protocol, providerIP, port));
        log.info("HiCommand DM server url to query: {}", uri);
        return uri;
    }

    @Override
    public void updateStorageGroupPolicyAndLimits(StorageSystem storage,
            ExportMask exportMask, List<URI> volumeURIs, VirtualPool newVirtualPool,
            boolean rollback, TaskCompleter taskCompleter) throws Exception {
        String message = rollback ? ("updateAutoTieringPolicy" + "(rollback)")
                : ("updateAutoTieringPolicy");
        log.info("{} {} START...", storage.getSerialNumber(), message);
        log.info("{} : volumeURIs: {}", message, volumeURIs);
        try {
            String newPolicyName = ControllerUtils.getFastPolicyNameFromVirtualPool(dbClient, storage, newVirtualPool);
            log.info("{} : AutoTieringPolicy: {}", message, newPolicyName);

            List<Volume> volumes = dbClient.queryObject(Volume.class,
                    volumeURIs);
            HDSApiClient hdsApiClient = hdsApiFactory.getClient(
                    HDSUtils.getHDSServerManagementServerInfo(storage),
                    storage.getSmisUserName(), storage.getSmisPassword());
            String systemObjectID = HDSUtils.getSystemObjectID(storage);
            for (Volume volume : volumes) {
                String luObjectId = HDSUtils.getLogicalUnitObjectId(volume.getNativeId(), storage);

                LogicalUnit logicalUnit = hdsApiClient.getLogicalUnitInfo(systemObjectID, luObjectId);
                if (null != logicalUnit && null != logicalUnit.getLdevList() && !logicalUnit.getLdevList().isEmpty()) {
                    Iterator<LDEV> ldevItr = logicalUnit.getLdevList().iterator();
                    if (ldevItr.hasNext()) {
                        LDEV ldev = ldevItr.next();
                        hdsApiClient.getLogicalUnitInfo(systemObjectID, luObjectId);
                        String tieringPolicyName = ControllerUtils.getAutoTieringPolicyName(volume.getId(), dbClient);
                        String policId = HitachiTieringPolicy.getPolicy(
                                tieringPolicyName.replaceAll(HDSConstants.SLASH_OPERATOR,
                                        HDSConstants.UNDERSCORE_OPERATOR)).getKey();
                        String asyncMessageId = hdsApiClient.modifyThinVolumeTieringPolicy(systemObjectID, luObjectId,
                                ldev.getObjectID(), policId, storage.getModel());
                        if (null != asyncMessageId) {
                            HDSJob modifyHDSJob = new HDSModifyVolumeJob(asyncMessageId, volume.getStorageController(),
                                    taskCompleter, HDSModifyVolumeJob.VOLUME_VPOOL_CHANGE_JOB);
                            ControllerServiceImpl.enqueueJob(new QueueJob(modifyHDSJob));
                        }
                    }
                }
            }
        } catch (Exception e) {
            String errMsg = String
                    .format("An error occurred while updating Auto-tiering policy for Volumes %s",
                            volumeURIs);
            log.error(errMsg, e);
            ServiceError serviceError = DeviceControllerException.errors
                    .jobFailedMsg(errMsg, e);
            taskCompleter.error(dbClient, serviceError);
        }

        log.info("{} {} updateAutoTieringPolicy END...", storage.getSerialNumber(), message);
    }

    @Override
    public Map<URI, Integer> getExportMaskHLUs(StorageSystem storage, ExportMask exportMask) {
        return Collections.emptyMap();
    }
}
