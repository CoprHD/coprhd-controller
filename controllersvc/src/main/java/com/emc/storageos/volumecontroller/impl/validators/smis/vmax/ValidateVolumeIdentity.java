/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.validators.smis.vmax;

import com.emc.storageos.cimadapter.connections.cim.CimConnection;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.model.Volume;
import com.emc.storageos.volumecontroller.impl.validators.smis.AbstractSMISValidator;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.cim.CIMInstance;
import javax.cim.CIMObjectPath;
import javax.cim.CIMProperty;
import javax.wbem.client.WBEMClient;
import java.util.Collection;

import static com.emc.storageos.volumecontroller.impl.smis.SmisConstants.CP_WWN_NAME;
import static com.google.common.base.Strings.isNullOrEmpty;

/**
 * Checks for differences in volume identity between ViPR DB and SMI-S.
 */
public class ValidateVolumeIdentity extends AbstractSMISValidator {

    private static final Logger log = LoggerFactory.getLogger(ValidateVolumeIdentity.class);
    private static final String[] PROP_KEYS = new String[] { CP_WWN_NAME };

    private StorageSystem system;
    private Collection<Volume> volumes;

    public ValidateVolumeIdentity(StorageSystem system, Collection<Volume> volumes) {
        this.system = system;
        this.volumes = volumes;
    }

    @Override
    public boolean validate() throws Exception {
        getLogger().setLog(log);
        for (Volume volume : volumes) {
            CIMObjectPath volumePath = getCimPath().getBlockObjectPath(system, volume);
            CimConnection connection = getHelper().getConnection(system);
            WBEMClient cimClient = connection.getCimClient();
            CIMInstance instance = cimClient.getInstance(volumePath, false, false, PROP_KEYS);

            checkForDifferences(instance, volume);
        }

        return getLogger().hasErrors();
    }

    private void checkForDifferences(CIMInstance instance, Volume volume) {
        for (String propKey : PROP_KEYS) {
            CIMProperty<?> property = instance.getProperty(propKey);
            if (property == null) {
                log.warn("Skipping property check for %s on %s as it was null", propKey, volume.getId());
                continue;
            }

            switch (propKey) {
                case CP_WWN_NAME:
                    validateWWN(property, volume);
                    break;
            }

        }
    }

    private void validateWWN(CIMProperty<?> property, Volume volume) {
        String hwWWN = (String) property.getValue();
        String dbWWN = volume.getWWN();
        if (!isNullOrEmpty(dbWWN) && !dbWWN.equalsIgnoreCase(hwWWN)) {
            String id = String.format("(%s/%s)", volume.getNativeGuid(), volume.getId());
            getLogger().logDiff(id, "wwn", dbWWN, hwWWN);
        }
    }
}
