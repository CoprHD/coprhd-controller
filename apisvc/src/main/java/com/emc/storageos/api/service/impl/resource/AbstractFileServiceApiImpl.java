/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.api.service.impl.resource;

import java.net.URI;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import com.emc.storageos.Controller;
import com.emc.storageos.api.service.authorization.PermissionsHelper;
import com.emc.storageos.coordinator.client.service.CoordinatorClient;
import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.DataObject;
import com.emc.storageos.db.client.model.Project;
import com.emc.storageos.db.client.model.TenantOrg;
import com.emc.storageos.db.client.model.VirtualArray;
import com.emc.storageos.db.client.model.VirtualPool;
import com.emc.storageos.db.common.DependencyChecker;
import com.emc.storageos.fileorchestrationcontroller.FileDescriptor;
import com.emc.storageos.fileorchestrationcontroller.FileOrchestrationController;
import com.emc.storageos.model.TaskList;
import com.emc.storageos.model.file.FileSystemParam;
import com.emc.storageos.svcs.errorhandling.resources.APIException;
import com.emc.storageos.svcs.errorhandling.resources.InternalException;
import com.emc.storageos.volumecontroller.Recommendation;
import com.emc.storageos.volumecontroller.impl.utils.VirtualPoolCapabilityValuesWrapper;

public abstract class AbstractFileServiceApiImpl<T> implements FileServiceApi {
    private static final Logger s_logger = LoggerFactory.getLogger(AbstractFileServiceApiImpl.class);

    @Autowired
    private PermissionsHelper _permissionsHelper;
    @Autowired
    protected DependencyChecker _dependencyChecker;
    protected T _scheduler;
    protected DbClient _dbClient;
    private CoordinatorClient _coordinator;

    // Permissions helper getter/setter
    public void setPermissionsHelper(PermissionsHelper permissionsHelper) {
        _permissionsHelper = permissionsHelper;
    }

    public PermissionsHelper getPermissionsHelper() {
        return _permissionsHelper;
    }

    // Dependency checker getter/setter
    public void setDependencyChecker(DependencyChecker dependencyChecker) {
        _dependencyChecker = dependencyChecker;
    }

    public DependencyChecker getDependencyChecker() {
        return _dependencyChecker;
    }

    // Coordinator getter/setter
    public void setCoordinator(CoordinatorClient locator) {
        _coordinator = locator;
    }

    public CoordinatorClient getCoordinator() {
        return _coordinator;
    }

    // Db client getter/setter
    public void setDbClient(DbClient dbClient) {
        _dbClient = dbClient;
    }

    public DbClient getDbClient() {
        return _dbClient;
    }

    // StorageScheduler getter/setter
    public void setFileScheduler(T scheduler) {
        _scheduler = scheduler;
    }

    public T getFileScheduler() {
        return _scheduler;
    }

    /**
     * Map of implementing class instances; used for iterating through them for
     * connectivity purposes.
     */
    static private Map<String, AbstractFileServiceApiImpl> s_protectionImplementations = new HashMap<String, AbstractFileServiceApiImpl>();

    public AbstractFileServiceApiImpl(String protectionType) {
        if (protectionType != null) {
            s_protectionImplementations.put(protectionType, this);
        }
    }

    static protected Map<String, AbstractFileServiceApiImpl> getProtectionImplementations() {
        return s_protectionImplementations;
    }

    /**
     * Check if a resource can be deactivated safely
     * 
     * @return detail type of the dependency if exist, null otherwise
     * @throws InternalException
     */
    @Override
    public <T extends DataObject> String checkForDelete(T object) throws InternalException {
        String depMsg = getDependencyChecker().checkDependencies(object.getId(), object.getClass(), true);
        if (depMsg != null) {
            return depMsg;
        }
        return object.canBeDeleted();
    }

    /**
     * Looks up controller dependency for given hardware
     * 
     * @param clazz controller interface
     * @param hw hardware name
     * @param <T>
     * @return
     */
    protected <T extends Controller> T getController(Class<T> clazz, String hw) {
        return _coordinator.locateService(clazz, CONTROLLER_SVC, CONTROLLER_SVC_VER, hw, clazz.getSimpleName());
    }

    @Override
    public TaskList createFileSystems(FileSystemParam param, Project project,
            VirtualArray varray, VirtualPool vpool, TenantOrg tenantOrg, DataObject.Flag[] flags,
            List<Recommendation> recommendations, TaskList taskList,
            String task, VirtualPoolCapabilityValuesWrapper vpoolCapabilities)
            throws InternalException {
        throw APIException.methodNotAllowed.notSupported();

    }

    @Override
    public void deleteFileSystems(URI systemURI, List<URI> fileSystemURIs,
            String deletionType, boolean forceDelete, String task) throws InternalException {
        // Get volume descriptor for all volumes to be deleted.
        List<FileDescriptor> fileDescriptors = getDescriptorsOfFileShareDeleted(
                systemURI, fileSystemURIs, deletionType, forceDelete);
        // place request in queue
        FileOrchestrationController controller = getController(
                FileOrchestrationController.class,
                FileOrchestrationController.FILE_ORCHESTRATION_DEVICE);
        controller.deleteFileSystems(fileDescriptors, task);
    }

    /**
     * get delete file share Descriptors
     * 
     * @param systemURI
     * @param fileShareURIs
     * @param deletionType
     * @param forceDelete
     * @return
     */
    abstract protected List<FileDescriptor> getDescriptorsOfFileShareDeleted(URI systemURI,
            List<URI> fileShareURIs, String deletionType, boolean forceDelete);

}
