/*
 * Copyright (c) 2017 Dell EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.validators.smis.vmax;

import static com.emc.storageos.db.client.util.CommonTransformerFunctions.fctnStoragePortToNetworkId;

import java.util.Collection;
import java.util.List;
import java.util.Set;

import javax.cim.CIMInstance;
import javax.cim.CIMObjectPath;
import javax.wbem.client.WBEMClient;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.CollectionUtils;

import com.emc.storageos.db.client.model.StoragePort;
import com.emc.storageos.db.client.model.StoragePortGroup;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.volumecontroller.impl.smis.SmisConstants;
import com.emc.storageos.volumecontroller.impl.validators.ValidatorLogger;
import com.emc.storageos.volumecontroller.impl.validators.smis.AbstractSMISValidator;
import com.google.common.base.Joiner;
import com.google.common.collect.Collections2;
import com.google.common.collect.Sets;

public class ExportMaskPortGroupValidator extends AbstractSMISValidator {
    private static final Logger log = LoggerFactory.getLogger(ExportMaskPortGroupValidator.class);

    private final Collection<StoragePort> storagePorts;
    private final StoragePortGroup storagePortGroup;
    private final StorageSystem storage;

    public ExportMaskPortGroupValidator(StorageSystem storage, Collection<StoragePort> storagePorts,
            StoragePortGroup storagePortGroup) {
        this.storage = storage;
        this.storagePorts = storagePorts;
        this.storagePortGroup = storagePortGroup;
    }

    /**
     * This validation would make sure that the hardware port group has all the expected storage ports
     * 
     */
    @Override
    public boolean validate() throws Exception {
        log.info("Validating export mask port group");

        getLogger().setLog(log);
        if (storagePortGroup == null || CollectionUtils.isEmpty(storagePorts)) {
            return true;
        }
        Collection<String> expectedPorts = Collections2.transform(storagePorts, fctnStoragePortToNetworkId());
        Set<String> expectedSet = Sets.newHashSet(expectedPorts);
        Set<String> hardware = getPortGroupMembers();

        log.info("expected ports has: {}", Joiner.on(",").join(expectedSet));

        // Get all items in database, but not in the hardware.
        Set<String> differences = Sets.difference(expectedSet, hardware);

        for (String diff : differences) {
            getLogger().logDiff(storagePortGroup.getId().toString(), "port group", diff, ValidatorLogger.NO_MATCHING_ENTRY);
        }

        return true;
    }

    private Set<String> getPortGroupMembers() throws Exception {

        final CIMObjectPath portGroupPath = getCimPath().getMaskingGroupPath(storage, storagePortGroup.getLabel(),
                SmisConstants.MASKING_GROUP_TYPE.SE_TargetMaskingGroup);
        CIMInstance instance = getHelper().getInstance(storage, portGroupPath, false, false, null);
        WBEMClient client = getHelper().getConnection(storage).getCimClient();
        List<String> storagePorts = getHelper().getStoragePortsFromLunMaskingInstance(client, instance);
        log.info("port group members : {}", Joiner.on(',').join(storagePorts));
        return Sets.newHashSet(storagePorts);
    }
}
