package com.emc.storageos.api.service.impl.resource.blockingestorchestration.context.impl;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.api.service.impl.resource.blockingestorchestration.context.IngestionRequestContext;
import com.emc.storageos.api.service.impl.resource.blockingestorchestration.context.VolumeIngestionContext;
import com.emc.storageos.api.service.impl.resource.utils.VolumeIngestionUtil;
import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.BlockConsistencyGroup;
import com.emc.storageos.db.client.model.BlockObject;
import com.emc.storageos.db.client.model.DataObject;
import com.emc.storageos.db.client.model.ExportGroup;
import com.emc.storageos.db.client.model.ExportMask;
import com.emc.storageos.db.client.model.Volume;
import com.emc.storageos.db.client.model.UnManagedDiscoveredObjects.UnManagedVolume;

public class RpVplexVolumeIngestionContext extends RecoverPointVolumeIngestionContext {

    private static final Logger _logger = LoggerFactory.getLogger(RpVplexVolumeIngestionContext.class);

    private VplexVolumeIngestionContext _vplexVolumeIngestionContext;
    
    public RpVplexVolumeIngestionContext(UnManagedVolume unManagedVolume, 
            DbClient dbClient, IngestionRequestContext parentRequestContext) {
        super(unManagedVolume, dbClient, parentRequestContext);
        
        _vplexVolumeIngestionContext = new VplexVolumeIngestionContext(
                unManagedVolume, dbClient, parentRequestContext);
    }

    public VplexVolumeIngestionContext getVplexVolumeIngestionContext() {
        return _vplexVolumeIngestionContext;
    }

    /* (non-Javadoc)
     * @see com.emc.storageos.api.service.impl.resource.blockingestorchestration.context.impl.RecoverPointVolumeIngestionContext#commit()
     */
    @Override
    public void commit() {

        // add block consistency group to VPLEX backend volumes.
        BlockConsistencyGroup bcg = getManagedBlockConsistencyGroup();
        if (bcg != null) {
            for (BlockObject backendVolume : _vplexVolumeIngestionContext.getBlockObjectsToBeCreatedMap().values()) {
                List<String> backendVolumeGuids = _vplexVolumeIngestionContext.getBackendVolumeGuids();
                if (backendVolumeGuids.contains(backendVolume.getNativeGuid())) {
                    _logger.info("Setting BlockConsistencyGroup {} on VPLEX backend Volume {}", 
                            bcg.forDisplay(), backendVolume.forDisplay());
                    backendVolume.setConsistencyGroup(bcg.getId());
                }
            }
        }

        // if this is an RP/VPLEX that is exported to a host or cluster, add the volume to the ExportGroup
        ExportGroup rootExportGroup = getRootIngestionRequestContext().getExportGroup();
        if (rootExportGroup != null && 
                VolumeIngestionUtil.checkUnManagedResourceIsNonRPExported(getUnmanagedVolume())) {
            _logger.info("Adding RP/VPLEX virtual volume {} to ExportGroup {}", 
                    getManagedBlockObject().forDisplay(), rootExportGroup.forDisplay());
            rootExportGroup.addVolume(getManagedBlockObject().getId(), ExportGroup.LUN_UNASSIGNED);
        }

        _vplexVolumeIngestionContext.commit();
        super.commit();
    }

    /* (non-Javadoc)
     * @see com.emc.storageos.api.service.impl.resource.blockingestorchestration.context.impl.RecoverPointVolumeIngestionContext#rollback()
     */
    @Override
    public void rollback() {
        _vplexVolumeIngestionContext.rollback();
        super.rollback();
    }

    /* (non-Javadoc)
     * @see com.emc.storageos.api.service.impl.resource.blockingestorchestration.context.impl.RecoverPointVolumeIngestionContext#findCreatedBlockObject(java.lang.String)
     */
    @Override
    public BlockObject findCreatedBlockObject(String nativeGuid) {
        BlockObject blockObject = _vplexVolumeIngestionContext.findCreatedBlockObject(nativeGuid);
        if (blockObject == null) {
            blockObject = super.findCreatedBlockObject(nativeGuid);
        }
        return blockObject;
    }
    
    /* (non-Javadoc)
     * @see com.emc.storageos.api.service.impl.resource.blockingestorchestration.context.impl.RecoverPointVolumeIngestionContext#findCreatedBlockObject(java.net.URI)
     */
    @Override
    public BlockObject findCreatedBlockObject(URI uri) {
        BlockObject blockObject = _vplexVolumeIngestionContext.findCreatedBlockObject(uri);
        if (blockObject == null) {
            blockObject = super.findCreatedBlockObject(uri);
        }
        return blockObject;
    }
    
    /* (non-Javadoc)
     * @see com.emc.storageos.api.service.impl.resource.blockingestorchestration.context.impl.RecoverPointVolumeIngestionContext#findInUpdatedObjects(java.net.URI)
     */
    @Override
    public DataObject findInUpdatedObjects(URI uri) {
        DataObject dataObject = _vplexVolumeIngestionContext.findInUpdatedObjects(uri);
        if (dataObject == null) {
            dataObject = super.findInUpdatedObjects(uri);
        }
        return dataObject;
    }
    

    /* (non-Javadoc)
     * @see com.emc.storageos.api.service.impl.resource.blockingestorchestration.context.IngestionRequestContext#findExportGroup(java.lang.String)
     */
    @Override
    public ExportGroup findExportGroup(String exportGroupLabel, URI project, URI varray, URI computeResource, String resourceType) {
        ExportGroup exportGroup = _vplexVolumeIngestionContext.findExportGroup(
                exportGroupLabel, project, varray, computeResource, resourceType);
        if (exportGroup == null) {
            exportGroup = super.findExportGroup(exportGroupLabel, project, varray, computeResource, resourceType);
        }
        return exportGroup;
    }
    

    /* (non-Javadoc)
     * @see com.emc.storageos.api.service.impl.resource.blockingestorchestration.context.IngestionRequestContext#findAllNewExportMasks()
     */
    @Override
    public List<ExportMask> findAllNewExportMasks() {
        List<ExportMask> newExportMasks = new ArrayList<ExportMask>();

        newExportMasks.addAll(super.findAllNewExportMasks());
        newExportMasks.addAll(_vplexVolumeIngestionContext.findAllNewExportMasks());

        return newExportMasks;
    }

}
