package com.emc.storageos.util;

import java.net.URI;
import java.util.Iterator;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.URIUtil;
import com.emc.storageos.db.client.model.QosSpecification;
import com.emc.storageos.db.client.model.StringMap;
import com.emc.storageos.db.client.model.VirtualPool;
import com.emc.storageos.svcs.errorhandling.resources.APIException;
import com.emc.storageos.volumecontroller.impl.utils.VirtualPoolCapabilityValuesWrapper;

public class CinderQosUtil {
    private static final Logger _log = LoggerFactory.getLogger(CinderQosUtil.class);
    public static final String EVENT_SERVICE_TYPE = "block";
    // QoS recordOperation labels
    public static final String QOS_CREATED_DESCRIPTION = "Quality of Service Created";
    public static final String QOS_UPDATED_DESCRIPTION = "Quality of Service Updated";
    public static final String QOS_DELETED_DESCRIPTION = "Quality of Service Deleted";

    public static final String QOS_CONSUMER = "back-end";
    public static final String QOS_NAME = "specs-"; // with appended Virtual Pool label
    // QoS spec labels
    public static final String SPEC_PROVISIONING_TYPE = "Provisioning Type";
    public static final String SPEC_PROTOCOL = "Protocol";
    public static final String SPEC_DRIVE_TYPE = "Drive Type";
    public static final String SPEC_SYSTEM_TYPE = "System Type";
    public static final String SPEC_MULTI_VOL_CONSISTENCY = "Multi-Volume Consistency";
    public static final String SPEC_RAID_LEVEL = "RAID Level";
    public static final String SPEC_EXPENDABLE = "Expendable";
    public static final String SPEC_MAX_SAN_PATHS = "Maximum SAN paths";
    public static final String SPEC_MIN_SAN_PATHS = "Minimum SAN paths";
    public static final String SPEC_MAX_BLOCK_MIRRORS = "Maximum block mirrors";
    public static final String SPEC_PATHS_PER_INITIATOR = "Paths per Initiator";
    public static final String SPEC_HIGH_AVAILABILITY = "High Availability";
    public static final String SPEC_MAX_SNAPSHOTS = "Maximum Snapshots";

    public static final String LABEL_DISABLED_SNAPSHOTS = "disabled";
    public static final String LABEL_UNLIMITED_SNAPSHOTS = "unlimited";
    public static final String LABEL_RAID_LEVEL = "raid_level";

    public static final Integer UNLIMITED_SNAPSHOTS = -1;
    public static final Integer DISABLED_SNAPSHOTS = 0;
    
    /**
     * Gives systemType of the given vPool
     * 
     * @param virtualPool
     * @return {@link String} vpool's systemType
     */
    private static String getSystemType(VirtualPool virtualPool) {
        String systemType = null;

        if (virtualPool != null && virtualPool.getArrayInfo() != null && virtualPool.getArrayInfo().containsKey(VirtualPoolCapabilityValuesWrapper.SYSTEM_TYPE)) {
            for (String sysType : virtualPool.getArrayInfo().get(VirtualPoolCapabilityValuesWrapper.SYSTEM_TYPE)) {
                systemType = sysType;
                break;
            }
        }

        return systemType;
    }


    /**
     * Retrieves information from given Virtual Pool, creates and persist Qos object to the DB
     *
     * @param virtualPool Virtual Pool
     * @return QosSpecification filled with information from Virtual Pool
     */
    public static QosSpecification createQosSpecification(VirtualPool virtualPool, DbClient dbClient) {
        _log.debug("Fetching data from Virtual Pool, id: {}", virtualPool.getId());
        QosSpecification qosSpecification = new QosSpecification();
        StringMap specs = new StringMap();
        String protocols = null;
        if (virtualPool.getProtocols() != null) {
            protocols = virtualPool.getProtocols().toString();
        }
        qosSpecification.setName(QOS_NAME + virtualPool.getLabel());
        qosSpecification.setConsumer(QOS_CONSUMER);
        qosSpecification.setLabel(virtualPool.getLabel());
        qosSpecification.setId(URIUtil.createId(QosSpecification.class));
        qosSpecification.setVirtualPoolId(virtualPool.getId());
        if (virtualPool.getSupportedProvisioningType() != null) {
            specs.put(SPEC_PROVISIONING_TYPE, virtualPool.getSupportedProvisioningType());
        }
        if (protocols != null) {
            specs.put(SPEC_PROTOCOL, protocols.substring(1, protocols.length() - 1));
        }
        if (virtualPool.getDriveType() != null) {
            specs.put(SPEC_DRIVE_TYPE, virtualPool.getDriveType());
        }
        if (getSystemType(virtualPool) != null) {
            specs.put(SPEC_SYSTEM_TYPE, getSystemType(virtualPool));
        }
        if (virtualPool.getMultivolumeConsistency() != null) {
            specs.put(SPEC_MULTI_VOL_CONSISTENCY, Boolean.toString(virtualPool.getMultivolumeConsistency()));
        }
        if (virtualPool.getArrayInfo() != null && virtualPool.getArrayInfo().get(LABEL_RAID_LEVEL) != null) {
            specs.put(SPEC_RAID_LEVEL, virtualPool.getArrayInfo().get(LABEL_RAID_LEVEL).toString());
        }
        if (virtualPool.getExpandable() != null) {
            specs.put(SPEC_EXPENDABLE, Boolean.toString(virtualPool.getExpandable()));
        }
        if (virtualPool.getNumPaths() != null) {
            specs.put(SPEC_MAX_SAN_PATHS, Integer.toString(virtualPool.getNumPaths()));
        }
        if (virtualPool.getMinPaths() != null) {
            specs.put(SPEC_MIN_SAN_PATHS, Integer.toString(virtualPool.getMinPaths()));
        }
        if (virtualPool.getMaxNativeContinuousCopies() != null) {
            specs.put(SPEC_MAX_BLOCK_MIRRORS, Integer.toString(virtualPool.getMaxNativeContinuousCopies()));
        }
        if (virtualPool.getPathsPerInitiator() != null) {
            specs.put(SPEC_PATHS_PER_INITIATOR, Integer.toString(virtualPool.getPathsPerInitiator()));
        }
        if (virtualPool.getHighAvailability() != null) {
            specs.put(SPEC_HIGH_AVAILABILITY, virtualPool.getHighAvailability());
        }
        if (virtualPool.getMaxNativeSnapshots() != null) {
            if (virtualPool.getMaxNativeSnapshots().equals(UNLIMITED_SNAPSHOTS)) {
                specs.put(SPEC_MAX_SNAPSHOTS, LABEL_UNLIMITED_SNAPSHOTS);
            } else if (virtualPool.getMaxNativeSnapshots().equals(DISABLED_SNAPSHOTS)) {
                specs.put(SPEC_MAX_SNAPSHOTS, LABEL_DISABLED_SNAPSHOTS);
            } else {
                specs.put(SPEC_MAX_SNAPSHOTS, Integer.toString(virtualPool.getMaxNativeSnapshots()));
            }
        }

        qosSpecification.setSpecs(specs);

        // Create new QoS in the DB
        dbClient.createObject(qosSpecification);

        return qosSpecification;
    }

    /**
     * Get QoS specification associated with provided VirtualPool.
     *
     * @param vpoolId the VirtualPool for which QoS specification is required.
     */
    public static QosSpecification getQos(URI vpoolId, DbClient dbClient) throws APIException {
        List<URI> qosSpecsURI = dbClient.queryByType(QosSpecification.class, true);
        Iterator<QosSpecification> qosIter = dbClient.queryIterativeObjects(QosSpecification.class, qosSpecsURI);
        while (qosIter.hasNext()) {
            QosSpecification activeQos = qosIter.next();
            if(activeQos != null && activeQos.getVirtualPoolId().equals(vpoolId)){
                _log.debug("Qos Specification {} assigned to Virtual Pool {} found", activeQos.getId(), vpoolId);
                return activeQos;
            }
        }
        throw APIException.internalServerErrors.noAssociatedQosForVirtualPool(vpoolId);
    }
    
    /**
     * Creates or updates the QoS specification as neeeded, persisting when done
     * @param virtualPool
     * @param dbClient
     * @return
     */
    public static QosSpecification createOrUpdateQos(VirtualPool virtualPool, DbClient dbClient) {
        List<URI> qosSpecsURI = dbClient.queryByType(QosSpecification.class, true);
        Iterator<QosSpecification> qosIter = dbClient.queryIterativeObjects(QosSpecification.class, qosSpecsURI);
        while (qosIter.hasNext()) {
            QosSpecification activeQos = qosIter.next();
            if(activeQos != null && activeQos.getVirtualPoolId().equals(virtualPool.getId())){
                _log.debug("Qos Specification {} assigned to Virtual Pool {} found", activeQos.getId(), virtualPool.getId());
                return updateQos(virtualPool, activeQos, dbClient);
            }
        }
        return createQosSpecification(virtualPool, dbClient);
    }

    /**
     * Update QoS specification associated with provided VirtualPool.
     *
     * @param virtualPool the VirtualPool object with updated data.
     */
    public static QosSpecification updateQos(VirtualPool virtualPool, QosSpecification qosSpecification, DbClient dbClient) {
        _log.debug("Updating Qos Specification, id: " + qosSpecification.getId());
        StringMap specs = qosSpecification.getSpecs();
        String protocols = virtualPool.getProtocols().toString();
        if (!qosSpecification.getLabel().equals(virtualPool.getLabel())) {
            qosSpecification.setLabel(virtualPool.getLabel());
        }
        if (!qosSpecification.getName().equals(QOS_NAME + virtualPool.getLabel())) {
            qosSpecification.setName(QOS_NAME + virtualPool.getLabel());
        }
        if (virtualPool.getSupportedProvisioningType() != null) {
            specs.put(SPEC_PROVISIONING_TYPE, virtualPool.getSupportedProvisioningType());
        }
        if (protocols != null) {
            specs.put(SPEC_PROTOCOL, protocols.substring(1, protocols.length() - 1));
        }
        if (virtualPool.getDriveType() != null) {
            specs.put(SPEC_DRIVE_TYPE, virtualPool.getDriveType());
        }
        if (getSystemType(virtualPool) != null) {
            specs.put(SPEC_SYSTEM_TYPE, getSystemType(virtualPool));
        }
        if (virtualPool.getMultivolumeConsistency() != null) {
            specs.put(SPEC_MULTI_VOL_CONSISTENCY, Boolean.toString(virtualPool.getMultivolumeConsistency()));
        }
        if (virtualPool.getArrayInfo() != null && virtualPool.getArrayInfo().get(LABEL_RAID_LEVEL) != null) {
            specs.put(SPEC_RAID_LEVEL, virtualPool.getArrayInfo().get(LABEL_RAID_LEVEL).toString());
        }
        if (virtualPool.getExpandable() != null) {
            specs.put(SPEC_EXPENDABLE, Boolean.toString(virtualPool.getExpandable()));
        }
        if (virtualPool.getNumPaths() != null) {
            specs.put(SPEC_MAX_SAN_PATHS, Integer.toString(virtualPool.getNumPaths()));
        }
        if (virtualPool.getMinPaths() != null) {
            specs.put(SPEC_MIN_SAN_PATHS, Integer.toString(virtualPool.getMinPaths()));
        }
        if (virtualPool.getMaxNativeContinuousCopies() != null) {
            specs.put(SPEC_MAX_BLOCK_MIRRORS, Integer.toString(virtualPool.getMaxNativeContinuousCopies()));
        }
        if (virtualPool.getPathsPerInitiator() != null) {
            specs.put(SPEC_PATHS_PER_INITIATOR, Integer.toString(virtualPool.getPathsPerInitiator()));
        }
        if (virtualPool.getHighAvailability() != null) {
            specs.put(SPEC_HIGH_AVAILABILITY, virtualPool.getHighAvailability());
        }
        if (virtualPool.getMaxNativeSnapshots() != null) {
            if (virtualPool.getMaxNativeSnapshots().equals(UNLIMITED_SNAPSHOTS)) {
                specs.put(SPEC_MAX_SNAPSHOTS, LABEL_UNLIMITED_SNAPSHOTS);
            } else if (virtualPool.getMaxNativeSnapshots().equals(DISABLED_SNAPSHOTS)) {
                specs.put(SPEC_MAX_SNAPSHOTS, LABEL_DISABLED_SNAPSHOTS);
            } else {
                specs.put(SPEC_MAX_SNAPSHOTS, Integer.toString(virtualPool.getMaxNativeSnapshots()));
            }
        }

        dbClient.updateObject(qosSpecification);

        return qosSpecification;
    }
}
