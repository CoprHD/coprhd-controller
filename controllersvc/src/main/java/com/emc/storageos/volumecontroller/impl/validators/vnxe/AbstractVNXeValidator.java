/*
 * Copyright (c) 2017 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.validators.vnxe;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.DiscoveredDataObject.Type;
import com.emc.storageos.db.client.model.ExportMask;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.exceptions.DeviceControllerException;
import com.emc.storageos.vnxe.VNXeApiClient;
import com.emc.storageos.vnxe.VNXeApiClientFactory;
import com.emc.storageos.volumecontroller.impl.validators.Validator;
import com.emc.storageos.volumecontroller.impl.validators.ValidatorConfig;
import com.emc.storageos.volumecontroller.impl.validators.ValidatorLogger;
import com.emc.storageos.volumecontroller.impl.validators.contexts.ExceptionContext;

/**
 * Abstract super-class for VNXe validators, providing convenience
 * methods for external dependencies set on the factory.
 */
public abstract class AbstractVNXeValidator implements Validator {

    public static final String NO_MATCH = "<no match>";
    private static final Logger log = LoggerFactory.getLogger(AbstractVNXeValidator.class);
    private VNXeSystemValidatorFactory factory;
    private ValidatorLogger logger;

    private final StorageSystem storage;
    private final ExportMask exportMask;
    private boolean errorOnMismatch = true;
    private String id = null; // identifying string for ExportMask
    private VNXeApiClient apiClient;
    private ExceptionContext exceptionContext;
    private String remediation = ValidatorLogger.CONTACT_EMC_SUPPORT;
    private String hostId;

    public AbstractVNXeValidator(StorageSystem storage, ExportMask exportMask) {
        this.storage = storage;
        this.exportMask = exportMask;
        this.id = String.format("%s (%s)(%s)", exportMask.getMaskName(), exportMask.getNativeId(), exportMask.getId().toString());
    }

    public void setFactory(VNXeSystemValidatorFactory factory) {
        this.factory = factory;
    }

    public ValidatorLogger getLogger() {
        return logger;
    }

    public void setLogger(ValidatorLogger logger) {
        this.logger = logger;
    }

    public DbClient getDbClient() {
        return factory.getDbClient();
    }

    public VNXeApiClientFactory getClientFactory() {
        return factory.getVnxeApiClientFactory();
    }

    public VNXeApiClient getApiClient() {
        if (apiClient != null) {
            return apiClient;
        }

        if (storage.deviceIsType(Type.unity)) {
            apiClient = getClientFactory().getUnityClient(storage.getIpAddress(),
                    storage.getPortNumber(), storage.getUsername(),
                    storage.getPassword());
        } else {
            apiClient = getClientFactory().getClient(storage.getIpAddress(),
                    storage.getPortNumber(), storage.getUsername(),
                    storage.getPassword());
        }

        return apiClient;
    }

    public void setApiClient(VNXeApiClient apiClient) {
        this.apiClient = apiClient;
    }

    public ValidatorConfig getConfig() {
        return factory.getConfig();
    }

    public void setErrorOnMismatch(boolean errorOnMismatch) {
        this.errorOnMismatch = errorOnMismatch;
    }

    public void setExceptionContext(ExceptionContext exceptionContext) {
        this.exceptionContext = exceptionContext;
    }

    public void checkForErrors() {
        if (errorOnMismatch && getLogger().hasErrors() && shouldThrowException()) {
            throw DeviceControllerException.exceptions.validationError(
                    "Export Mask", getLogger().getMsgs().toString(), getRemediation());
        }
    }

    private boolean shouldThrowException() {
        return getConfig().isValidationEnabled() && (exceptionContext == null || exceptionContext.isAllowExceptions());
    }


    public String getHostId() {
        return hostId;
    }

    public void setHostId(String hostId) {
        this.hostId = hostId;
    }

    public StorageSystem getStorage() {
        return storage;
    }

    public ExportMask getExportMask() {
        return exportMask;
    }

    public String getId() {
        return id;
    }

    protected String getRemediation() {
        return remediation;
    }

    protected void setRemediation(String remediation) {
        this.remediation = remediation;
    }
}
