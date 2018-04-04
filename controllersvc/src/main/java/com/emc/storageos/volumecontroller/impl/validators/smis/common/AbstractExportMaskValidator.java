/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.validators.smis.common;

import static com.google.common.collect.Collections2.transform;

import java.util.Collection;
import java.util.Set;

import javax.cim.CIMInstance;
import javax.cim.CIMObjectPath;
import javax.wbem.CloseableIterator;
import javax.wbem.WBEMException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.model.DiscoveredDataObject;
import com.emc.storageos.db.client.model.ExportMask;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.volumecontroller.impl.smis.CIMPropertyFactory;
import com.emc.storageos.volumecontroller.impl.validators.ValidatorLogger;
import com.emc.storageos.volumecontroller.impl.validators.smis.AbstractSMISValidator;
import com.google.common.base.Function;
import com.google.common.base.Joiner;
import com.google.common.collect.Sets;

/**
 * Abstract template class for comparing a set of ExportMask-related database values against hardware values.
 * If any additional differences are detected on the hardware (i.e. SMI-S) side, then a validation error would be
 * logged.
 */
public abstract class AbstractExportMaskValidator extends AbstractSMISValidator {

    public static final String FIELD_INITIATORS = "initiators";
    public static final String FIELD_VOLUMES = "volumes";
    public static final String FIELD_STORAGEPORTS = "storagePorts";

    private static final Logger log = LoggerFactory.getLogger(AbstractExportMaskValidator.class);

    private final StorageSystem storage;
    private final ExportMask exportMask;
    private final String field;

    public AbstractExportMaskValidator(StorageSystem storage, ExportMask exportMask, String field) {
        this.storage = storage;
        this.exportMask = exportMask;
        this.field = field;
    }

    @Override
    public boolean validate() throws Exception {
        log.info("Validating export mask: {}, field: {}", exportMask.getId(), field);
        getLogger().setLog(log);

        // We want the latest info, but in most customer cases, array configurations don't change every 5 minutes.
        // By default we do not want to refresh and cause additional performance issues.
        // But in the case of automated test suites where we combine in-controller and out-of-controller operations,
        // we have tighter tolerances and need to run refresh.
        if (getConfig().validationRefreshEnabled()) {
            // Refresh the provider's view of the storage system
            getEmcRefreshSystemInvoker().invoke(storage);
        }

        Set<String> database = getDatabaseResources();
        Set<String> hardware = getHardwareResources();

        log.info("Database has: {}", Joiner.on(",").join(database));
        log.info("{} has: {}", storage.getSerialNumber(), Joiner.on(",").join(hardware));

        // Get all items in hardware that are not contained in the database, i.e. resources unknown to the database.
        Set<String> differences = Sets.difference(hardware, database);

        for (String diff : differences) {
            getLogger().logDiff(exportMask.getId().toString(), field, ValidatorLogger.NO_MATCHING_ENTRY, diff);
        }

        return true;
    }

    protected abstract String getAssociatorProperty();

    protected abstract String getAssociatorClass();

    protected abstract Function<? super String, String> getHardwareTransformer();

    protected abstract Set<String> getDatabaseResources();

    protected Set<String> getHardwareResources() {
        Set<String> hardware = Sets.newHashSet();
        CloseableIterator<CIMInstance> associatedResources = null;

        try {
            CIMObjectPath maskingViewPath = getMaskingView();
            String[] prop = new String[] { getAssociatorProperty() };
            associatedResources = getHelper().getAssociatorInstances(storage, maskingViewPath, null, getAssociatorClass(), null, null,
                    prop);

            while (associatedResources.hasNext()) {
                CIMInstance cimInstance = associatedResources.next();
                String assocProperty = CIMPropertyFactory.getPropertyValue(cimInstance, getAssociatorProperty());
                hardware.add(assocProperty);
            }
        } catch (WBEMException wbeme) {
            log.error("SMI-S failure", wbeme);
        } finally {
            if (associatedResources != null) {
                try {
                    associatedResources.close();
                } catch (Exception e) {
                    // ignore
                }
            }
        }

        Function<? super String, String> hardwareTransformer = getHardwareTransformer();
        Collection<String> result = hardware;
        if (hardwareTransformer != null) {
            result = transform(hardware, getHardwareTransformer());
        }
        return Sets.newHashSet(result);
    }

    private CIMObjectPath getMaskingView() {
        if (DiscoveredDataObject.Type.vmax.toString().equalsIgnoreCase(storage.getSystemType())) {
            return getCimPath().getMaskingViewPath(storage, exportMask.getMaskName());
        } else if (DiscoveredDataObject.Type.vnxblock.toString().equalsIgnoreCase(storage.getSystemType())) {
            return getCimPath().getLunMaskingProtocolControllerPath(storage, exportMask);
        }
        throw new IllegalArgumentException(
                String.format("Don't know how to get masking view for storage type: %s", storage.getSystemType()));
    }

}
