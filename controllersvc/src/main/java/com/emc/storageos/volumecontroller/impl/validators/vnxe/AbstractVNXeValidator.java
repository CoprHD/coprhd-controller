/*
 * Copyright (c) 2017 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.validators.vnxe;

import java.net.URI;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.DiscoveredDataObject.Type;
import com.emc.storageos.db.client.model.ExportMask;
import com.emc.storageos.db.client.model.HostInterface.Protocol;
import com.emc.storageos.db.client.model.Initiator;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.exceptions.DeviceControllerException;
import com.emc.storageos.vnxe.VNXeApiClient;
import com.emc.storageos.vnxe.VNXeApiClientFactory;
import com.emc.storageos.vnxe.models.VNXeBase;
import com.emc.storageos.vnxe.models.VNXeHostInitiator;
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

    /*
     * Get VNXe hostId from ViPR initiators
     * All initiators need to be on a single host (if there is one on array)
     *
     * @return VNXe host Id
     */
    protected String getVNXeHostFromInitiators() {
        // all initiator on ViPR host should be on one host on VNXe
        DbClient dbClient = getDbClient();
        String vnxeHostId = null;
        for (String init : exportMask.getInitiators()) {
            Initiator initiator = dbClient.queryObject(Initiator.class, URI.create(init));
            if (initiator != null && !initiator.getInactive()) {
                log.info("Processing initiator {}", initiator.getLabel());
                String initiatorId = initiator.getInitiatorPort();
                if (Protocol.FC.name().equals(initiator.getProtocol())) {
                    initiatorId = initiator.getInitiatorNode() + ":" + initiatorId;
                }

                // query initiator on array
                VNXeHostInitiator vnxeInitiator = apiClient.getInitiatorByWWN(initiatorId);
                if (vnxeInitiator != null) {
                    VNXeBase parentHost = vnxeInitiator.getParentHost();
                    if (parentHost != null) {
                        if (vnxeHostId == null) {
                            vnxeHostId = parentHost.getId();
                        } else if (!vnxeHostId.equals(parentHost.getId())) {
                            log.info("ViPR initiator {} belongs to different host", initiator.getLabel());
                            getLogger().logDiff(exportMask.getId().toString(), "initiators", ValidatorLogger.NO_MATCHING_ENTRY,
                                    initiator.getLabel());
                            checkForErrors();
                        }
                    }
                }
            }
        }

        if (vnxeHostId == null) {
            log.warn("No host found for export mask {}", exportMask.getLabel());
        }

        return vnxeHostId;
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
