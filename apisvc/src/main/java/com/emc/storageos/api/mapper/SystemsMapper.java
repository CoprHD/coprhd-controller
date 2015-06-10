/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.api.mapper;

import static com.emc.storageos.api.mapper.DbObjectMapper.mapDataObjectFields;
import static com.emc.storageos.api.mapper.DbObjectMapper.mapDiscoveredDataObjectFields;
import static com.emc.storageos.api.mapper.DbObjectMapper.mapDiscoveredSystemObjectFields;
import static com.emc.storageos.api.mapper.DbObjectMapper.toRelatedResource;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Map;

import com.emc.storageos.api.service.impl.resource.utils.CapacityUtils;
import com.emc.storageos.api.service.impl.response.RestLinkFactory;
import com.emc.storageos.coordinator.client.service.CoordinatorClient;
import com.emc.storageos.db.client.model.DecommissionedResource;
import com.emc.storageos.db.client.model.StoragePool;
import com.emc.storageos.db.client.model.StoragePort;
import com.emc.storageos.db.client.model.StorageProvider;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.model.ResourceTypeEnum;
import com.emc.storageos.model.RestLinkRep;
import com.emc.storageos.model.adapters.StringMapAdapter;
import com.emc.storageos.model.pools.StoragePoolRestRep;
import com.emc.storageos.model.ports.StoragePortRestRep;
import com.emc.storageos.model.smis.SMISProviderRestRep;
import com.emc.storageos.model.smis.StorageProviderRestRep;
import com.emc.storageos.model.systems.StorageSystemRestRep;
import com.emc.storageos.model.varray.DecommissionedResourceRep;
import com.emc.storageos.volumecontroller.impl.ControllerUtils;
import com.emc.storageos.volumecontroller.impl.plugins.metering.smis.processor.MetricsKeys;
import com.emc.storageos.volumecontroller.impl.utils.attrmatchers.CapacityMatcher;

public class SystemsMapper {
	 private static final String MINUS_ONE_LONG = "-1";
	 
	@Deprecated
    public static SMISProviderRestRep mapStorageProviderToSMISRep(StorageProvider from) {
        if (from == null) {
            return null;
        }
        SMISProviderRestRep to = new SMISProviderRestRep();
        mapDataObjectFields(from, to);
        // Workaround to generate /vdc/smis-providers uri for self link instead of type base URI generation.
        try {
			to.setLink(new RestLinkRep("self", RestLinkFactory.simpleServiceLink(ResourceTypeEnum.SMIS_PROVIDER, from.getId())));
		} catch (URISyntaxException e) {
			//impossible to get exception here.
		}
        to.setIPAddress(from.getIPAddress());
        to.setPortNumber(from.getPortNumber());
        if (from.getStorageSystems() != null) {
            for (String system: from.getStorageSystems()) {
                to.getStorageSystems().add(toRelatedResource(ResourceTypeEnum.STORAGE_SYSTEM, URI.create(system)));
            }
        }
        to.setDescription(from.getDescription());
        to.setManufacturer(from.getManufacturer());
        to.setVersionString(from.getVersionString());
        to.setProviderID(from.getProviderID());
        to.setConnectionStatus(from.getConnectionStatus());
        to.setUserName(from.getUserName());
        to.setUseSSL(from.getUseSSL());
        to.setScanStatus(from.getScanStatus());
        to.setLastScanStatusMessage(from.getLastScanStatusMessage());
        to.setLastScanTime(from.getLastScanTime());
        to.setNextScanTime(from.getNextScanTime());
        to.setSuccessScanTime(from.getSuccessScanTime());
        to.setCompatibilityStatus(from.getCompatibilityStatus());
        to.setRegistrationStatus(from.getRegistrationStatus());
        return to;
    }

    public static StorageProviderRestRep map(StorageProvider from) {
        if (from == null) {
            return null;
        }
        StorageProviderRestRep to = new StorageProviderRestRep();
        mapDataObjectFields(from, to);
        to.setIPAddress(from.getIPAddress());
        to.setPortNumber(from.getPortNumber());
        if (from.getStorageSystems() != null) {
            for (String system: from.getStorageSystems()) {
                to.getStorageSystems().add(toRelatedResource(ResourceTypeEnum.STORAGE_SYSTEM, URI.create(system)));
            }
        }
        to.setInterface(from.getInterfaceType());
        to.setDescription(from.getDescription());
        to.setManufacturer(from.getManufacturer());
        to.setVersionString(from.getVersionString());
        to.setProviderID(from.getProviderID());
        to.setConnectionStatus(from.getConnectionStatus());
        to.setUserName(from.getUserName());
        to.setUseSSL(from.getUseSSL());
        to.setScanStatus(from.getScanStatus());
        to.setLastScanStatusMessage(from.getLastScanStatusMessage());
        to.setLastScanTime(from.getLastScanTime());
        to.setNextScanTime(from.getNextScanTime());
        to.setSuccessScanTime(from.getSuccessScanTime());
        to.setCompatibilityStatus(from.getCompatibilityStatus());
        to.setRegistrationStatus(from.getRegistrationStatus());
        to.setSecondaryUsername(from.getSecondaryUsername());
        to.setElementManagerURL(from.getElementManagerURL());
        return to;
    }

    public static StoragePoolRestRep map(StoragePool from, Map<String, Long> capacityMetrics,
            boolean isBlockStoragePool, CoordinatorClient coordinatorClient) {
        if (from == null) {
            return null;
        }
        StoragePoolRestRep to = new StoragePoolRestRep();
        mapDiscoveredDataObjectFields(from, to);
        to.setProtocols(from.getProtocols());
        to.setControllerParams(new StringMapAdapter().marshal(from.getControllerParams()));
        to.setOperationalStatus(from.getOperationalStatus());
        to.setTotalCapacity(capacityMetrics.get(CapacityUtils.StorageMetrics.USABLE.toString()));
        to.setFreeCapacity(capacityMetrics.get(CapacityUtils.StorageMetrics.FREE.toString()));
        to.setUsedCapacity(capacityMetrics.get(CapacityUtils.StorageMetrics.USED.toString()));
		to.setPercentUsed(capacityMetrics
				.get(CapacityUtils.StorageMetrics.PERCENT_USED.toString()));
		if ((null != capacityMetrics
				.get(CapacityUtils.StorageMetrics.SUBSCRIBED.toString()) && 
				!(capacityMetrics.get(CapacityUtils.StorageMetrics.SUBSCRIBED.toString()).toString().equals(MINUS_ONE_LONG)))) {

			to.setSubscribedCapacity(capacityMetrics
					.get(CapacityUtils.StorageMetrics.SUBSCRIBED.toString()));
			to.setPercentSubscribed(capacityMetrics
					.get(CapacityUtils.StorageMetrics.PERCENT_SUBSCRIBED
							.toString()));
		}
        to.setMaximumThinVolumeSize(CapacityUtils.convertKBToGB(from.getMaximumThinVolumeSize()));
        to.setMinimumThinVolumeSize(CapacityUtils.convertKBToGB(from.getMinimumThinVolumeSize()));
        to.setMaximumThickVolumeSize(CapacityUtils.convertKBToGB(from.getMaximumThickVolumeSize()));
        to.setMinimumThickVolumeSize(CapacityUtils.convertKBToGB(from.getMinimumThickVolumeSize()));
        to.setMaxResources(from.getMaxResources());
        to.setAssignedVirtualArrays(from.getAssignedVirtualArrays());
        to.setConnectedVirtualArrays(from.getConnectedVirtualArrays());
        to.setTaggedVirtualArrays(from.getTaggedVirtualArrays());
        to.setRaidLevels(from.getSupportedRaidLevels());
        to.setThinVolumePreAllocationSupported(from.getThinVolumePreAllocationSupported());
        to.setAutoTieringSupported(from.getAutoTieringEnabled());
        to.setDriveTypes(from.getSupportedDriveTypes());
        to.setCopyTypes(from.getSupportedCopyTypes());
        to.setTierUtilizationPercentage(new StringMapAdapter().marshal(from.getTierUtilizationPercentage()));
        to.setPoolName(from.getPoolName());
        to.setPoolServiceType(from.getPoolServiceType());
        to.setLongTermRetention(from.getLongTermRetention());
        to.setSupportedResourceTypes(from.getSupportedResourceTypes());
        to.setStorageSystem(toRelatedResource(ResourceTypeEnum.STORAGE_SYSTEM, from.getStorageDevice()));
        to.setRegistrationStatus(from.getRegistrationStatus());
        to.setCompatibilityStatus(from.getCompatibilityStatus());
        to.setDiscoveryStatus(from.getDiscoveryStatus());
        to.setMaxPoolUtilizationPercentage((from.getMaxPoolUtilizationPercentage() != null) ? from
                .getMaxPoolUtilizationPercentage() : Integer.valueOf(ControllerUtils.
                        getPropertyValueFromCoordinator(coordinatorClient, CapacityMatcher.MAX_POOL_UTILIZATION_PERCENTAGE)));
        
        if (null != from.getSupportedResourceTypes() && 
                !from.getSupportedResourceTypes().equals(StoragePool.SupportedResourceTypes.THICK_ONLY)) {
            
            to.setMaxThinPoolSubscriptionPercentage((from.getMaxThinPoolSubscriptionPercentage() != null) ? from
                    .getMaxThinPoolSubscriptionPercentage() : Integer.valueOf(ControllerUtils.
                            getPropertyValueFromCoordinator(coordinatorClient, CapacityMatcher.MAX_THIN_POOL_SUBSCRIPTION_PERCENTAGE)));
        }
        return to;
    }

    public static StoragePortRestRep map(StoragePort from) {
        if (from == null) {
            return null;
        }
        StoragePortRestRep to = new StoragePortRestRep();
        mapDiscoveredDataObjectFields(from, to);
        to.setPortName(from.getPortName());
        to.setIpAddress(from.getIpAddress());
        to.setTcpPortNumber(from.getTcpPortNumber());
        to.setPortNetworkId(from.getPortNetworkId());
        to.setPortEndPointId(from.getPortEndPointID());
        to.setTransportType(from.getTransportType());
        to.setNetwork(toRelatedResource(ResourceTypeEnum.NETWORK, from.getNetwork()));
        to.setStorageDevice(toRelatedResource(ResourceTypeEnum.STORAGE_SYSTEM, from.getStorageDevice()));
        to.setPortSpeed(from.getPortSpeed());
        to.setPortType(from.getPortType());
        to.setPortGroup(from.getPortGroup());
        to.setAvgBandwidth(from.getAvgBandwidth());
        to.setStaticLoad(from.getStaticLoad());
        to.setRegistrationStatus(from.getRegistrationStatus());
        to.setCompatibilityStatus(from.getCompatibilityStatus());
        to.setOperationalStatus(from.getOperationalStatus());
        to.setAssignedVirtualArrays(from.getAssignedVirtualArrays());
        to.setConnectedVirtualArrays(from.getConnectedVirtualArrays());
        to.setTaggedVirtualArrays(from.getTaggedVirtualArrays());
        to.setDiscoveryStatus(from.getDiscoveryStatus());
        
        // Port metrics.
        Double percentBusy = MetricsKeys.getDoubleOrNull(MetricsKeys.avgPortPercentBusy, from.getMetrics());
        if (percentBusy != null) {
            to.setPortPercentBusy(percentBusy);
        }
        percentBusy = MetricsKeys.getDoubleOrNull(MetricsKeys.avgCpuPercentBusy, from.getMetrics());
        if (percentBusy != null) {
            to.setCpuPercentBusy(percentBusy);
        }
        to.setAllocationMetric(MetricsKeys.getDouble(MetricsKeys.portMetric, from.getMetrics()));
        
        to.setVolumeLoad(MetricsKeys.getLong(MetricsKeys.volumeCount, from.getMetrics()));
        to.setInitiatorLoad(MetricsKeys.getLong(MetricsKeys.initiatorCount, from.getMetrics()));
        
        to.setAllocationDisqualified(MetricsKeys.getBoolean(MetricsKeys.allocationDisqualified, from.getMetrics()));
        return to;
    }
    public static StorageSystemRestRep map(StorageSystem from) {
        if (from == null) {
            return null;
        }
        StorageSystemRestRep to = new StorageSystemRestRep();
        mapDiscoveredSystemObjectFields(from, to);
        to.setSerialNumber(from.getSerialNumber());
        to.setMajorVersion(from.getMajorVersion());
        to.setMinorVersion(from.getMinorVersion());
        to.setIpAddress(from.getIpAddress());
        to.setSecondaryIPs(from.getSecondaryIPs());
        to.setPortNumber(from.getPortNumber());
        to.setSmisProviderIP(from.getSmisProviderIP());
        to.setSmisPortNumber(from.getSmisPortNumber());
        to.setSmisUseSSL(from.getSmisUseSSL());
        to.setSmisUserName(from.getSmisUserName());
        to.setExportMasks(new StringMapAdapter().marshal(from.getExportMasks()));
        to.setProtocols(from.getProtocols());
        to.setReachableStatus(from.getReachableStatus());
        to.setFirmwareVersion(from.getFirmwareVersion());
        to.setActiveProvider(toRelatedResource(ResourceTypeEnum.SMIS_PROVIDER, from.getActiveProviderURI()));
        if (from.getProviders() != null) {
            for (String provider: from.getProviders()) {
                to.getProviders().add(toRelatedResource(ResourceTypeEnum.SMIS_PROVIDER, URI.create(provider)));
            }
        }
        to.setUsername(from.getUsername());
        to.setModel(from.getModel());
        to.setSupportedProvisioningType(from.getSupportedProvisioningType());
        to.setSupportedAsynchronousActions(from.getSupportedAsynchronousActions());
        to.setMaxResources(from.getMaxResources());
        to.setRemotelyConnectedTo(from.getRemotelyConnectedTo());
        to.setSupportedReplicationTypes(from.getSupportedReplicationTypes());
        to.setAveragePortMetrics(from.getAveragePortMetrics());
       
        return to;
    }

    public static DecommissionedResourceRep map(DecommissionedResource from) {
        if (from == null) {
            return null;
        }
        DecommissionedResourceRep to = new DecommissionedResourceRep();
        to.setDecommissionedId(from.getDecommissionedId().toString());
        to.setNativeGuid(from.getNativeGuid());
        to.setType(from.getType());
        to.setUser(from.getUser());
        return to;
    }
}
