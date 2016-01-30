/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.fileorchestrationcontroller;

import java.util.List;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.util.NullColumnValueGetter;
import com.emc.storageos.volumecontroller.ControllerException;
import com.emc.storageos.volumecontroller.impl.Dispatcher;

public class FileOrchestrationControllerImpl implements FileOrchestrationController {

    private Dispatcher _dispatcher;
    private FileOrchestrationController _controller;
    private DbClient _dbClient;

    @Override
    public void createFileSystems(List<FileDescriptor> fileDescriptors,
            String taskId) throws ControllerException {

        execOrchestration("createFileSystems", fileDescriptors, taskId);
    }

    @Override
    public void deleteFileSystems(List<FileDescriptor> fileDescriptors,
            String taskId) throws ControllerException {
        execOrchestration("deleteFileSystems", fileDescriptors, taskId);
    }

    @Override
    public void expandFileSystem(List<FileDescriptor> fileDescriptors,
            String taskId) throws ControllerException {
        execOrchestration("expandFileSystem", fileDescriptors, taskId);
    }

    @Override
    public void changeFileSystemVirtualPool(String sourceFs,
            List<FileDescriptor> fileDescriptors, String taskId) throws ControllerException {
        execOrchestration("changeFileSystemVirtualPool", sourceFs, fileDescriptors, taskId);
    }

    @Override
    public void createTargetsForExistingSource(String sourceFs,
            List<FileDescriptor> fileDescriptors, String taskId) throws ControllerException {
        execOrchestration("createTargetsForExistingSource", sourceFs, fileDescriptors, taskId);
    }

    // getter and setter methods
    public FileOrchestrationController getController() {
        return _controller;
    }

    public void setController(FileOrchestrationController controller) {
        this._controller = controller;
    }

    public Dispatcher getDispatcher() {
        return _dispatcher;
    }

    public void setDispatcher(Dispatcher dispatcher) {
        this._dispatcher = dispatcher;
    }

    public DbClient getDbClient() {
        return _dbClient;
    }

    public void setDbClient(DbClient dbClient) {
        this._dbClient = dbClient;
    }

    private void execOrchestration(String methodName, Object... args) throws ControllerException {
        _dispatcher.queue(NullColumnValueGetter.getNullURI(), FILE_ORCHESTRATION_DEVICE,
                getController(), methodName, args);
    }
}
