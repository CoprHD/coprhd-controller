/*
 * Copyright (c) 2013 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.vplexcontroller;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.emc.storageos.Controller;
import com.emc.storageos.blockorchestrationcontroller.VolumeDescriptor;
import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.DiscoveredSystemObject;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.impl.AbstractDiscoveredSystemController;
import com.emc.storageos.services.OperationTypeEnum;
import com.emc.storageos.svcs.errorhandling.resources.InternalException;
import com.emc.storageos.volumecontroller.ControllerException;
import com.emc.storageos.volumecontroller.impl.Dispatcher;

/**
 * South bound API implementation - a singleton instance of this class services
 * all vplex controller calls. Calls are matched against device specific
 * controller implementations and forwarded from this implementation.
 */
public class VPlexControllerImpl extends AbstractDiscoveredSystemController implements VPlexController {

    private Set<VPlexController> _deviceImpl;
    private Dispatcher _dispatcher;
    private DbClient _dbClient;

    public void setDeviceImpl(Set<VPlexController> deviceImpl) {
        _deviceImpl = deviceImpl;
    }

    public void setDispatcher(Dispatcher dispatcher) {
        _dispatcher = dispatcher;
    }

    public void setDbClient(DbClient dbClient) {
        _dbClient = dbClient;
    }

    @Override
    protected Controller lookupDeviceController(DiscoveredSystemObject system) {
        return _deviceImpl.iterator().next();
    }

    private void queueRequest(String methodName, Object... args) throws InternalException {
        queueTask(_dbClient, StorageSystem.class, _dispatcher, methodName, args);
    }

    @Override
    public void migrateVolumes(URI vplexURI, URI virtualVolumeURI,
            List<URI> targetVolumeURIs, Map<URI, URI> migrationsMap,
            Map<URI, URI> poolVolumeMap, URI newCoSURI, URI newNhURI, String successMsg,
            String failMsg, OperationTypeEnum opType, String opId, String wfStepId) throws InternalException {
        queueRequest("migrateVolumes", vplexURI, virtualVolumeURI, targetVolumeURIs,
                migrationsMap, poolVolumeMap, newCoSURI, newNhURI, successMsg, failMsg, opType, opId, wfStepId);
    }

    @Override
    public void importVolume(URI vplexURI, List<VolumeDescriptor> descriptors,
            URI vplexSystemProject, URI vplexSystemTenant, URI newCos, String newLabel,
            String opId) throws InternalException {
        queueRequest("importVolume", vplexURI, descriptors,
                vplexSystemProject, vplexSystemTenant, newCos, newLabel, opId);
    }

    @Override
    public void expandVolumeUsingMigration(URI vplexURI, URI vplexVolumeURI,
            List<URI> targetVolumeURIs, Map<URI, URI> migrationsMap,
            Map<URI, URI> poolVolumeMap, Long newSize, String opId)
            throws InternalException {
        queueRequest("expandVolumeUsingMigration", vplexURI, vplexVolumeURI,
                targetVolumeURIs, migrationsMap, poolVolumeMap, newSize, opId);
    }

    @Override
    public void deleteConsistencyGroup(URI vplexURI, URI cgURI, String opId)
            throws InternalException {
        queueRequest("deleteConsistencyGroup", vplexURI, cgURI, opId);
    }

    @Override
    public void updateConsistencyGroup(URI vplexURI, URI cgURI, List<URI> addVolumesList,
            List<URI> removeVolumesList, String opId) throws InternalException {
        queueRequest("updateConsistencyGroup", vplexURI, cgURI, addVolumesList,
                removeVolumesList, opId);
    }

    @Override
    public void createFullCopy(URI vplexURI, List<VolumeDescriptor> volumeDescriptors,
            String opId) throws InternalException {
        queueRequest("createFullCopy", vplexURI, volumeDescriptors, opId);
    }

    @Override
    public void restoreFromFullCopy(URI vplexURI, List<URI> fullCopyURIs, String opId)
            throws InternalException {
        queueRequest("restoreFromFullCopy", vplexURI, fullCopyURIs, opId);
    }

    @Override
    public void resyncFullCopy(URI vplexURI, List<URI> fullCopyURIs, String opId)
            throws InternalException {
        queueRequest("resyncFullCopy", vplexURI, fullCopyURIs, opId);
    }

    @Override
    public void detachFullCopy(URI vplexURI, List<URI> fullCopyURIs, String opId)
            throws InternalException {
        queueRequest("detachFullCopy", vplexURI, fullCopyURIs, opId);
    }

    @Override
    public void restoreVolume(URI vplexURI, URI snapshotURI, String opId)
            throws InternalException {
        queueRequest("restoreVolume", vplexURI, snapshotURI, opId);
    }

    @Override
    public void attachContinuousCopies(URI vplexURI, List<VolumeDescriptor> volumeDescriptors, URI sourceVolumeURI,
            String opId) throws ControllerException {
        queueRequest("attachContinuousCopies", vplexURI, volumeDescriptors, sourceVolumeURI, opId);
    }

    @Override
    public void deactivateMirror(URI vplexURI, URI mirrorURI, List<VolumeDescriptor> volumeDescriptors,
            String opId) throws InternalException {
        queueRequest("deactivateMirror", vplexURI, mirrorURI, volumeDescriptors, opId);
    }

    @Override
    public void detachContinuousCopies(URI vplexURI, URI sourceVolumeURI, List<URI> mirrors, List<URI> promotees,
            String opId) throws InternalException {
        queueRequest("detachContinuousCopies", vplexURI, sourceVolumeURI, mirrors, promotees, opId);
    }

    @Override
    public boolean validateStorageProviderConnection(String ipAddress, Integer portNumber) {
        return VPlexDeviceController.getInstance().validateStorageProviderConnection(ipAddress, portNumber);
    }
    
    @Override
    public void establishVolumeAndFullCopyGroupRelation(URI storage, URI sourceVolume, URI fullCopy, String opId) {
        queueRequest("establishVolumeAndFullCopyGroupRelation", storage, sourceVolume, fullCopy, opId);
    }

    @Override
    public void resyncSnapshot(URI vplexURI, URI snapshotURI, String opId) throws InternalException {
        queueRequest("resyncSnapshot", vplexURI, snapshotURI, opId);
    }

    @Override
    public void pauseMigration(URI vplexURI, URI migrationURI, String opId) {
        queueRequest("pauseMigration", vplexURI, migrationURI, opId);
        
    }

    @Override
    public void resumeMigration(URI vplexURI, URI migrationURI, String opId) {
        queueRequest("resumeMigration", vplexURI, migrationURI, opId);
        
    }
    
    @Override
    public void cancelMigration(URI vplexURI, URI migrationURI, String opId) {
        queueRequest("cancelMigration", vplexURI, migrationURI, opId);
        
    }
    
    @Override
    public void deleteMigration(URI vplexURI, URI migrationURI, String opId) {
        queueRequest("deleteMigration", vplexURI, migrationURI, opId);
        
    }
}
