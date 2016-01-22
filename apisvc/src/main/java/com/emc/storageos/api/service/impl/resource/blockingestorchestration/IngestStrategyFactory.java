/*
 * Copyright (c) 2008-2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.api.service.impl.resource.blockingestorchestration;

import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.api.service.impl.resource.utils.PropertySetterUtil;
import com.emc.storageos.api.service.impl.resource.utils.VolumeIngestionUtil;
import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.UnManagedDiscoveredObjects.UnManagedVolume;
import com.emc.storageos.db.client.model.UnManagedDiscoveredObjects.UnManagedVolume.SupportedVolumeCharacterstics;
import com.emc.storageos.db.client.model.UnManagedDiscoveredObjects.UnManagedVolume.SupportedVolumeInformation;

public class IngestStrategyFactory {

    public static final boolean DISREGARD_PROTECTION = true;

    private static final Logger _logger = LoggerFactory.getLogger(IngestStrategyFactory.class);
    
    private BlockIngestOrchestrator blockVolumeIngestOrchestrator;

    private BlockIngestOrchestrator blockRemoteReplicationIngestOrchestrator;

    private BlockIngestOrchestrator blockRecoverPointIngestOrchestrator;
    
    private BlockIngestOrchestrator blockVplexVolumeIngestOrchestrator;

    private BlockIngestExportOrchestrator maskPerHostIngestOrchestrator;

    private BlockIngestExportOrchestrator multipleMaskPerHostIngestOrchestrator;

    private BlockIngestExportOrchestrator unexportedVolumeIngestOrchestrator;
    
    private BlockIngestOrchestrator blockSnapshotIngestOrchestrator;
    
    private BlockIngestOrchestrator blockMirrorIngestOrchestrator;

    private final Map<String, IngestStrategy> ingestStrategyMap;
    
    private final Map<String, IngestExportStrategy> ingestExportStrategyMap;

    private DbClient _dbClient;

    public void setDbClient(DbClient dbClient) {
        _dbClient = dbClient;
    }

    public DbClient getDbClient() {
        return _dbClient;
    }

    public BlockIngestOrchestrator getBlockVolumeIngestOrchestrator() {
        return blockVolumeIngestOrchestrator;
    }

    public void setBlockVolumeIngestOrchestrator(
            BlockIngestOrchestrator blockVolumeIngestOrchestrator) {
        this.blockVolumeIngestOrchestrator = blockVolumeIngestOrchestrator;
    }

    public BlockIngestOrchestrator getBlockRemoteReplicationIngestOrchestrator() {
        return blockRemoteReplicationIngestOrchestrator;
    }

    public void setBlockRemoteReplicationIngestOrchestrator(
            BlockIngestOrchestrator blockRemoteReplicationIngestOrchestrator) {
        this.blockRemoteReplicationIngestOrchestrator = blockRemoteReplicationIngestOrchestrator;
    }

    public BlockIngestOrchestrator getBlockVplexVolumeIngestOrchestrator() {
        return blockVplexVolumeIngestOrchestrator;
    }

    public void setBlockVplexVolumeIngestOrchestrator(
            BlockIngestOrchestrator blockVplexVolumeIngestOrchestrator) {
        this.blockVplexVolumeIngestOrchestrator = blockVplexVolumeIngestOrchestrator;
    }

    public void setBlockRecoverPointIngestOrchestrator(
            BlockIngestOrchestrator blockRecoverPointIngestOrchestrator) {
        this.blockRecoverPointIngestOrchestrator = blockRecoverPointIngestOrchestrator;
    }
    
    public BlockIngestOrchestrator getBlockSnapshotIngestOrchestrator() {
        return blockSnapshotIngestOrchestrator;
    }

    public void setBlockSnapshotIngestOrchestrator(
            BlockIngestOrchestrator blockSnapshotIngestOrchestrator) {
        this.blockSnapshotIngestOrchestrator = blockSnapshotIngestOrchestrator;
    }

    public BlockIngestOrchestrator getBlockMirrorIngestOrchestrator() {
        return blockMirrorIngestOrchestrator;
    }

    public void setBlockMirrorIngestOrchestrator(
            BlockIngestOrchestrator blockMirrorIngestOrchestrator) {
        this.blockMirrorIngestOrchestrator = blockMirrorIngestOrchestrator;
    }

    public BlockIngestExportOrchestrator getMaskPerHostIngestOrchestrator() {
        return maskPerHostIngestOrchestrator;
    }

    public void setMaskPerHostIngestOrchestrator(
            BlockIngestExportOrchestrator maskPerHostIngestOrchestrator) {
        this.maskPerHostIngestOrchestrator = maskPerHostIngestOrchestrator;
    }

    public BlockIngestExportOrchestrator getMultipleMaskPerHostIngestOrchestrator() {
        return multipleMaskPerHostIngestOrchestrator;
    }

    public void setMultipleMaskPerHostIngestOrchestrator(
            BlockIngestExportOrchestrator multipleMaskPerHostIngestOrchestrator) {
        this.multipleMaskPerHostIngestOrchestrator = multipleMaskPerHostIngestOrchestrator;
    }

    public BlockIngestExportOrchestrator getUnexportedVolumeIngestOrchestrator() {
        return unexportedVolumeIngestOrchestrator;
    }

    public void setUnexportedVolumeIngestOrchestrator(
            BlockIngestExportOrchestrator unexportedVolumeIngestOrchestrator) {
        this.unexportedVolumeIngestOrchestrator = unexportedVolumeIngestOrchestrator;
    }

    public IngestStrategyFactory() {
        this.ingestStrategyMap = new HashMap<String, IngestStrategy>();
        this.ingestExportStrategyMap = new HashMap<String, IngestExportStrategy>();
    }

    public enum ReplicationStrategy {
        LOCAL, REMOTE, VPLEX, RP, RPVPLEX
    }
    
    public enum VolumeType {
        VOLUME, SNAPSHOT, CLONE, MIRROR
    }

    public enum IngestExportStrategyEnum {
        /*
         * MASK_PER_HOST :
         * Arrays whose existing masking containers cannot be modeled as export mask in ViPR DB
         * are candidates for this mask per host behavior.
         * Here, during provisioning ViPR creates a logical container Export mask for each Host to get exported
         * through ViPR.Its guaranteed that there will be always only 1 export mask available in ViPR Db at any
         * point of time.
         * 
         * XtremIO,HDS are examples.
         */
        MASK_PER_HOST("xtremio,hds"),
        /*
         * MULTIPLE_MASK_PER_HOST :
         * Arrays whose existing masking containers can be modeled to export mask in ViPR DB
         * are candidates for this multiple mask per host behavior.
         * Here, during provisioning ViPR creates an export mask object for every masking container
         * found in the Array. There is no restriction of one export mask per host , as the export masks created in
         * ViPR DB are actually a replica of what's there in Array.
         * 
         * VMAX,VNX Block are examples
         */
        MULTIPLE_MASK_PER_HOST("vmax,vnxblock,vplex"),
        NO_MASK("vnxe");

        private String ingestStrategy;

        IngestExportStrategyEnum(String ingestStrategy) {
            this.ingestStrategy = ingestStrategy;
        }

        public String getIngestStrategy() {
            return ingestStrategy;
        }

        public static IngestExportStrategyEnum getIngestStrategy(String strategyName) {
            for (IngestExportStrategyEnum strategy : copyOfValues) {
                if (strategy.getIngestStrategy().contains(strategyName)) {
                    return strategy;
            }
            }
            return null;
        }

        private static final IngestExportStrategyEnum[] copyOfValues = values();

    }

    public enum IngestStrategyEnum {
        LOCAL_VOLUME,
        LOCAL_SNAPSHOT,
        LOCAL_MIRROR,
        LOCAL_CLONE,
        REMOTE_VOLUME,
        VPLEX_VOLUME,
        RP_VOLUME,
        NONE;

        public static IngestStrategyEnum getIngestStrategy(String strategyName) {
            _logger.debug("looking for a strategy for strategy name: " + strategyName);
            for (IngestStrategyEnum strategy : copyOfValues) {
                if (strategy.name().equals(strategyName)) {
                    return strategy;
                }
            }
            return NONE;
        }

        private static final IngestStrategyEnum[] copyOfValues = values();

    }
    
    public IngestExportStrategy getIngestExportStrategy(IngestExportStrategyEnum strategyEnum) {
        IngestExportStrategy ingestStrategy = new IngestExportStrategy();
        ingestStrategy.setDbClient(_dbClient);
        switch (strategyEnum) {
        /*
         * MASK_PER_HOST:
         * Ingest Block Object, where the masking containers on Array CANNOT be
         * modeled as Export mask in ViPR.
         * 
         * Eg: Ingest Exported HDS Remote Replicated Volume into ViPR
         */
      case MASK_PER_HOST:
          ingestStrategy.setIngestExportOrchestrator(maskPerHostIngestOrchestrator);
          break;
          
          /*
           * MULTIPLE_MASK_PER_HOST:
           * Ingest Block Object, where the masking containers on Array CAN be
           * modeled as Export mask in ViPR.
           * 
           * Eg: Ingest Exported VMAX Remote Replicated Volume (SRDF) into ViPR
           */
      case MULTIPLE_MASK_PER_HOST:
          ingestStrategy.setIngestExportOrchestrator(multipleMaskPerHostIngestOrchestrator);
          break;
          
      case NO_MASK:
          ingestStrategy.setIngestExportOrchestrator(unexportedVolumeIngestOrchestrator);
          break;

      default:
          break;
        }
        
        return ingestStrategy;
    }

    /**
     * Based on the strategy key, ingest strategy object will be associated
     * with corresponding ingestResource and ingestExport orchestrators.
     * 
     * @param strategyEnum
     * @return
     */
    public IngestStrategy getIngestStrategy(IngestStrategyEnum strategyEnum) {

        IngestStrategy ingestStrategy = new IngestStrategy();
        ingestStrategy.setDbClient(_dbClient);
        switch (strategyEnum) {
          
        case REMOTE_VOLUME:
            ingestStrategy.setIngestResourceOrchestrator(blockRemoteReplicationIngestOrchestrator);
            break;
            
        case LOCAL_CLONE:
        case LOCAL_VOLUME:
            ingestStrategy.setIngestResourceOrchestrator(blockVolumeIngestOrchestrator);
            break;
            
        case LOCAL_SNAPSHOT:
            ingestStrategy.setIngestResourceOrchestrator(blockSnapshotIngestOrchestrator);
            break;
            
        case LOCAL_MIRROR:
            ingestStrategy.setIngestResourceOrchestrator(blockMirrorIngestOrchestrator);
            break;
        
        case VPLEX_VOLUME:
            ingestStrategy.setIngestResourceOrchestrator(blockVplexVolumeIngestOrchestrator);
            break;

        case RP_VOLUME:
            ingestStrategy.setIngestResourceOrchestrator(blockRecoverPointIngestOrchestrator);
            break;

        default:
            break;

        }
        return ingestStrategy;

    }

    /**
     * Retrieves the proper ingestion strategy for the given UnManagedVolume.
     * 
     * @param unManagedVolume unmanaged volume
     * @param disregardProtection disregard RP properties when determining strategy (required when RP orch. is ingesting backing vols)
     * @return ingestion strategy
     */
    public IngestStrategy buildIngestStrategy(UnManagedVolume unManagedVolume, boolean disregardProtection) {
        String remoteMirrorEnabledInVolume = unManagedVolume.getVolumeCharacterstics().get(
                SupportedVolumeCharacterstics.REMOTE_MIRRORING.toString());

        String replicationStrategy;
        
        boolean isVplexVolume = VolumeIngestionUtil.isVplexVolume(unManagedVolume);
        boolean isRpEnabled = VolumeIngestionUtil.checkUnManagedResourceIsRecoverPointEnabled(unManagedVolume);
//        
//        if (isVplexVolume && isRpEnabled) {
//            replicationStrategy = ReplicationStrategy.RPVPLEX.name();
//        } else 
            
        if (!disregardProtection && isRpEnabled) {
            replicationStrategy = ReplicationStrategy.RP.name();
        } else if (isVplexVolume) {
            replicationStrategy = ReplicationStrategy.VPLEX.name();
        } else if (null == remoteMirrorEnabledInVolume || !Boolean.parseBoolean(remoteMirrorEnabledInVolume)) {
            replicationStrategy = ReplicationStrategy.LOCAL.name();
        } else {
            replicationStrategy = ReplicationStrategy.REMOTE.name();
        }
        // Since a VPLEX backend volume may also be a snapshot target volume, we want to
        // make sure we use the local volume ingest strategy when the volume is a VPLEX backend
        // volume.
        String volumeType = VolumeType.VOLUME.name();
        if ((VolumeIngestionUtil.isSnapshot(unManagedVolume)) && (!VolumeIngestionUtil.isVplexBackendVolume(unManagedVolume))) {
            volumeType = VolumeType.SNAPSHOT.name();
        } else if (VolumeIngestionUtil.isMirror(unManagedVolume)) {
            volumeType = VolumeType.MIRROR.name();
        }
        
        String strategyKey = replicationStrategy + "_" + volumeType;
        _logger.info("strategy key is " + strategyKey);

        if (null == ingestStrategyMap.get(strategyKey)) {
            IngestStrategy strategy = getIngestStrategy(IngestStrategyEnum.getIngestStrategy(strategyKey));
            _logger.debug("ingest strategy map does not contain key, adding " + strategyKey + " for " + strategy);
            ingestStrategyMap.put(strategyKey, strategy);
        }

        return ingestStrategyMap.get(strategyKey);
    }
    
    public IngestExportStrategy buildIngestExportStrategy(UnManagedVolume unManagedVolume) {
        boolean isVolumeExported = Boolean.parseBoolean(unManagedVolume.getVolumeCharacterstics().get(
                SupportedVolumeCharacterstics.IS_VOLUME_EXPORTED.toString()));
        // being RP enabled implies the volume is exported to the RP device
        boolean isRpEnabled = VolumeIngestionUtil.checkUnManagedResourceIsRecoverPointEnabled(unManagedVolume);
        String systemType = PropertySetterUtil.extractValueFromStringSet(SupportedVolumeInformation.SYSTEM_TYPE.toString(),
                unManagedVolume.getVolumeInformation());
        _logger.info("system type is " + systemType);
        
        IngestExportStrategyEnum exportStrategy = IngestExportStrategyEnum.NO_MASK;
        if (isVolumeExported || isRpEnabled) {
            exportStrategy = IngestExportStrategyEnum.getIngestStrategy(systemType);
        }
        _logger.info("export strategy is " + exportStrategy.name());
        
        if (null == ingestExportStrategyMap.get(exportStrategy.name())) {
            IngestExportStrategy strategy = getIngestExportStrategy(exportStrategy);
            _logger.info("ingest strategy map does not contain key, adding " + exportStrategy + " for " + strategy);
            ingestExportStrategyMap.put(exportStrategy.name(), strategy);
        }
        
        return ingestExportStrategyMap.get(exportStrategy.name());
    }

}
