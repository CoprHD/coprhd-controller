/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.validators.smis.vmax;

import com.emc.storageos.db.client.model.ExportMask;
import com.emc.storageos.db.client.model.Initiator;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.model.StringMap;
import com.google.common.collect.Lists;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.cim.CIMObjectPath;
import java.net.URI;
import java.util.Collection;
import java.util.List;

import static com.emc.storageos.db.client.util.CommonTransformerFunctions.FCTN_VOLUME_URI_TO_STR;
import static com.google.common.collect.Collections2.transform;

/**
 * Sub-class for {@link AbstractMultipleVmaxMaskValidator} in order to validate that a given
 * {@link Initiator} is not shared by another masking view out of management.
 */
public class MultipleVmaxMaskForInitiatorsValidator extends AbstractMultipleVmaxMaskValidator<Initiator> {

    private static final Logger log = LoggerFactory.getLogger(MultipleVmaxMaskForInitiatorsValidator.class);

    /**
     * Default constructor.
     *
     * @param storage     StorageSystem
     * @param exportMask  ExportMask
     * @param initiators  List of dataObjects to check.
     *                    May be null, in which case all user added initiators from {@code exportMask} is used.
     */
    public MultipleVmaxMaskForInitiatorsValidator(StorageSystem storage, ExportMask exportMask,
                                                  Collection<Initiator> initiators) {
        super(storage, exportMask, initiators);
    }

    @Override
    protected String getFriendlyId(Initiator initiator) {
        return String.format("%s/%s", initiator.getId(), initiator.getInitiatorPort());
    }

    @Override
    protected CIMObjectPath getCIMObjectPath(Initiator obj) throws Exception {
        try {
            CIMObjectPath[] initiatorPaths = getCimPath().getInitiatorPaths(storage, new String[]{});
            if (initiatorPaths != null && initiatorPaths.length > 0) {
                return initiatorPaths[0];
            }
        } catch (Exception e) {
            log.error("Exception occurred getting Initiator CIM object path: {}", obj.getId());
            throw e;
        }
        return null;
    }

    @Override
    protected Collection<Initiator> getDataObjects() {
        if (dataObjects != null) {
            checkRequestedInitiatorsBelongToMask();
            return dataObjects;
        }

        StringMap userAddedInitiators = exportMask.getUserAddedInitiators();
        List<URI> initURIs = Lists.newArrayList();

        for (String initURI : userAddedInitiators.values()) {
            initURIs.add(URI.create(initURI));
        }

        dataObjects = getDbClient().queryObject(Initiator.class, initURIs);
        return dataObjects;
    }

    private void checkRequestedInitiatorsBelongToMask() {
        StringMap userAddedInitiators = exportMask.getUserAddedInitiators();

        if (userAddedInitiators == null) {
            return;
        }

        Collection<String> maskInitiatorURIs = userAddedInitiators.values();
        Collection<String> reqInitiatorURIs = transform(dataObjects, FCTN_VOLUME_URI_TO_STR);

        for (String reqVolumeURI : reqInitiatorURIs) {
            if (!maskInitiatorURIs.contains(reqVolumeURI)) {
                String msg = String.format("Requested initiator %s does not belong in mask %s", reqVolumeURI, exportMask);
                throw new IllegalArgumentException(msg);
            }
        }
    }
}
