/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.validators.smis.vmax;

import static com.google.common.collect.Collections2.transform;

import java.util.Collection;
import java.util.Set;

import javax.cim.CIMObjectPath;
import javax.wbem.CloseableIterator;
import javax.wbem.WBEMException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.model.ExportMask;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.volumecontroller.impl.validators.smis.AbstractSMISValidator;
import com.google.common.base.Function;
import com.google.common.base.Joiner;
import com.google.common.collect.Sets;

/**
 * Abstract template class for comparing a set of ExportMask-related database values against hardware values.
 * If any additional differences are detected on the hardware (i.e. SMI-S) side, then a validation error would be
 * logged.
 */
public abstract class ExportMaskValidator extends AbstractSMISValidator {

    private static final Logger log = LoggerFactory.getLogger(ExportMaskValidator.class);
    private static final String NO_MATCH = "<N/A>";

    private StorageSystem storage;
    private ExportMask exportMask;
    private String field;

    public ExportMaskValidator(StorageSystem storage, ExportMask exportMask, String field) {
        this.storage = storage;
        this.exportMask = exportMask;
        this.field = field;
    }

    @Override
    public boolean validate() throws Exception {
        log.info("Validating export mask: {}", exportMask.getId());
        getLogger().setLog(log);

        // Refresh the provider's view of the storage system
        getHelper().callRefreshSystem(storage);

        Set<String> database = getDatabaseResources();
        Set<String> hardware = getHardwareResources();

        log.info("Database has: {}", Joiner.on(",").join(database));
        log.info("{} has: {}", storage.getSerialNumber(), Joiner.on(",").join(hardware));

        // Get all items in hardware that are not contained in the database, i.e. resources unknown to the database.
        Set<String> differences = Sets.difference(hardware, database);

        for (String diff : differences) {
            getLogger().logDiff(exportMask.getId().toString(), field, NO_MATCH, diff);
        }

        return true;
    }

    protected abstract String getAssociatorProperty();

    protected abstract String getAssociatorClass();

    protected abstract Function<? super String, String> getHardwareTransformer();

    protected abstract Set<String> getDatabaseResources();

    private Set<String> getHardwareResources() {
        Set<String> hardware = Sets.newHashSet();
        CloseableIterator<CIMObjectPath> associatedResources = null;

        try {
            CIMObjectPath maskingViewPath = getCimPath().getMaskingViewPath(storage, exportMask.getMaskName());
            associatedResources = getHelper().getAssociatorNames(storage, maskingViewPath, null, getAssociatorClass(),
                    null, null);

            while (associatedResources.hasNext()) {
                CIMObjectPath assoc = associatedResources.next();
                String assocProperty = (String) assoc.getKeyValue(getAssociatorProperty());
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
}
