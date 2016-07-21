/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.validators.smis.vnx;

import static com.google.common.collect.Collections2.transform;

import java.util.Collection;
import java.util.Set;

import javax.cim.CIMInstance;
import javax.cim.CIMObjectPath;
import javax.wbem.CloseableIterator;
import javax.wbem.WBEMException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.model.ExportMask;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.volumecontroller.impl.smis.CIMPropertyFactory;
import com.emc.storageos.volumecontroller.impl.validators.smis.AbstractSMISValidator;
import com.google.common.base.Function;
import com.google.common.base.Joiner;
import com.google.common.collect.Sets;

public abstract class VnxExportMaskValidator extends AbstractSMISValidator {

    private static final Logger log = LoggerFactory.getLogger(VnxExportMaskValidator.class);
    private static final String NO_MATCH = "<no match>";

    private final StorageSystem storage;
    private final ExportMask exportMask;
    private final String field;

    public VnxExportMaskValidator(StorageSystem storage, ExportMask exportMask, String field) {
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
        CloseableIterator<CIMInstance> associatedResources = null;

        try {
            CIMObjectPath maskingViewPath = getCimPath().getLunMaskingProtocolControllerPath(storage, exportMask);
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

}
