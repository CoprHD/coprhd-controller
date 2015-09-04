/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.db.client.model;

import java.net.URI;

/**
 * Ingestion Tasks Details
 */
@Cf("IngestionTask")
public class IngestionTask extends DataObject {

    public static final String CREATE_OP_ID = "create";
    public static final String DELETE_OP_ID = "delete";

    // hostingDeviceID where the file share is mounted
    private URI _hostingDeviceId;

    // progress marker for the ingestion task
    private String _ingestionProgress;

    // internal state of the ingestion task
    private String _internalTaskState;

    // Error associated with the ingestion task
    private String _taskError;

    // First file within the share from which an object
    // could not be created
    private String _failedFile;

    // data replicated here in addition to being stored in HosingDeviceInfo to make validation easier
    private URI _fileShareId;

    private URI _tenantId;

    private Boolean _keypoolValidated = Boolean.FALSE;

    public enum InternalTaskState {
        Initializing,
        InProgress,
        DatasvcCleanupNeeded,
        Aborting,
        AbortingNoDatasvcActionRequired,
        AbortingWaitingForDataStoreDeletion,
        DatasvcAborted,
        Error,
        Completed
    }

    // todo: enhance this to associate detailed error message and service code so that we can return the proper TaskResourceRep
    public enum TaskError {
        None("No error"),
        HostingDeviceIdNotFound("Hosting device not found while ingestion task was being processed"),
        Aborted("Received an abort request from user"),
        DataStoreCreationFailed("Data store creation failed"),
        BucketNotEmpty("Bucket used as the target of ingestion was not empty"),
        FileFailed("Ingestion failed as one or more of the files could not be accessed"),
        ReleaseFailed("Object service could not take control of the file share"),
        ImproperRelease("Object service could not take control of the file share"),
        // the following error marks that validation of file
                                                                                    // share after the release failed
        BucketNotFound("Bucket used as target for ingestion was not found");

        private String _message;

        TaskError(String message) {
            _message = message;
        }

        public String getMessage() {
            return _message;
        }
    }

    public enum EventType {
        ObjectIngestionCompleted,
        ObjectIngestionFailed
    }

    public IngestionTask() {
        super();
    }

    public IngestionTask(URI id) {
        super();
        _id = id;
    }

    @Name("hostingDeviceId")
    public URI getHostingDeviceId() {
        return _hostingDeviceId;
    }

    public void setHostingDeviceId(URI hostingDeviceId) {
        _hostingDeviceId = hostingDeviceId;
        setChanged("hostingDeviceId");
    }

    @Name("ingestionProgress")
    public String getIngestionProgress() {
        return _ingestionProgress;
    }

    public void setIngestionProgress(String ingestionProgress) {
        _ingestionProgress = ingestionProgress;
        setChanged("ingestionProgress");
    }

    @Name("internalTaskState")
    public String getInternalTaskState() {
        return _internalTaskState;
    }

    public void setInternalTaskState(String internalTaskState) {
        _internalTaskState = internalTaskState;
        setChanged("internalTaskState");
        InternalTaskState taskState = InternalTaskState.valueOf(_internalTaskState);
        switch (taskState) {
        // states that don't result in operation status change are mostly ignored here
            case Initializing:
                setOpStatus(new OpStatusMap());
                Operation createOp = new Operation();
                createOp.setDescription("Object ingestion task create operation");
                getOpStatus().put(CREATE_OP_ID, createOp);
                break;

            case Completed:
                getOpStatus().put(CREATE_OP_ID, new Operation(Operation.Status.ready.name()));
                break;

            case Error:
                getOpStatus().put(CREATE_OP_ID, new Operation(Operation.Status.error.name()));
                break;

            case Aborting:
            case AbortingNoDatasvcActionRequired:
                if (getOpStatus().get(DELETE_OP_ID) == null) {
                    Operation deleteOp = new Operation(Operation.Status.pending.name());
                    deleteOp.setDescription("Object ingestion task delete operation");
                    getOpStatus().put(DELETE_OP_ID, deleteOp);
                }
                break;
            case AbortingWaitingForDataStoreDeletion:
                getOpStatus().put(DELETE_OP_ID, new Operation(Operation.Status.pending.name()));
                break;

            default:

        }
    }

    @Name("taskError")
    public String getTaskError() {
        return _taskError;
    }

    public void setTaskError(String taskError) {
        _taskError = taskError;
        setChanged("taskError");
    }

    @Name("failedFile")
    public String getFailedFile() {
        return _failedFile;
    }

    public void setFailedFile(String failedFile) {
        _failedFile = failedFile;
        setChanged("failedFile");
    }

    @Name("keypoolValidated")
    public Boolean getKeypoolValidated() {
        return _keypoolValidated;
    }

    public void setKeypoolValidated(Boolean keypoolValidated) {
        _keypoolValidated = keypoolValidated;
        setChanged("keypoolValidated");
    }

    @Name("fileShareId")
    public URI getFileShareId() {
        return _fileShareId;
    }

    public void setFileShareId(URI fileShareId) {
        _fileShareId = fileShareId;
        setChanged("fileShareId");
    }

    @Name("tenantId")
    public URI getTenantId() {
        return _tenantId;
    }

    public void setTenantId(URI tenantId) {
        _tenantId = tenantId;
        setChanged("tenantId");
    }

}
