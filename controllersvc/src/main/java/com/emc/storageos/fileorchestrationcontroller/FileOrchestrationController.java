package com.emc.storageos.fileorchestrationcontroller;

import java.net.URI;

import java.util.List;
import com.emc.storageos.Controller;
import com.emc.storageos.fileorchestrationcontroller.FileDescriptor;
import com.emc.storageos.volumecontroller.ControllerException;


public interface FileOrchestrationController extends Controller {
	public final static String FILE_ORCHESTRATION_DEVICE = "file-orchestration";

    
    public abstract void createFileSystems(List<FileDescriptor> fileDescriptors, String taskId)
                                                throws ControllerException;

    public abstract void deleteFileSystems(List<FileDescriptor> fileDescriptors, String taskId)
                                                throws ControllerException;


    public abstract void expandFileSystem(List<FileDescriptor> fileDescriptors, String taskId)
            throws ControllerException;
    


}
