/*
 * Copyright (c) 2008-2013 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

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
import com.emc.storageos.db.client.model.BlockObject;
import com.emc.storageos.db.client.model.BlockSnapshot;
import com.emc.storageos.db.client.model.DataObject;
import com.emc.storageos.db.client.model.DiscoveredDataObject;
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
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.model.StringSet;
import com.emc.storageos.db.client.model.TenantOrg;
import com.emc.storageos.db.client.model.VirtualPool;
import com.emc.storageos.db.client.model.Volume;
import com.emc.storageos.db.client.model.VplexMirror;
import com.emc.storageos.db.client.util.NullColumnValueGetter;
import com.emc.storageos.db.exceptions.DatabaseException;
import com.emc.storageos.plugins.common.Constants;
import com.emc.storageos.volumecontroller.impl.monitoring.RecordableBourneEvent;
import com.emc.storageos.volumecontroller.impl.monitoring.RecordableEvent;
import com.emc.storageos.volumecontroller.impl.utils.VirtualPoolCapabilityValuesWrapper;
import com.emc.storageos.volumecontroller.logging.BournePatternConverter;
import com.google.common.base.Joiner;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ListMultimap;

/**
 * Utilities class encapsulates controller utility methods.
 */
public class ControllerUtils {

    // Logger reference.
    private static final Logger s_logger = LoggerFactory.getLogger(ControllerUtils.class);

    // Constant that represents BLOCK_EVENT_SOURCE
    public static final String BLOCK_EVENT_SOURCE = "Block Controller";
   
    // Constant that represents BLOCK_EVENT_SERVICE
    public static final String BLOCK_EVENT_SERVICE = "block";
    
    private static final String KILOBYTECONVERTERVALUE = "1024";
    
    private static final VolumeURIHLU[] EMPTY_VOLUME_URI_HLU_ARRAY = new VolumeURIHLU[0];

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
      * @param storage
      * @param physicalHardware
      * @return
      *
     public static
     boolean reconcilePhysicalHardware(URI storage,
                                       List<Object> physicalHardware,
                                       DbClient dbClient) {
         Logger log = LoggerFactory.getLogger(ControllerUtils.class);
         try {
             
             // First update the logical pools represented by the physical pool
              
             List<URI> poolURIs = dbClient.queryByConstraint
                     (ContainmentConstraint.Factory
                             .getStorageDeviceStoragePoolConstraint(storage));
             List<StoragePool> pools = dbClient.queryObject(StoragePool
                     .class, poolURIs);
             boolean poolFound;
             for(StoragePool pool : pools){
                 poolFound = false;
                 for(Object obj : physicalHardware){
                     if (obj instanceof PhysicalStoragePool) {
                         // the type and ID must match
                         PhysicalStoragePool psp = (PhysicalStoragePool) obj;
                         if (pool.getControllerParams().get(StoragePool.ControllerParam.NativeId.name()).equals(psp.getNativeId())&&
                             pool.getControllerParams().get(StoragePool.ControllerParam.PoolType.name()).equals(psp.getType())) {
                             pool.setFreeCapacity(psp.getFreeCapacity());
                             pool.setTotalCapacity(psp.getTotalCapacity());
                             pool.setLargestContiguousBlock(psp
                                     .getLargestContiguousBlock());
                             pool.setSubscribedCapacity(psp.getSubscribedCapacity());
                             log.info(String.format("Logical pool %1$s updated by " +
                                     "physical storage pool %2$s/%3$s",
                                     pool.getId().toString(),
                                     psp.getType(), psp.getNativeId()));
                             dbClient.persistObject(pool);
                             poolFound = true;
                             break;
                         }
                     }
                 }
                 if(poolFound == false){
                     // probably a good indication this pool is not valid
                     //pool.setInactive(true);
                     //dbClient.persistObject(pool);
                     log.warn(String.format("Logical pool %1$s not found on storage system",
                                              pool.getId().toString()));
                 }
             }
             
             // Now update the physical pools obtained from controller
              
             poolURIs = dbClient.queryByConstraint(ContainmentConstraint.Factory
                                     .getStorageDevicePhysicalPoolConstraint(storage));
             List<PhysicalStoragePool> physicalPools = dbClient.queryObject(PhysicalStoragePool.class, poolURIs);
             Map<URI,PhysicalStoragePool> newPools = new HashMap<URI,PhysicalStoragePool>();
             // save the set of physical pools so we can tell if there are new ones
             for (Object obj : physicalHardware) {
                 if (obj instanceof PhysicalStoragePool) {
                     PhysicalStoragePool psp = (PhysicalStoragePool) obj;
                     psp.setId(URIUtil.createId(PhysicalStoragePool.class));
                     psp.setInactive(false);
                     psp.setStorageDevice(storage);
                     newPools.put(psp.getId(),psp);
                 }
             }
             for (PhysicalStoragePool pool : physicalPools) {
                 poolFound = false;
                 for (Object obj : physicalHardware) {
                     if (obj instanceof PhysicalStoragePool) {
                         PhysicalStoragePool psp = (PhysicalStoragePool) obj;
                         // native ID and type must match
                         if (pool.getNativeId().equals(psp.getNativeId()) &&
                                 pool.getType().equals(psp.getType())) {
                             newPools.remove(psp.getId());
                             psp.setId(pool.getId());
                             log.info(String.format("Updated physical storage pool %1$s/%2$s:%3$s %4$s",
                                                      psp.getType(), psp.getNativeId(),
                                                      pool.getId().toString(),
                                                      pool.getLabel()));
                             dbClient.persistObject(psp);
                             poolFound = true;
                             break;
                         }
                     }
                 }
                 if(poolFound==false){
                     // this pool is no longer on array
                     log.info(String.format("Inactivated Pool %1$s", pool.getId()));
                     dbClient.markForDeletion(pool);
                 }
             }

             // add new pools
             Iterator<Map.Entry<URI,PhysicalStoragePool>> itr = newPools.entrySet().iterator();
             while(itr.hasNext()){
                 Map.Entry<URI, PhysicalStoragePool> entry = itr.next();
                 PhysicalStoragePool psp = entry.getValue();
                 log.info(String.format("New physical storage pool %1$s/%2$s:%3$s %4$s",
                                         psp.getType(),psp.getNativeId(),
                                         psp.getId().toString(),
                                         psp.getLabel()));
                 dbClient.persistObject(psp);
             }
             return true;
         } catch (IOException e) {
             log.error("Exception while trying to handle results from " +
                     "getPhysicalInventory", e);
         }
         return false;
     }*/

    /**
     * returns if operation (besides opId) is pending
     * @param id  id of resource
     * @param opId operation id for current operation
     * @param resource instance of resource
     * @return
     */
    public static
    boolean isOperationInProgress(URI id, String opId, DataObject resource) {
        OpStatusMap ops = resource.getOpStatus();
        Set<Map.Entry<String,Operation>> opSet = ops.entrySet();
        Iterator<Map.Entry<String,Operation>> opItr = opSet.iterator();

        while (opItr.hasNext()) {
            Map.Entry <String, Operation> entry = opItr.next();
            if(entry.getValue().getStatus().equals(Operation.Status.pending.toString())){
                if (entry.getKey().equals(opId)) {
                    // our operation, pass
                    continue;
                }
                //
                //Logger log = LoggerFactory.getLogger(ControllerUtils.class);
                //log.debug("operation in progress");
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
            }else if (resource instanceof BlockSnapshot) {
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
        if (null == value) return 0L;
        BigDecimal val = new BigDecimal(value);
        BigDecimal kbconverter = new BigDecimal(KILOBYTECONVERTERVALUE);
        BigDecimal result = val.divide(kbconverter, RoundingMode.CEILING);
        // if the passed in Value from Provider is less than 1024 bytes, then by
        // default make it to 1 KB.
        if (result.longValue() == 0)
            return 1L;
        return result.longValue();
    }
    
    /**
     * If the returned value from Provider cannot be accommodated within Long, then make it to 0.
     * as this is not a valid stat.The only possibility to get a high number is ,Provider initializes
     * all stat property values with a default value of uint64. (18444......)
     * Once stats collected, values will then accommodated within Long. 
     * @param value
     * @return
     */
    public static Long getLongValue(String value) {
        try {
            return Long.parseLong(value);
        }catch(Exception e) {
            s_logger.warn("Not parse String to get Long value");
        }
        return 0L;
    }
    
    static final BigInteger modValue = BigInteger.valueOf(Long.MAX_VALUE).add(BigInteger.ONE);
    /**
     * Get a modulo long value from a potentially bigger number by creating a BigInteger and 
     * MODing by Long.MAX_VALUE + 1
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
        if(volumeMap != null && !volumeMap.keySet().isEmpty()) {
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
                if ( storageType.equals(DiscoveredDataObject.Type.hds.name()) || storageType.equals(DiscoveredDataObject.Type.xtremio.name())) {
                    //@TODO setting the policy name as null for now. We should handle when we support tiering.
                    volURIsHlus[index++] = new VolumeURIHLU(uri, String.valueOf(entryHLU), null, volLabel);
                } else {
                    String policyName = getAutoTieringPolicyName(uri,dbClient);
                    VolumeURIHLU volumeURLHLU = new VolumeURIHLU(uri, hluString, policyName, volLabel);
                    if ( storageType.equals(DiscoveredDataObject.Type.vmax.name()) ) {                    
                        BlockObject blockObject = BlockObject.fetch(dbClient, uri);
                        if (blockObject instanceof Volume) {
                            Volume volume = (Volume)blockObject;
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
              AutoTieringPolicy policy =  dbClient.queryObject(AutoTieringPolicy.class, policyURI); 
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
                    AutoTieringPolicy policy =  dbClient.queryObject(AutoTieringPolicy.class, policyURI); 
                    policyName = policy.getPolicyName();
                }
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
                StringSet systemType = vPool.getArrayInfo().get(VirtualPoolCapabilityValuesWrapper.SYSTEM_TYPE);
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
     * @param volumeMap
     * @param dbClient
     * @return
     */
    public static Map<String, Map<URI, Integer>> groupVolumeBasedOnPolicy(
            Map<URI, Integer> volumeMap, DbClient dbClient) {
        Map<String, Map<URI, Integer>> volumeGroup = new HashMap<String, Map<URI, Integer>>();
        
        if (volumeMap != null && !volumeMap.keySet().isEmpty()) {
            for (Map.Entry<URI, Integer> entry : volumeMap.entrySet()) {
                String policyName = getAutoTieringPolicyName(entry.getKey(),dbClient);
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
            if(snapshot.getProject() != null && snapshot.getProject().getURI().equals(snap.getProject().getURI())) {
                snapshots.add(snap);
            }
        }
        return snapshots;
    }
}
