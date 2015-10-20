/*
 * Copyright (c) 2008-2011 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.volumecontroller.impl;

import java.net.URI;
import java.util.List;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.Controller;
import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.DiscoveredDataObject.Type;
import com.emc.storageos.db.client.model.DiscoveredSystemObject;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.exceptions.ClientControllerException;
import com.emc.storageos.impl.AbstractDiscoveredSystemController;
import com.emc.storageos.model.file.CifsShareACLUpdateParams;
import com.emc.storageos.model.file.FileExportUpdateParams;
import com.emc.storageos.model.file.NfsACLUpdateParams;
import com.emc.storageos.svcs.errorhandling.resources.InternalException;
import com.emc.storageos.volumecontroller.AsyncTask;
import com.emc.storageos.volumecontroller.ControllerException;
import com.emc.storageos.volumecontroller.FileController;
import com.emc.storageos.volumecontroller.FileSMBShare;
import com.emc.storageos.volumecontroller.FileShareExport;
import com.emc.storageos.volumecontroller.FileShareQuotaDirectory;
import com.emc.storageos.volumecontroller.impl.ControllerServiceImpl.Lock;
import com.emc.storageos.volumecontroller.impl.monitoring.MonitoringJob;
import com.emc.storageos.volumecontroller.impl.plugins.discovery.smis.MonitorTaskCompleter;

/**
 * South bound API implementation - a singleton instance
 * of this class services all provisioning calls. Provisioning
 * calls are matched against device specific controller implementations
 * and forwarded from this implementation
 */
public class FileControllerImpl extends AbstractDiscoveredSystemController implements FileController {
    private final static Logger _log = LoggerFactory.getLogger(FileControllerImpl.class);

    // device specific FileController implementations
    private Set<FileController> _deviceImpl;
    private Dispatcher _dispatcher;
    private DbClient _dbClient;

    public void setDeviceImpl(Set<FileController> deviceImpl) {
        _deviceImpl = deviceImpl;
    }

    public void setDispatcher(Dispatcher dispatcher) {
        _dispatcher = dispatcher;
    }

    public void setDbClient(DbClient dbClient) {
        _dbClient = dbClient;
    }

    @Override
    public Controller lookupDeviceController(DiscoveredSystemObject device) {
        // dummy impl that returns the first one
        return _deviceImpl.iterator().next();
    }

    private void execFS(String methodName, Object... args) throws InternalException {
        queueTask(_dbClient, StorageSystem.class, _dispatcher, methodName, args);
    }

    @Override
    public void createFS(URI storage, URI pool, URI fs, String suggestedNativeFsId, String opId)
            throws InternalException {
        execFS("createFS", storage, pool, fs, suggestedNativeFsId, opId);
    }

    @Override
    public void delete(URI storage, URI pool, URI uri, boolean forceDelete, String deleteType, String opId)
            throws InternalException {
        execFS("delete", storage, pool, uri, forceDelete, deleteType, opId);
    }

    @Override
    public void export(URI storage, URI uri, List<FileShareExport> exports, String opId)
            throws InternalException {
        execFS("export", storage, uri, exports, opId);
    }

    @Override
    public void unexport(URI storage, URI uri, List<FileShareExport> exports, String opId)
            throws InternalException {
        execFS("unexport", storage, uri, exports, opId);
    }

    @Override
    public void share(URI storage, URI uri, FileSMBShare smbShare, String opId) throws InternalException {
        execFS("share", storage, uri, smbShare, opId);
    }

    @Override
    public void deleteShare(URI storage, URI uri, FileSMBShare smbShare, String opId) throws InternalException {
        execFS("deleteShare", storage, uri, smbShare, opId);
    }

    @Override
    public void modifyFS(URI storage, URI pool, URI fs, String opId)
            throws InternalException {
        execFS("modifyFS", storage, pool, fs, opId);
    }

    @Override
    public void expandFS(URI storage, URI fs, long size, String opId)
            throws InternalException {
        execFS("expandFS", storage, fs, size, opId);
    }

    @Override
    public void snapshotFS(URI storage, URI snapshot, URI fs, String opId)
            throws InternalException {
        execFS("snapshotFS", storage, snapshot, fs, opId);
    }

    @Override
    public void restoreFS(URI storage, URI fs, URI snapshot, String opId)
            throws InternalException {
        execFS("restoreFS", storage, fs, snapshot, opId);
    }

    @Override
    public void connectStorage(URI storage) throws InternalException {
        execFS("connectStorage", storage);
    }

    @Override
    public void disconnectStorage(URI storage) throws InternalException {
        execFS("disconnectStorage", storage);
    }

    @Override
    public void discoverStorageSystem(AsyncTask[] tasks) throws ControllerException {
        try {
            ControllerServiceImpl.scheduleDiscoverJobs(tasks, Lock.DISCOVER_COLLECTION_LOCK, ControllerServiceImpl.DISCOVERY);
        } catch (Exception e) {
            _log.error(
                    "Problem in discoverStorageSystem due to {} ",
                    e.getMessage());
            throw ClientControllerException.fatals.unableToScheduleDiscoverJobs(tasks, e);
        }
    }

    @Override
    public void scanStorageProviders(AsyncTask[] tasks)
            throws ControllerException {
        throw ClientControllerException.fatals.unableToScanSMISProviders(tasks, "FileController", null);
    }

    @Override
    public void startMonitoring(AsyncTask task, Type deviceType) throws ControllerException {
        try {
            MonitoringJob job = new MonitoringJob();
            job.setCompleter(new MonitorTaskCompleter(task));
            job.setDeviceType(deviceType);
            ControllerServiceImpl.enqueueMonitoringJob(job);
        } catch (Exception e) {
            throw ClientControllerException.fatals.unableToMonitorSMISProvider(task, deviceType.toString(), e);
        }
    }

    @Override
    public void createQuotaDirectory(URI storage, FileShareQuotaDirectory quotaDir, URI fs, String opId)
            throws InternalException {
        execFS("createQuotaDirectory", storage, quotaDir, fs, opId);
    }

    @Override
    public void updateQuotaDirectory(URI storage, FileShareQuotaDirectory quotaDir, URI fs, String opId)
            throws InternalException {
        execFS("updateQuotaDirectory", storage, quotaDir, fs, opId);
    }

    @Override
    public void deleteQuotaDirectory(URI storage, URI quotaDir, URI fs, String opId) throws InternalException {
        execFS("deleteQuotaDirectory", storage, quotaDir, fs, opId);
    }

    @Override
    public void updateExportRules(URI storage, URI fsURI, FileExportUpdateParams param,
            String opId) throws ControllerException {
        execFS("updateExportRules", storage, fsURI, param, opId);
    }

    @Override
    public void deleteExportRules(URI storage, URI fileUri, boolean allDirs, String subDir, String opId) throws ControllerException {
        execFS("deleteExportRules", storage, fileUri, allDirs, subDir, opId);
    }

    @Override
    public void updateShareACLs(URI storageURI, URI fsURI, String shareName,
            CifsShareACLUpdateParams param, String opId)
            throws ControllerException {
        execFS("updateShareACLs", storageURI, fsURI, shareName, param, opId);

    }

    @Override
    public void deleteShareACLs(URI storageURI, URI fsURI, String shareName,
            String opId) throws InternalException {

        execFS("deleteShareACLs", storageURI, fsURI, shareName, opId);

    }

    @Override
    public void updateNFSAcl(URI storageURI, URI fsURI, NfsACLUpdateParams param, String opId) throws InternalException {
        execFS("updateNFSAcl", storageURI, fsURI, param, opId);

    }

    @Override
    public void deleteNFSAcls(URI storageURI, URI fsURI, String subDir, String opId) throws InternalException {
        execFS("deleteNFSAcls", storageURI, fsURI, subDir, opId);

    }
}
