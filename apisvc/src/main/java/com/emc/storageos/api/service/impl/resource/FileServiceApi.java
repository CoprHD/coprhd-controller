package com.emc.storageos.api.service.impl.resource;

import java.net.URI;
import java.util.List;

import com.emc.storageos.db.client.model.*;
import com.emc.storageos.model.TaskList;
import com.emc.storageos.model.file.FileSystemParam;
import com.emc.storageos.svcs.errorhandling.resources.InternalException;
import com.emc.storageos.volumecontroller.ControllerException;
import com.emc.storageos.volumecontroller.Recommendation;
import com.emc.storageos.volumecontroller.impl.utils.VirtualPoolCapabilityValuesWrapper;


public interface FileServiceApi {
	public static final String DEFAULT = "default";

    public static final String CONTROLLER_SVC = "controllersvc";
    public static final String CONTROLLER_SVC_VER = "1";
    public static final String EVENT_SERVICE_TYPE = "file";
    
	 /**
     * Define the default FileServiceApi implementation.
     */
	
	/**
     * Create filesystems
     *
     * @param param The filesystem creation post parameter
     * @param project project requested
     * @param varray source VirtualArray
     * @param vpool VirtualPool requested
     * @param recommendations Placement recommendation object
     * @param taskList list of tasks for source filesystems
     * @param task task ID
     * @param vpoolCapabilities wrapper for vpool params
     * @return TaskList
     *
     * @throws InternalException
     */
    public TaskList createFileSystems(FileSystemParam param, Project project,
                                  VirtualArray varray, VirtualPool vpool, List<Recommendation> recommendations,
                                  TaskList taskList, String task, VirtualPoolCapabilityValuesWrapper vpoolCapabilities)
            throws InternalException;

    /**
     * Delete the passed filesystems for the passed system.
     *
     * @param systemURI URI of the system owing the filesystems.
     * @param fileSystemURIs The URIs of the filesystems to be deleted.
     * @param deletionType The type of deletion to perform.
     * @param task The task identifier.
     *
     * @throws InternalException
     */
    public void deleteFileSystems(URI systemURI, List<URI> fileSystemURIs, String deletionType,
                              String task) throws InternalException;
    
    
    /**
     * Check if a resource can be deactivated safely.
     *
     * @return detail type of the dependency if exist, null otherwise
     *
     * @throws InternalException
     */
    public <T extends DataObject> String checkForDelete(T object) throws InternalException;
    
    
    

}
