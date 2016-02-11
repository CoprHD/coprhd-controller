/*
 * Copyright (c) 2008-2013 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl;

import static com.emc.storageos.db.client.constraint.AlternateIdConstraint.Factory.getBlockSnapshotSessionBySessionInstance;
import static com.google.common.collect.Lists.newArrayList;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.coordinator.client.service.CoordinatorClient;
import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.URIUtil;
import com.emc.storageos.db.client.constraint.AlternateIdConstraint;
import com.emc.storageos.db.client.constraint.ContainmentConstraint;
import com.emc.storageos.db.client.constraint.URIQueryResultList;
import com.emc.storageos.db.client.model.AutoTieringPolicy;
import com.emc.storageos.db.client.model.BlockConsistencyGroup;
import com.emc.storageos.db.client.model.BlockMirror;
import com.emc.storageos.db.client.model.BlockObject;
import com.emc.storageos.db.client.model.BlockSnapshot;
import com.emc.storageos.db.client.model.BlockSnapshotSession;
import com.emc.storageos.db.client.model.DataObject;
import com.emc.storageos.db.client.model.DataObject.Flag;
import com.emc.storageos.db.client.model.DiscoveredDataObject;
import com.emc.storageos.db.client.model.DiscoveredDataObject.Type;
import com.emc.storageos.db.client.model.Event;
import com.emc.storageos.db.client.model.ExportGroup;
import com.emc.storageos.db.client.model.FCZoneReference;
import com.emc.storageos.db.client.model.FileShare;
import com.emc.storageos.db.client.model.Network;
import com.emc.storageos.db.client.model.OpStatusMap;
import com.emc.storageos.db.client.model.Operation;
import com.emc.storageos.db.client.model.Project;
import com.emc.storageos.db.client.model.StoragePool;
import com.emc.storageos.db.client.model.StoragePort;
import com.emc.storageos.db.client.model.StorageProvider;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.model.StringSet;
import com.emc.storageos.db.client.model.TenantOrg;
import com.emc.storageos.db.client.model.VirtualPool;
import com.emc.storageos.db.client.model.Volume;
import com.emc.storageos.db.client.model.VolumeGroup;
import com.emc.storageos.db.client.model.VplexMirror;
import com.emc.storageos.db.client.util.CustomQueryUtility;
import com.emc.storageos.db.client.util.NullColumnValueGetter;
import com.emc.storageos.db.exceptions.DatabaseException;
import com.emc.storageos.plugins.common.Constants;
import com.emc.storageos.volumecontroller.TaskCompleter;
import com.emc.storageos.volumecontroller.impl.monitoring.RecordableBourneEvent;
import com.emc.storageos.volumecontroller.impl.monitoring.RecordableEvent;
import com.emc.storageos.volumecontroller.impl.smis.SmisConstants;
import com.emc.storageos.volumecontroller.impl.utils.ConsistencyUtils;
import com.emc.storageos.volumecontroller.impl.utils.VirtualPoolCapabilityValuesWrapper;
import com.emc.storageos.volumecontroller.logging.BournePatternConverter;
import com.google.common.base.Joiner;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ListMultimap;

/**
 * Utilities class encapsulates controller utility methods.
 */
public class ControllerUtils {

    private static final String SMI81_VERSION_STARTING_STR = "V8.1";

    // Logger reference.
    private static final Logger s_logger = LoggerFactory.getLogger(ControllerUtils.class);

    // Constant that represents BLOCK_EVENT_SOURCE
    public static final String BLOCK_EVENT_SOURCE = "Block Controller";

    // Constant that represents BLOCK_EVENT_SERVICE
    public static final String BLOCK_EVENT_SERVICE = "block";

    private static final String KILOBYTECONVERTERVALUE = "1024";

    private static final VolumeURIHLU[] EMPTY_VOLUME_URI_HLU_ARRAY = new VolumeURIHLU[0];

    private static final String LABEL_DELIMITER = "-";

    /**
     * Gets the URI of the tenant organization for the project with the passed
     * URI.
     * 
     * @param dbClient A reference to the database client.
     * @param projectURI The URI for the project.
     * 
     * @return The URI of the tenant organization.
     */
    public static URI getProjectTenantOrgURI(DbClient dbClient, URI projectURI) {
        URI tenantOrgURI = null;
        try {
            s_logger.debug("Getting the URI of the tenant for project {}.", projectURI);

            // Get the Project with the passed URI from the database and extract
            // the tenant organization for the project.
            Project project = dbClient.queryObject(Project.class, projectURI);
            if (project != null) {
                tenantOrgURI = project.getTenantOrg().getURI();
                if (tenantOrgURI == null) {
                    s_logger.warn("The tenant URI is null for project {}.", projectURI);
                }
            } else {
                s_logger.warn("The database returned a null project for URI {}.", projectURI);
            }
        } catch (Exception e) {
            s_logger.warn("Exception fetching project {} from the database.", projectURI, e);
        }

        // Use the default provider tenant if the tenant cannot be determined.
        if (tenantOrgURI == null) {
            tenantOrgURI = URI.create(TenantOrg.PROVIDER_TENANT_ORG);
        }

        s_logger.debug("Returning tenant {} for project {}.", new Object[] { tenantOrgURI, projectURI });

        return tenantOrgURI;
    }

    /**
     * Sets data to be included in log messages while an operation is executed.
     * The data to be included in the log message is the id of the operation and
     * the id of the resource impacted by the operation.
     * 
     * @param resourceId The urn of the resource impacted by the operation.
     * @param opId The identifier for the operation being executed.
     */
    public static void setThreadLocalLogData(URI resourceId, String opId) {
        ArrayList<String> patternData = new ArrayList<String>();
        if ((opId != null) && (opId.length() != 0)) {
            patternData.add(opId);
        }
        if (resourceId != null) {
            patternData.add(resourceId.toString());
        }
        BournePatternConverter.s_patternData.set(patternData);
    }

    /**
     * Clears the data to be included in log messages. Typically called after
     * the operation has completed.
     */
    public static void clearThreadLocalLogData() {
        BournePatternConverter.s_patternData.set(new ArrayList<String>());
    }

    /**
     * This function looks first at the logical pools and updates them with physical
     * capacity information, then updates the physical pools.
     * If physical pools are removed from the storage system it marks them inactive.
     * 
     * @param storage
     * @param physicalHardware
     * @return
     * 
     *         public static
     *         boolean reconcilePhysicalHardware(URI storage,
     *         List<Object> physicalHardware,
     *         DbClient dbClient) {
     *         Logger log = LoggerFactory.getLogger(ControllerUtils.class);
     *         try {
     * 
     *         // First update the logical pools represented by the physical pool
     * 
     *         List<URI> poolURIs = dbClient.queryByConstraint
     *         (ContainmentConstraint.Factory
     *         .getStorageDeviceStoragePoolConstraint(storage));
     *         List<StoragePool> pools = dbClient.queryObject(StoragePool
     *         .class, poolURIs);
     *         boolean poolFound;
     *         for(StoragePool pool : pools){
     *         poolFound = false;
     *         for(Object obj : physicalHardware){
     *         if (obj instanceof PhysicalStoragePool) {
     *         // the type and ID must match
     *         PhysicalStoragePool psp = (PhysicalStoragePool) obj;
     *         if (pool.getControllerParams().get(StoragePool.ControllerParam.NativeId.name()).equals(psp.getNativeId())&&
     *         pool.getControllerParams().get(StoragePool.ControllerParam.PoolType.name()).equals(psp.getType())) {
     *         pool.setFreeCapacity(psp.getFreeCapacity());
     *         pool.setTotalCapacity(psp.getTotalCapacity());
     *         pool.setLargestContiguousBlock(psp
     *         .getLargestContiguousBlock());
     *         pool.setSubscribedCapacity(psp.getSubscribedCapacity());
     *         log.info(String.format("Logical pool %1$s updated by " +
     *         "physical storage pool %2$s/%3$s",
     *         pool.getId().toString(),
     *         psp.getType(), psp.getNativeId()));
     *         dbClient.persistObject(pool);
     *         poolFound = true;
     *         break;
     *         }
     *         }
     *         }
     *         if(poolFound == false){
     *         // probably a good indication this pool is not valid
     *         //pool.setInactive(true);
     *         //dbClient.persistObject(pool);
     *         log.warn(String.format("Logical pool %1$s not found on storage system",
     *         pool.getId().toString()));
     *         }
     *         }
     * 
     *         // Now update the physical pools obtained from controller
     * 
     *         poolURIs = dbClient.queryByConstraint(ContainmentConstraint.Factory
     *         .getStorageDevicePhysicalPoolConstraint(storage));
     *         List<PhysicalStoragePool> physicalPools = dbClient.queryObject(PhysicalStoragePool.class, poolURIs);
     *         Map<URI,PhysicalStoragePool> newPools = new HashMap<URI,PhysicalStoragePool>();
     *         // save the set of physical pools so we can tell if there are new ones
     *         for (Object obj : physicalHardware) {
     *         if (obj instanceof PhysicalStoragePool) {
     *         PhysicalStoragePool psp = (PhysicalStoragePool) obj;
     *         psp.setId(URIUtil.createId(PhysicalStoragePool.class));
     *         psp.setInactive(false);
     *         psp.setStorageDevice(storage);
     *         newPools.put(psp.getId(),psp);
     *         }
     *         }
     *         for (PhysicalStoragePool pool : physicalPools) {
     *         poolFound = false;
     *         for (Object obj : physicalHardware) {
     *         if (obj instanceof PhysicalStoragePool) {
     *         PhysicalStoragePool psp = (PhysicalStoragePool) obj;
     *         // native ID and type must match
     *         if (pool.getNativeId().equals(psp.getNativeId()) &&
     *         pool.getType().equals(psp.getType())) {
     *         newPools.remove(psp.getId());
     *         psp.setId(pool.getId());
     *         log.info(String.format("Updated physical storage pool %1$s/%2$s:%3$s %4$s",
     *         psp.getType(), psp.getNativeId(),
     *         pool.getId().toString(),
     *         pool.getLabel()));
     *         dbClient.persistObject(psp);
     *         poolFound = true;
     *         break;
     *         }
     *         }
     *         }
     *         if(poolFound==false){
     *         // this pool is no longer on array
     *         log.info(String.format("Inactivated Pool %1$s", pool.getId()));
     *         dbClient.markForDeletion(pool);
     *         }
     *         }
     * 
     *         // add new pools
     *         Iterator<Map.Entry<URI,PhysicalStoragePool>> itr = newPools.entrySet().iterator();
     *         while(itr.hasNext()){
     *         Map.Entry<URI, PhysicalStoragePool> entry = itr.next();
     *         PhysicalStoragePool psp = entry.getValue();
     *         log.info(String.format("New physical storage pool %1$s/%2$s:%3$s %4$s",
     *         psp.getType(),psp.getNativeId(),
     *         psp.getId().toString(),
     *         psp.getLabel()));
     *         dbClient.persistObject(psp);
     *         }
     *         return true;
     *         } catch (IOException e) {
     *         log.error("Exception while trying to handle results from " +
     *         "getPhysicalInventory", e);
     *         }
     *         return false;
     *         }
     */

    /**
     * returns if operation (besides opId) is pending
     * 
     * @param id id of resource
     * @param opId operation id for current operation
     * @param resource instance of resource
     * @return
     */
    public static boolean isOperationInProgress(URI id, String opId, DataObject resource) {
        OpStatusMap ops = resource.getOpStatus();
        Set<Map.Entry<String, Operation>> opSet = ops.entrySet();
        Iterator<Map.Entry<String, Operation>> opItr = opSet.iterator();

        while (opItr.hasNext()) {
            Map.Entry<String, Operation> entry = opItr.next();
            if (entry.getValue().getStatus().equals(Operation.Status.pending.toString())) {
                if (entry.getKey().equals(opId)) {
                    // our operation, pass
                    continue;
                }
                //
                // Logger log = LoggerFactory.getLogger(ControllerUtils.class);
                // log.debug("operation in progress");
                //
                return true;
            }
        }
        return false;
    }

    /**
     * Converts a RecordableEvent to an Event Model
     * 
     * @param event
     * @return
     */
    public static Event convertToEvent(RecordableEvent event) {

        Event dbEvent = new Event();

        dbEvent.setTimeInMillis(event.getTimestamp());
        dbEvent.setEventType(event.getType());
        dbEvent.setTenantId(event.getTenantId());
        dbEvent.setProjectId(event.getProjectId());
        dbEvent.setUserId(event.getUserId());
        dbEvent.setVirtualPool(event.getVirtualPool());
        dbEvent.setService(event.getService());
        dbEvent.setResourceId(event.getResourceId());
        dbEvent.setSeverity(event.getSeverity());
        dbEvent.setDescription(event.getDescription());
        dbEvent.setExtensions(event.getExtensions());
        dbEvent.setEventId(event.getEventId());
        dbEvent.setAlertType(event.getAlertType());
        dbEvent.setRecordType(event.getRecordType());
        dbEvent.setNativeGuid(event.getNativeGuid());
        dbEvent.setOperationalStatusCodes(event.getOperationalStatusCodes());
        dbEvent.setOperationalStatusDescriptions(event.getOperationalStatusDescriptions());
        dbEvent.setEventSource(event.getSource());

        return dbEvent;

    }

    /**
     * Create a new instance of RecordableBourneEvent with the given resource
     * and properties.
     * 
     * @param resource
     *            - Type of Resource - File or Volume
     * @param type
     *            - Event Type Enum
     * @param description
     *            - Description of event if available
     * @param extensions
     *            - Extensions mapped with Event Model Extensions
     * @param eventServiceSource
     *            - URI of the Project
     * @param dbClient
     *            - DBClient reference
     * @param evtServiceType
     *            - Service Type
     * @param recordType
     *            - Type of Indication
     * @return RecordableBourneEvent
     */
    public static RecordableBourneEvent convertToRecordableBourneEvent(
            DataObject resource, String type,
            String description, String extensions, DbClient dbClient,
            String evtServiceType, String recordType, String eventServiceSource) {

        URI cos = null;
        URI id = null;
        String nativeGuid = null;
        URI projectURI = null;
        URI tenantURI = null;
        RecordableBourneEvent event = null;

        if (resource != null) {
            if (resource instanceof Volume) {
                Volume volume = (Volume) resource;
                cos = volume.getVirtualPool();
                id = volume.getId();
                nativeGuid = volume.getNativeGuid();
                projectURI = volume.getProject().getURI();
                tenantURI = volume.getTenant().getURI();
            } else if (resource instanceof FileShare) {
                FileShare fs = (FileShare) resource;
                cos = fs.getVirtualPool();
                id = fs.getId();
                nativeGuid = fs.getNativeGuid();
                projectURI = (fs.getProject() != null) ? fs.getProject().getURI() : null;
                tenantURI = (fs.getTenant() != null) ? fs.getTenant().getURI() : null;
            } else if (resource instanceof VplexMirror) {
                VplexMirror vplexMirror = (VplexMirror) resource;
                cos = vplexMirror.getVirtualPool();
                id = vplexMirror.getId();
                projectURI = vplexMirror.getProject().getURI();
                tenantURI = vplexMirror.getTenant().getURI();
            } else if (resource instanceof BlockSnapshot) {
                BlockSnapshot snapshot = (BlockSnapshot) resource;
                try {
                    if (!NullColumnValueGetter.isNullNamedURI(snapshot.getParent())) {
                        Volume volume = dbClient.queryObject(Volume.class, snapshot.getParent());
                        cos = volume.getVirtualPool();
                        tenantURI = (volume.getTenant() != null) ? volume.getTenant().getURI() : null;
                    }
                    id = snapshot.getId();
                    nativeGuid = snapshot.getNativeGuid();
                    projectURI = snapshot.getProject().getURI();
                } catch (DatabaseException e) {
                    s_logger.error("Exception caught", e);
                }
            } else if (resource instanceof BlockSnapshotSession) {
                BlockSnapshotSession session = (BlockSnapshotSession) resource;
                try {
                    id = session.getId();
                    projectURI = session.getProject().getURI();
                } catch (DatabaseException e) {
                    s_logger.error("Exception caught", e);
                }
            } else if (resource instanceof ExportGroup) {
                ExportGroup exportGroup = (ExportGroup) resource;
                try {
                    id = exportGroup.getId();
                    projectURI = exportGroup.getProject().getURI();
                    tenantURI = (exportGroup.getTenant() != null) ? exportGroup.getTenant().getURI() : null;
                } catch (Exception e) {
                    s_logger.error("Exception caught", e);
                }
            } else if (resource instanceof FCZoneReference) {
                FCZoneReference zone = (FCZoneReference) resource;
                try {
                    id = zone.getId();
                } catch (Exception e) {
                    s_logger.error("Exception caught", e);
                }
            } else if (resource instanceof Network) {
                Network tz = (Network) resource;
                id = tz.getId();
                nativeGuid = tz.getNativeGuid();
            } else if (resource instanceof BlockConsistencyGroup) {
                BlockConsistencyGroup consistencyGroup = (BlockConsistencyGroup) resource;
                try {
                    id = consistencyGroup.getId();
                    projectURI = consistencyGroup.getProject().getURI();
                    tenantURI = (consistencyGroup.getTenant() != null) ? consistencyGroup.getTenant()
                            .getURI() : null;
                } catch (Exception e) {
                    s_logger.error("Exception caught", e);
                }
            } else if (resource instanceof StoragePool) {
                StoragePool sp = (StoragePool) resource;
                id = sp.getId();
                nativeGuid = sp.getNativeGuid();
            } else {
                s_logger.info(
                        "Error getting vpool,id,NativeGuid for event. Unexpected resource type {}.",
                        resource.getClass().getName());
            }
            // TODO fix the bogus tenant, user ID once we have AuthZ working
            if (tenantURI == null && projectURI != null) {
                tenantURI = ControllerUtils.getProjectTenantOrgURI(dbClient, projectURI);
            }
            event = new RecordableBourneEvent(
                    type,
                    tenantURI,
                    URI.create("ViPR-User"), // user ID TODO when AAA
                                             // fixed
                    projectURI, cos, evtServiceType, id, description,
                    System.currentTimeMillis(), extensions, nativeGuid,
                    recordType, eventServiceSource, "", "");
        }

        return event;
    }

    /**
     * convert Bytes to KiloBytes
     * 
     * @param value
     * @return
     */
    public static Long convertBytesToKBytes(String value) {
        if (null == value) {
            return 0L;
        }
        BigDecimal val = new BigDecimal(value);
        BigDecimal kbconverter = new BigDecimal(KILOBYTECONVERTERVALUE);
        BigDecimal result = val.divide(kbconverter, RoundingMode.CEILING);
        // if the passed in Value from Provider is less than 1024 bytes, then by
        // default make it to 1 KB.
        if (result.longValue() == 0) {
            return 1L;
        }
        return result.longValue();
    }

    /**
     * If the returned value from Provider cannot be accommodated within Long, then make it to 0.
     * as this is not a valid stat.The only possibility to get a high number is ,Provider initializes
     * all stat property values with a default value of uint64. (18444......)
     * Once stats collected, values will then accommodated within Long.
     * 
     * @param value
     * @return
     */
    public static Long getLongValue(String value) {
        try {
            return Long.parseLong(value);
        } catch (Exception e) {
            s_logger.warn("Not parse String to get Long value");
        }
        return 0L;
    }

    static final BigInteger modValue = BigInteger.valueOf(Long.MAX_VALUE).add(BigInteger.ONE);

    /**
     * Get a modulo long value from a potentially bigger number by creating a BigInteger and
     * MODing by Long.MAX_VALUE + 1
     * 
     * @param value - String value of arbitrarily large integer
     * @return Long value computed by BigInteger MOD (Long.MAX_VALUE+1), 0 in case of Exception
     */
    public static Long getModLongValue(String value) {
        try {
            BigInteger bigValue = new BigInteger(value);
            bigValue = bigValue.mod(modValue);
            return bigValue.longValue();
        } catch (Exception e) {
            s_logger.warn("Not parse String to get Long value");
        }
        return 0L;
    }

    /**
     * Returen a double vaule. Returns 0.0 if mal-formatted.
     * 
     * @param value -- String
     * @return Double value
     */
    public static Double getDoubleValue(String value) {
        try {
            return Double.parseDouble(value);
        } catch (Exception e) {
            s_logger.warn("Not parse String to get Double value");
        }
        return 0.0;
    }

    public static VolumeURIHLU[] getVolumeURIHLUArray(String storageType,
            Map<URI, Integer> volumeMap,
            DbClient dbClient) {
        VolumeURIHLU[] volURIsHlus = EMPTY_VOLUME_URI_HLU_ARRAY; // Have a non-null default value
        if (volumeMap != null && !volumeMap.keySet().isEmpty()) {
            boolean convertFromHex = storageType.equals(DiscoveredDataObject.Type.vmax.name());
            int entryCount = volumeMap.keySet().size();
            volURIsHlus = new VolumeURIHLU[entryCount];
            int index = 0;
            Map<URI, String> blockURIToLabelMap = new HashMap<URI, String>();
            Map<String, URI> nativeIdToURIMap = new HashMap<String, URI>();
            for (URI uri : volumeMap.keySet()) {
                BlockObject blockObject = BlockObject.fetch(dbClient, uri);
                Integer nativeId;
                String nativeIdString = blockObject.getNativeId();
                if (convertFromHex) {
                    nativeId = Integer.parseInt(blockObject.getNativeId(), 16);
                    nativeIdString = String.format("%04d", nativeId);
                } else if (!storageType.equals(DiscoveredDataObject.Type.vnxe.name()) &&
                        blockObject.getNativeId().matches("\\d+")) {
                    nativeId = Integer.parseInt(blockObject.getNativeId());
                    nativeIdString = String.format("%04d", nativeId);
                }
                nativeIdToURIMap.put(nativeIdString, blockObject.getId());
                blockURIToLabelMap.put(blockObject.getId(), blockObject.getLabel());
            }
            Set<String> orderedByNativeId = new TreeSet<String>(nativeIdToURIMap.keySet());
            for (String nativeId : orderedByNativeId) {
                URI uri = nativeIdToURIMap.get(nativeId);
                Integer entryHLU = volumeMap.get(uri);
                String hluString = (entryHLU != null) ? Integer.toHexString(entryHLU) :
                        ExportGroup.LUN_UNASSIGNED_STR;
                String volLabel = blockURIToLabelMap.get(uri);
                if (storageType.equals(DiscoveredDataObject.Type.hds.name())
                        || storageType.equals(DiscoveredDataObject.Type.xtremio.name())) {
                    // @TODO setting the policy name as null for now. We should handle when we support tiering.
                    volURIsHlus[index++] = new VolumeURIHLU(uri, String.valueOf(entryHLU), null, volLabel);
                } else {
                    String policyName = getAutoTieringPolicyName(uri, dbClient);
                    VolumeURIHLU volumeURLHLU = new VolumeURIHLU(uri, hluString, policyName, volLabel);
                    if (storageType.equals(DiscoveredDataObject.Type.vmax.name())) {
                        BlockObject blockObject = BlockObject.fetch(dbClient, uri);
                        if (blockObject instanceof Volume) {
                            Volume volume = (Volume) blockObject;
                            VirtualPool virtualPool = dbClient.queryObject(VirtualPool.class, volume.getVirtualPool());
                            volumeURLHLU = new VolumeURIHLU(uri, hluString, policyName, volLabel,
                                    virtualPool.getHostIOLimitBandwidth(),
                                    virtualPool.getHostIOLimitIOPs());
                        }
                    }
                    volURIsHlus[index++] = volumeURLHLU;
                }
            }
            s_logger.info(String.format("getVolumeURIHLUArray = %s",
                    Joiner.on(',').join(volURIsHlus)));
        }
        return volURIsHlus;
    }

    public static String getAutoTieringPolicyName(URI uri, DbClient dbClient) {
        String policyName = Constants.NONE;
        if (URIUtil.isType(uri, Volume.class)) {
            Volume volume = dbClient.queryObject(Volume.class, uri);
            URI policyURI = volume.getAutoTieringPolicyUri();
            if (!NullColumnValueGetter.isNullURI(policyURI)) {
                AutoTieringPolicy policy = dbClient.queryObject(AutoTieringPolicy.class, policyURI);
                policyName = policy.getPolicyName();
            }
        }
        else if (URIUtil.isType(uri, BlockSnapshot.class)) {
            BlockSnapshot snapshot = dbClient.queryObject(BlockSnapshot.class, uri);
            StorageSystem storage = dbClient.queryObject(StorageSystem.class, snapshot.getStorageController());
            if (storage.checkIfVmax3()) {
                Volume volume = dbClient.queryObject(Volume.class, snapshot.getParent());
                URI policyURI = volume.getAutoTieringPolicyUri();
                if (!NullColumnValueGetter.isNullURI(policyURI)) {
                    AutoTieringPolicy policy = dbClient.queryObject(AutoTieringPolicy.class, policyURI);
                    policyName = policy.getPolicyName();
                }
            }
        } else if (URIUtil.isType(uri, BlockMirror.class)) {
            BlockMirror mirror = dbClient.queryObject(BlockMirror.class, uri);
            if (!NullColumnValueGetter.isNullURI(mirror.getAutoTieringPolicyUri())) {
                AutoTieringPolicy policy = dbClient.queryObject(AutoTieringPolicy.class, mirror.getAutoTieringPolicyUri());
                policyName = policy.getPolicyName();
            }
        }

        return policyName;
    }

    /**
     * Gets the URI of auto tiering policy associated with from virtual pool.
     * 
     * @param vPool the virtual pool
     * @param storage the storage system
     * @param dbClient the db client
     * @return the auto tiering policy uri
     */
    public static URI getAutoTieringPolicyURIFromVirtualPool(VirtualPool vPool,
            StorageSystem storage, DbClient dbClient) {
        /**
         * for VMAX:
         * if unique tiering policy is enabled on Virtual Pool, it has policy's
         * name. else it has policy's nativeGuid.
         * 
         * for VNX:
         * Unique tiering policy field is not available.
         * So, it always has the policy's name.
         */
        String policyNameInVpool = vPool.getAutoTierPolicyName();
        if (policyNameInVpool != null) {
            URIQueryResultList result = new URIQueryResultList();
            if (vPool.getUniquePolicyNames()) {
                dbClient.queryByConstraint(AlternateIdConstraint.Factory
                        .getFASTPolicyByNameConstraint(policyNameInVpool), result);
            } else {
                StringSet systemType = new StringSet();
                if (vPool.getArrayInfo() != null) {
                    systemType.addAll(vPool.getArrayInfo().get(VirtualPoolCapabilityValuesWrapper.SYSTEM_TYPE));
                }
                if (systemType.contains(DiscoveredDataObject.Type.vnxblock.name())) {
                    dbClient.queryByConstraint(AlternateIdConstraint.Factory
                            .getFASTPolicyByNameConstraint(policyNameInVpool), result);
                } else {
                    dbClient.queryByConstraint(AlternateIdConstraint.Factory
                            .getAutoTieringPolicyByNativeGuidConstraint(policyNameInVpool), result);
                }
            }
            Iterator<URI> iterator = result.iterator();
            // if virtual pool is set with a unique policy name, it returns all
            // policies with that name from different arrays.
            while (iterator.hasNext()) {
                URI policyURI = iterator.next();
                AutoTieringPolicy policy = dbClient.queryObject(
                        AutoTieringPolicy.class, policyURI);
                if (policy.getStorageSystem().equals(storage.getId())) {
                    return policyURI;
                }
            }
        }
        return null;
    }

    /**
     * grouping volumes based on fast Policy
     * 
     * @param volumeMap
     * @param dbClient
     * @return
     */
    public static Map<String, Map<URI, Integer>> groupVolumeBasedOnPolicy(
            Map<URI, Integer> volumeMap, DbClient dbClient) {
        Map<String, Map<URI, Integer>> volumeGroup = new HashMap<String, Map<URI, Integer>>();

        if (volumeMap != null && !volumeMap.keySet().isEmpty()) {
            for (Map.Entry<URI, Integer> entry : volumeMap.entrySet()) {
                String policyName = getAutoTieringPolicyName(entry.getKey(), dbClient);
                Map<URI, Integer> volumeUris = volumeGroup.get(policyName);
                if (null == volumeUris) {
                    volumeUris = new HashMap<URI, Integer>();
                    volumeGroup.put(policyName, volumeUris);
                }
                volumeUris.put(entry.getKey(), entry.getValue());
            }
        }
        return volumeGroup;
    }

    /**
     * get Volume NativeGuids from volume Map
     * 
     * @param volumeMap
     * @param dbClient
     * @return
     */
    public static ListMultimap<String, VolumeURIHLU> getVolumeNativeGuids(
            Collection<VolumeURIHLU> volumeMap, DbClient dbClient) {
        ListMultimap<String, VolumeURIHLU> nativeGuidToVolumeUriHLU = ArrayListMultimap
                .create();
        for (VolumeURIHLU volumeURIHLU : volumeMap) {
            BlockObject blockObject = BlockObject.fetch(dbClient, volumeURIHLU.getVolumeURI());
            nativeGuidToVolumeUriHLU.put(blockObject.getNativeGuid(), volumeURIHLU);
        }
        return nativeGuidToVolumeUriHLU;
    }

    public static VolumeURIHLU[] constructVolumeUriHLUs(Set<String> diff, ListMultimap<String, VolumeURIHLU> nativeGuidToVolumeHluMap) {
        List<VolumeURIHLU> volumeUriHLUs = new ArrayList<VolumeURIHLU>();
        for (String nativeGuid : diff) {
            Collection<VolumeURIHLU> volumeUriHLU = nativeGuidToVolumeHluMap.asMap().get(nativeGuid);
            volumeUriHLUs.addAll(volumeUriHLU);

        }
        VolumeURIHLU[] volumeURIHLUArr = new VolumeURIHLU[volumeUriHLUs.size()];
        return volumeUriHLUs.toArray(volumeURIHLUArr);
    }

    /**
     * Gets the property value from coordinator.
     * 
     * @param coordinator
     * @param key
     * @return the property value
     */
    public static String getPropertyValueFromCoordinator(CoordinatorClient coordinator, String key) {
        return coordinator.getPropertyInfo().getProperty(key);
    }

    /**
     * Query database to get storage ports of given storage systems
     * 
     * @param dbClient
     * @param systemURI
     * @return list of storage system's storage ports
     */
    public static List<StoragePort> getSystemPortsOfSystem(final DbClient dbClient, final URI systemURI) {
        List<StoragePort> systemPorts = new ArrayList<StoragePort>();
        URIQueryResultList portQueryResult = new URIQueryResultList();
        try {
            dbClient.queryByConstraint(ContainmentConstraint.Factory.getStorageDeviceStoragePortConstraint(systemURI),
                    portQueryResult);
            for (Iterator<URI> portResultItr = portQueryResult.iterator(); portResultItr.hasNext();) {
                StoragePort port = dbClient.queryObject(StoragePort.class, portResultItr.next());
                systemPorts.add(port);
            }
        } catch (DatabaseException e) {
            // Even if one volume fails, no need to throw exception instead
            // continue processing other volumes
            s_logger.error("Unable to retrieve ports for system: {}", systemURI);
        }
        return systemPorts;
    }

    /**
     * Convenient method to get policy name from a virtual pool
     * 
     * @param _dbClient
     * @param storage
     * @param vpool
     * @return
     */
    public static String getFastPolicyNameFromVirtualPool(DbClient _dbClient, StorageSystem storage, VirtualPool vpool) {
        String policyName = Constants.NONE;
        URI policyURI = ControllerUtils.getAutoTieringPolicyURIFromVirtualPool(vpool, storage, _dbClient);
        if (policyURI != null) {
            AutoTieringPolicy policy = _dbClient.queryObject(AutoTieringPolicy.class, policyURI);
            policyName = policy.getPolicyName();
        }
        return policyName;

    }

    /**
     * Utility method which will filter the snapshots from getBlockSnapshotsBySnapsetLabel query by the
     * snapshot's project
     * 
     * @param snapshot
     * @param dbClient
     * @return
     */
    public static List<BlockSnapshot> getBlockSnapshotsBySnapsetLabelForProject(BlockSnapshot snapshot, DbClient dbClient) {
        URIQueryResultList list = new URIQueryResultList();
        dbClient.queryByConstraint(AlternateIdConstraint.Factory.
                getBlockSnapshotsBySnapsetLabel(snapshot.getSnapsetLabel()), list);
        Iterator<BlockSnapshot> resultsIt = dbClient.queryIterativeObjects(BlockSnapshot.class, list);
        List<BlockSnapshot> snapshots = new ArrayList<BlockSnapshot>();
        while (resultsIt.hasNext()) {
            BlockSnapshot snap = resultsIt.next();
            if (snapshot.getProject() != null && snapshot.getProject().getURI().equals(snap.getProject().getURI())) {
                snapshots.add(snap);
            }
        }
        return snapshots;
    }

    /**
     * Determines if the passed volume is a full copy.
     * 
     * @param volume A reference to a volume.
     * @param dbClient A reference to database client.
     * 
     * @return true if the volume is a full copy, false otherwise.
     */
    public static boolean isVolumeFullCopy(Volume volume, DbClient dbClient) {
        boolean isFullCopy = false;
        URI fcSourceObjURI = volume.getAssociatedSourceVolume();
        if (!NullColumnValueGetter.isNullURI(fcSourceObjURI)) {
            BlockObject fcSourceObj = BlockObject.fetch(dbClient, fcSourceObjURI);
            if ((fcSourceObj != null) && (!fcSourceObj.getInactive())) {
                // The volume has a valid source object, so it
                // is a full copy volume. We check the source,
                // because the full copy mat have been detached
                // from the source and the source may have been
                // deleted.
                isFullCopy = true;
            }
        }
        return isFullCopy;
    }

    /**
     * Gets the volumes part of a given consistency group.
     *
     */
    public static List<Volume> getVolumesPartOfCG(URI cgURI, DbClient dbClient) {
        List<Volume> volumes = new ArrayList<Volume>();
        final URIQueryResultList uriQueryResultList = new URIQueryResultList();
        dbClient.queryByConstraint(AlternateIdConstraint.Factory
                .getBlockObjectsByConsistencyGroup(cgURI.toString()),
                uriQueryResultList);
        Iterator<Volume> volumeIterator = dbClient.queryIterativeObjects(Volume.class,
                uriQueryResultList);
        while (volumeIterator.hasNext()) {
            Volume volume = volumeIterator.next();
            if (volume != null && !volume.getInactive()) {
                volumes.add(volume);
            }
        }
        return volumes;
    }

    /**
     * Gets the volumes part of a given replication group.
     */
    public static List<Volume> getVolumesPartOfRG(StorageSystem storage, URI cgURI /* TODO - use replicaitonGroupInstance */, DbClient dbClient) {
        List<Volume> volumes = new ArrayList<Volume>();
        final BlockConsistencyGroup group =
                dbClient.queryObject(BlockConsistencyGroup.class, cgURI);
        String rgGroup = null;
        if (group != null && storage != null) {
            rgGroup = group.getCgNameOnStorageSystem(storage.getId());
            if (rgGroup != null) {
                final URIQueryResultList uriQueryResultList = new URIQueryResultList();
                dbClient.queryByConstraint(AlternateIdConstraint.Factory
                        .getVolumeReplicationGroupInstanceConstraint(rgGroup), uriQueryResultList);
                Iterator<Volume> volumeIterator = dbClient.queryIterativeObjects(Volume.class,
                        uriQueryResultList);
                while (volumeIterator.hasNext()) {
                    Volume volume = volumeIterator.next();
                    if (volume != null && !volume.getInactive()) {
                        volumes.add(volume);
                    }
                }
            }
        }

        return volumes;
    }

    /**
     * Gets the volumes part of a given replication group.
     * TODO remove this method when the above method (getVolumesPartOfRG) takes in RepGroup name instead of CG.
     */
    public static List<Volume> getVolumesPartOfRG(String replicationGroupInstance, DbClient dbClient) {
        List<Volume> volumes = new ArrayList<Volume>();
        URIQueryResultList uriQueryResultList = new URIQueryResultList();
        dbClient.queryByConstraint(AlternateIdConstraint.Factory
                .getVolumeReplicationGroupInstanceConstraint(replicationGroupInstance), uriQueryResultList);
        Iterator<Volume> volumeIterator = dbClient.queryIterativeObjects(Volume.class,
                uriQueryResultList, true);
        while (volumeIterator.hasNext()) {
            Volume volume = volumeIterator.next();
            if (volume != null && !volume.getInactive()) {
                volumes.add(volume);
            }
        }
        return volumes;
    }

    /**
     * Gets the mirrors part of a given replication group.
     */
    public static List<BlockMirror> getMirrorsPartOfReplicationGroup(
            String replicationGroupInstance, DbClient dbClient) {
        List<BlockMirror> mirrors = new ArrayList<BlockMirror>();
        URIQueryResultList uriQueryResultList = new URIQueryResultList();
        dbClient.queryByConstraint(AlternateIdConstraint.Factory
                .getMirrorReplicationGroupInstanceConstraint(replicationGroupInstance),
                uriQueryResultList);
        Iterator<BlockMirror> mirrorIterator = dbClient.queryIterativeObjects(BlockMirror.class,
                uriQueryResultList);
        while (mirrorIterator.hasNext()) {
            BlockMirror mirror = mirrorIterator.next();
            if (mirror != null && !mirror.getInactive()) {
                mirrors.add(mirror);
            }
        }
        return mirrors;
    }

    /**
     * Gets the full copies part of a given replication group.
     */
    public static List<Volume> getFullCopiesPartOfReplicationGroup(
            String replicationGroupInstance, DbClient dbClient) {
        List<Volume> fullCopies = new ArrayList<Volume>();
        URIQueryResultList uriQueryResultList = new URIQueryResultList();
        dbClient.queryByConstraint(AlternateIdConstraint.Factory
                .getVolumeReplicationGroupInstanceConstraint(replicationGroupInstance),
                uriQueryResultList);
        Iterator<Volume> itr = dbClient.queryIterativeObjects(Volume.class,
                uriQueryResultList);
        while (itr.hasNext()) {
            Volume fullCopy = itr.next();
            if (fullCopy != null && !fullCopy.getInactive()) {
                fullCopies.add(fullCopy);
            }
        }
        return fullCopies;
    }

    /**
     * Gets the snapshots part of a given replication group.
     */
    public static List<BlockSnapshot> getSnapshotsPartOfReplicationGroup(
            String replicationGroupInstance, DbClient dbClient) {
        List<BlockSnapshot> snapshots = new ArrayList<BlockSnapshot>();
        URIQueryResultList uriQueryResultList = new URIQueryResultList();
        dbClient.queryByConstraint(AlternateIdConstraint.Factory
                .getSnapshotReplicationGroupInstanceConstraint(replicationGroupInstance),
                uriQueryResultList);
        Iterator<BlockSnapshot> snapIterator = dbClient.queryIterativeObjects(BlockSnapshot.class,
                uriQueryResultList);
        while (snapIterator.hasNext()) {
            BlockSnapshot snapshot = snapIterator.next();
            if (snapshot != null && !snapshot.getInactive()) {
                snapshots.add(snapshot);
            }
        }
        return snapshots;
    }

    /**
     * Gets the replication group name from replicas of all volumes in CG.
     */
    public static String getGroupNameFromReplicas(List<URI> replicas,
            BlockConsistencyGroup consistencyGroup, DbClient dbClient) {
        URI replicaURI = replicas.iterator().next();
        // get volumes part of this CG
        List<Volume> volumes = ControllerUtils.
                getVolumesPartOfCG(consistencyGroup.getId(), dbClient);

        // check if replica of any of these volumes have replicationGroupInstance set
        for (Volume volume : volumes) {
            if (URIUtil.isType(replicaURI, BlockSnapshot.class)) {
                URIQueryResultList list = new URIQueryResultList();
                dbClient.queryByConstraint(ContainmentConstraint.Factory
                        .getVolumeSnapshotConstraint(volume.getId()), list);
                Iterator<URI> it = list.iterator();
                while (it.hasNext()) {
                    URI snapshotID = it.next();
                    BlockSnapshot snapshot = dbClient.queryObject(BlockSnapshot.class, snapshotID);
                    if (snapshot != null && !snapshot.getInactive()
                            && NullColumnValueGetter.isNotNullValue(snapshot.getReplicationGroupInstance())) {
                        return snapshot.getReplicationGroupInstance();
                    }
                }
            } else if (URIUtil.isType(replicaURI, Volume.class)) {
                URIQueryResultList cloneList = new URIQueryResultList();
                dbClient.queryByConstraint(ContainmentConstraint.Factory
                        .getAssociatedSourceVolumeConstraint(volume.getId()), cloneList);
                Iterator<URI> iter = cloneList.iterator();
                while (iter.hasNext()) {
                    URI cloneID = iter.next();
                    Volume clone = dbClient.queryObject(Volume.class, cloneID);
                    if (clone != null && !clone.getInactive()
                            && NullColumnValueGetter.isNotNullValue(clone.getReplicationGroupInstance())) {
                        return clone.getReplicationGroupInstance();
                    }
                }
            } else if (URIUtil.isType(replicaURI, BlockMirror.class)) {
                URIQueryResultList mirrorList = new URIQueryResultList();
                dbClient.queryByConstraint(ContainmentConstraint.Factory
                        .getVolumeBlockMirrorConstraint(volume.getId()), mirrorList);
                Iterator<URI> itr = mirrorList.iterator();
                while (itr.hasNext()) {
                    URI mirrorID = itr.next();
                    BlockMirror mirror = dbClient.queryObject(BlockMirror.class, mirrorID);
                    if (mirror != null && !mirror.getInactive()
                            && NullColumnValueGetter.isNotNullValue(mirror.getReplicationGroupInstance())) {
                        return mirror.getReplicationGroupInstance();
                    }
                }
            }
        }
        return null;
    }

    /**
     * Check if CG has any group relationship
     *
     * Note - on array side, if replica has been removed from replication group, but source volume has not been removed from CG yet,
     * the CG will not have group relationship until the source volume get removed from the CG.
     *
     * As a result, getting associator names cannot be used to check if CG has group relationship.
     */
    public static boolean checkCGHasGroupRelationship(StorageSystem storage, URI cgURI, DbClient dbClient) {
        // get volumes part of this CG
        List<Volume> volumes = ControllerUtils.getVolumesPartOfCG(cgURI, dbClient);
        boolean isVNX = storage.deviceIsType(Type.vnxblock);
        // check if replica of any of these volumes have replicationGroupInstance set
        for (Volume volume : volumes) {
            if (!isVNX) { // VNX doesn't have group clones/mirrors
                // clone
                URIQueryResultList cloneList = new URIQueryResultList();
                dbClient.queryByConstraint(ContainmentConstraint.Factory
                        .getAssociatedSourceVolumeConstraint(volume.getId()), cloneList);
                Iterator<URI> iter = cloneList.iterator();
                while (iter.hasNext()) {
                    URI cloneID = iter.next();
                    Volume clone = dbClient.queryObject(Volume.class, cloneID);
                    if (clone != null && !clone.getInactive()
                            && NullColumnValueGetter.isNotNullValue(clone.getReplicationGroupInstance())) {
                        return true;
                    }
                }

                // mirror
                URIQueryResultList mirrorList = new URIQueryResultList();
                dbClient.queryByConstraint(ContainmentConstraint.Factory
                        .getVolumeBlockMirrorConstraint(volume.getId()), mirrorList);
                Iterator<URI> itr = mirrorList.iterator();
                while (itr.hasNext()) {
                    URI mirrorID = itr.next();
                    BlockMirror mirror = dbClient.queryObject(BlockMirror.class, mirrorID);
                    if (mirror != null && !mirror.getInactive()
                            && NullColumnValueGetter.isNotNullValue(mirror.getReplicationGroupInstance())) {
                        return true;
                    }
                }
            }

            // snapshot
            URIQueryResultList list = new URIQueryResultList();
            dbClient.queryByConstraint(ContainmentConstraint.Factory
                    .getVolumeSnapshotConstraint(volume.getId()), list);
            Iterator<URI> it = list.iterator();
            while (it.hasNext()) {
                URI snapshotID = it.next();
                BlockSnapshot snapshot = dbClient.queryObject(BlockSnapshot.class, snapshotID);
                if (snapshot != null && !snapshot.getInactive()
                        && NullColumnValueGetter.isNotNullValue(snapshot.getReplicationGroupInstance())) {
                    return true;
                }
            }
        }

        return false;
    }

    /**
     * Gets snapshot replication group names from source volumes in CG.
     *
     * @param volumes
     * @param dbClient
     * @return
     */
    public static Set<String> getSnapshotReplicationGroupNames(List<Volume> volumes, DbClient dbClient) {
        Set<String> groupNames = new HashSet<>();

        // check if replica of any of these volumes have replicationGroupInstance set
        for (Volume volume : volumes) {
            URIQueryResultList snapshotList = new URIQueryResultList();
            dbClient.queryByConstraint(ContainmentConstraint.Factory.getVolumeSnapshotConstraint(volume.getId()),
                    snapshotList);
            Iterator<URI> iter = snapshotList.iterator();
            while (iter.hasNext()) {
                URI snapshotID = iter.next();
                BlockSnapshot snapshot = dbClient.queryObject(BlockSnapshot.class, snapshotID);
                if (snapshot != null && !snapshot.getInactive()
                        && NullColumnValueGetter.isNotNullValue(snapshot.getReplicationGroupInstance())) {
                    groupNames.add(snapshot.getReplicationGroupInstance());
                }
            }

            if (!groupNames.isEmpty()) {
                // no need to check other CG members
                break;
            }
        }

        return groupNames;
    }

    /**
     * Gets clone replication group names from clones of all volumes in CG.
     */
    public static Set<String> getCloneReplicationGroupNames(List<Volume> volumes, DbClient dbClient) {
        Set<String> groupNames = new HashSet<String>();

        // check if replica of any of these volumes have replicationGroupInstance set
        for (Volume volume : volumes) {
            URIQueryResultList cloneList = new URIQueryResultList();
            dbClient.queryByConstraint(ContainmentConstraint.Factory
                    .getAssociatedSourceVolumeConstraint(volume.getId()), cloneList);
            Iterator<URI> iter = cloneList.iterator();
            while (iter.hasNext()) {
                URI cloneID = iter.next();
                Volume clone = dbClient.queryObject(Volume.class, cloneID);
                if (clone != null && !clone.getInactive()
                        && NullColumnValueGetter.isNotNullValue(clone.getReplicationGroupInstance())) {
                    groupNames.add(clone.getReplicationGroupInstance());
                }
            }

            if (!groupNames.isEmpty()) {
                // no need to check other CG members
                break;
            }
        }

        return groupNames;
    }

    /**
     * Gets mirror replication group names from mirrors of all volumes in CG.
     */
    public static Set<String> getMirrorReplicationGroupNames(List<Volume> volumes, DbClient dbClient) {
        Set<String> groupNames = new HashSet<String>();

        // check if replica of any of these volumes have replicationGroupInstance set
        for (Volume volume : volumes) {
            URIQueryResultList mirrorList = new URIQueryResultList();
            dbClient.queryByConstraint(ContainmentConstraint.Factory
                    .getVolumeBlockMirrorConstraint(volume.getId()), mirrorList);
            Iterator<URI> iter = mirrorList.iterator();
            while (iter.hasNext()) {
                URI mirrorID = iter.next();
                BlockMirror mirror = dbClient.queryObject(BlockMirror.class, mirrorID);
                if (mirror != null && !mirror.getInactive()
                        && NullColumnValueGetter.isNotNullValue(mirror.getReplicationGroupInstance())) {
                    groupNames.add(mirror.getReplicationGroupInstance());
                }
            }

            if (!groupNames.isEmpty()) {
                // no need to check other CG members
                break;
            }
        }

        return groupNames;
    }

    public static String getMirrorLabel(String sourceLabel, String mirrorLabel) {
        return sourceLabel + LABEL_DELIMITER + mirrorLabel;
    }

    public static String getMirrorLabel(String mirrorLabel, int counter) {
        return mirrorLabel + LABEL_DELIMITER + counter;
    }

    public static String generateLabel(String sourceLabel, String mirrorLabel) {
        if (mirrorLabel.startsWith(sourceLabel + LABEL_DELIMITER) && mirrorLabel.length() > sourceLabel.length() + 1) {
            return mirrorLabel.substring(sourceLabel.length() + 1);
        } else {
            return mirrorLabel;
        }
    }

    /**
     * Returns true, if a snapshot is part of a consistency group, false otherwise.
     * In addition to this, if a non-null {@link TaskCompleter} is provided the {@BlockConsistencyGroup} instance
     * added to it.
     *
     * @param snapshots List of snapshot URI's
     * @param dbClient DbClient instance
     * @param completer Optional TaskCompleter instance.
     * @return true/false dependent on a snapshot being part of a consistency group.
     */
    public static boolean checkSnapshotsInConsistencyGroup(List<BlockSnapshot> snapshots, DbClient dbClient,
            TaskCompleter completer) {
        BlockConsistencyGroup group = ConsistencyUtils.getSnapshotsConsistencyGroup(snapshots, dbClient);
        if (group != null) {
            if (completer != null) {
                completer.addConsistencyGroupId(group.getId());
            }
            return true;
        }
        return false;
    }

    /**
     * Returns true, if the clone is part of a consistency group, false otherwise.
     * In addition to this, if a non-null {@link TaskCompleter} is provided the {@BlockConsistencyGroup} instance
     * added to it.
     *
     * @param clone URI of the clone/fullcopy
     * @param dbClient DbClient instance
     * @param completer Optional TaskCompleter instance.
     * @return true/false dependent on the clone being part of a consistency group.
     */
    public static boolean checkCloneConsistencyGroup(URI clone, DbClient dbClient, TaskCompleter completer) {
        BlockConsistencyGroup group = ConsistencyUtils.getCloneConsistencyGroup(clone, dbClient);
        if (group != null) {
            if (completer != null) {
                completer.addConsistencyGroupId(group.getId());
            }
            return true;
        }
        return false;
    }

    public static boolean checkSnapshotSessionConsistencyGroup(URI snapshotSession, DbClient dbClient, TaskCompleter completer) {
        BlockConsistencyGroup group = ConsistencyUtils.getSnapshotSessionConsistencyGroup(snapshotSession, dbClient);
        if (group != null) {
            if (completer != null) {
                completer.addConsistencyGroupId(group.getId());
            }
            return true;
        }
        return false;
    }

    /**
     * Check whether the given volume is vmax volume and vmax managed by SMI 8.0.3
     *
     * @param mirrors
     * @param dbClient
     * @param completer Optional TaskCompleter instance.
     * @return true/false dependent on the clone being part of a consistency group.
     */
    public static boolean checkMirrorConsistencyGroup(List<URI> mirrors, DbClient dbClient, TaskCompleter completer) {
        BlockConsistencyGroup group = ConsistencyUtils.getMirrorsConsistencyGroup(mirrors, dbClient);
        if (group != null) {
            if (completer != null) {
                completer.addConsistencyGroupId(group.getId());
            }
            return true;
        }
        return false;
    }

    /**
     * Check whether the given volume is vmax volume and vmax managed by SMI 8.0.3
     * 
     * @param volume
     * @param dbClient
     * @return
     */
    public static boolean isVmaxVolumeUsing803SMIS(Volume volume, DbClient dbClient) {
        StorageSystem storage = dbClient.queryObject(StorageSystem.class, volume.getStorageController());
        return (storage != null && storage.deviceIsType(Type.vmax) && storage.getUsingSmis80());
    }

    /**
     * Check whether the given volume is VNX volume
     *
     * @param volume
     * @param dbClient
     * @return
     */
    public static boolean isVnxVolume(Volume volume, DbClient dbClient) {
        StorageSystem storage = dbClient.queryObject(StorageSystem.class, volume.getStorageController());
        return storage != null && storage.deviceIsType(Type.vnxblock);
    }

    /**
     * Check whether the given volume is XtremIO volume
     *
     * @param volume
     * @param dbClient
     * @return
     */
    public static boolean isXtremIOVolume(Volume volume, DbClient dbClient) {
        StorageSystem storage = dbClient.queryObject(StorageSystem.class, volume.getStorageController());
        return storage != null && storage.deviceIsType(Type.xtremio);
    }

    /**
     * Check whether the given volume is in VNX virtual replication group
     *
     * @param volume
     * @param dbClient
     * @return
     */
    public static boolean isInVNXVirtualRG(Volume volume, DbClient dbClient) {
        return volume != null && ControllerUtils.isVnxVolume(volume, dbClient) &&
                StringUtils.startsWith(volume.getReplicationGroupInstance(), SmisConstants.VNX_VIRTUAL_RG);
    }

    public static String generateReplicationGroupName(StorageSystem storage, URI cgUri, String replicationGroupName, DbClient dbClient) {
        BlockConsistencyGroup cg = dbClient.queryObject(BlockConsistencyGroup.class, cgUri);
        if (cg == null || cg.getInactive()) {
            s_logger.warn(String.format("BlockConsistencyGroup with uri %s does not exist or is inactive", cgUri.toString()));
        }
        return generateReplicationGroupName(storage, cg, replicationGroupName);
    }
    
    public static String generateReplicationGroupName(StorageSystem storage, BlockConsistencyGroup cg, String replicationGroupName) {
        String groupName = replicationGroupName;
        
        if (storage.deviceIsType(Type.vnxblock) && cg.getArrayConsistency()) {
            return cg.getCgNameOnStorageSystem(storage.getId());
        }
        
        if (groupName == null && cg != null) {
            // if there is only one system cg name for this storage system, use this; it may be different than the label
            if (cg.getSystemConsistencyGroups() != null && storage != null) {
                StringSet cgsforStorage = cg.getSystemConsistencyGroups().get(storage.getId().toString());
                if (cgsforStorage != null && cgsforStorage.size() == 1) {
                    groupName = cgsforStorage.iterator().next();
                } else {
                    groupName = (cg.getAlternateLabel() != null) ? cg.getAlternateLabel() : cg.getLabel();
                }
            } else {
                groupName = (cg.getAlternateLabel() != null) ? cg.getAlternateLabel() : cg.getLabel();
            }
        }

        return groupName;
    }

    public static String generateVirtualReplicationGroupName(String groupName) {
        return SmisConstants.VNX_VIRTUAL_RG + groupName;
    }

    /**
     * This utility method returns the snapsetLabel of the existing snapshots.
     * This is required when we try to create a new snapshot when the existing source volumes have snapshots.
     * 
     * @param repGroupName
     * @param dbClient
     * @return
     */
    public static String getSnapSetLabelFromExistingSnaps(String repGroupName, DbClient dbClient) {
        List<BlockSnapshot> snapshots = getSnapshotsPartOfReplicationGroup(repGroupName, dbClient);
        String existingSnapSnapSetLabel = null;
        if (null != snapshots && !snapshots.isEmpty()) {
            existingSnapSnapSetLabel = snapshots.get(0).getSnapsetLabel();
        }
        return existingSnapSnapSetLabel;
    }

    /**
     * Check whether the given storage system is managed by SMI 8.1
     * 
     * @param storage
     * @param dbClient
     * @return status
     */
    public static boolean isVmaxUsing81SMIS(StorageSystem storage, DbClient dbClient) {
        boolean status = false;
        if (storage != null) {
            StorageProvider provider = dbClient.queryObject(StorageProvider.class, storage.getActiveProviderURI());
            if (provider != null) {
                String providerVersion = provider.getVersionString();
                status = providerVersion != null && providerVersion.startsWith(SMI81_VERSION_STARTING_STR);
            }
        }
        return status;
    }

    /**
     * return the cause of the exception.
     * 
     * @param ex
     * @return
     */
    public static String getMessage(final Exception ex) {
        String cause = ex.getCause() != null ? ex.getCause().toString() : "";
        String message = ex.getMessage() != null ? ex.getMessage() : "";
        String error = "";
        if (!cause.isEmpty()) {
            error = cause;
        }
        if (!message.isEmpty()) {
            error = error + "-" + message;
        }
        return error;
    }

    /*
     * Check CG contains all and only volumes provided
     *
     * Assumption - all volumes provided are in the CG
     *
     * @param dbClient
     * @param cg
     * @param volumes
     * @return boolean
     */
    public static boolean cgHasNoOtherVolume(DbClient dbClient, URI cg, List<?> volumes) {
        URIQueryResultList cgVolumeList = new URIQueryResultList();
        dbClient.queryByConstraint(ContainmentConstraint.Factory
                .getVolumesByConsistencyGroup(cg), cgVolumeList);
        int totalVolumeCount = 0;
        while (cgVolumeList.iterator().hasNext()) {
            Volume cgSourceVolume = dbClient.queryObject(Volume.class, cgVolumeList.iterator().next());
            if (cgSourceVolume != null) {
                totalVolumeCount++;
            }
        }

        s_logger.info("totalVolumeCount {} volume size {}", totalVolumeCount, volumes.size());
        return totalVolumeCount == volumes.size();
    }

    /**
     * Check back end cg created on array or not for the given volume
     * 
     * @param volume
     * @return
     */
    public static boolean checkCGCreatedOnBackEndArray(Volume volume) {

        return (volume != null && StringUtils.isNotBlank(volume.getReplicationGroupInstance()));
    }

    /**
     * Get volume group's volumes.
     * skip internal volumes
     *
     * @param volumeGroup
     * @return The list of volumes in volume group
     */
    public static List<Volume> getVolumeGroupVolumes(DbClient dbClient, VolumeGroup volumeGroup) {
        List<Volume> result = new ArrayList<Volume>();
        final List<Volume> volumes = CustomQueryUtility
                .queryActiveResourcesByConstraint(dbClient, Volume.class,
                        AlternateIdConstraint.Factory.getVolumesByVolumeGroupId(volumeGroup.getId().toString()));
        for (Volume vol : volumes) {
            // return only visible volumes. i.e skip backend or internal volumes
            // TODO check with others
            if (!vol.getInactive() && !vol.checkInternalFlags(Flag.INTERNAL_OBJECT)) {
                result.add(vol);
            }
        }
        return result;
    }

    /**
     * Group volumes by array group.
     *
     * @param volumes the volumes
     * @return the map of array group to volumes
     */
    public static Map<String, List<Volume>> groupVolumesByArrayGroup(List<Volume> volumes) {
        Map<String, List<Volume>> arrayGroupToVolumes = new HashMap<String, List<Volume>>();
        for (Volume volume : volumes) {
            String repGroupName = volume.getReplicationGroupInstance();
            if (arrayGroupToVolumes.get(repGroupName) == null) {
                arrayGroupToVolumes.put(repGroupName, new ArrayList<Volume>());
            }
            arrayGroupToVolumes.get(repGroupName).add(volume);
        }
        return arrayGroupToVolumes;
    }

    /**
     * Return snapshot sessions based on the given snapshot session instance.
     *
     * @param instance
     * @param dbClient
     * @return
     */
    public static List<URI> getSnapshotSessionsByInstance(String instance, DbClient dbClient) {
        URIQueryResultList resultList = new URIQueryResultList();
        dbClient.queryByConstraint(getBlockSnapshotSessionBySessionInstance(instance), resultList);
        return newArrayList(resultList.iterator());
    }
}
