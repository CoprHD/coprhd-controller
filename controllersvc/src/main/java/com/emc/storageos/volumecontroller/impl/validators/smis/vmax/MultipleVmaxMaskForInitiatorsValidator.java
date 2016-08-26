/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.validators.smis.vmax;

import com.emc.storageos.db.client.model.ExportMask;
import com.emc.storageos.db.client.model.Initiator;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.model.StringMap;
import com.emc.storageos.volumecontroller.impl.smis.SmisConstants;
import com.google.common.collect.Lists;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.cim.CIMInstance;
import javax.cim.CIMObjectPath;
import java.net.URI;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import static com.emc.storageos.db.client.util.CommonTransformerFunctions.FCTN_VOLUME_URI_TO_STR;
import static com.google.common.collect.Collections2.transform;

/**
 * Sub-class for {@link AbstractMultipleVmaxMaskValidator} in order to validate that a given
 * {@link Initiator} is not shared by another masking view out of management.
 */
class MultipleVmaxMaskForInitiatorsValidator extends AbstractMultipleVmaxMaskValidator<Initiator> {

    private static final Logger log = LoggerFactory.getLogger(MultipleVmaxMaskForInitiatorsValidator.class);
    private static final String INSTANCE_ID_PREFIX = "W-+-";

    /**
     * Default constructor.
     *
     * @param storage     StorageSystem
     * @param exportMask  ExportMask
     * @param initiators  List of dataObjects to check.
     *                    May be null, in which case all user added initiators from {@code exportMask} is used.
     */
    MultipleVmaxMaskForInitiatorsValidator(StorageSystem storage, ExportMask exportMask,
                                                  Collection<Initiator> initiators) {
        super(storage, exportMask, initiators);
    }

    /**
     * Initiators are not to be shared across multiple masks, so this should simply
     * return false.
     *
     * @param mask      The export mask in the ViPR request
     * @param assocMask An export mask found to be associated with {@code mask}
     * @return          false, always
     * @throws IllegalArgumentException if {@code mask} and {@code assocMask} are equal
     */
    @Override
    protected boolean validate(CIMInstance mask, CIMInstance assocMask) {
        if (mask.equals(assocMask)) {
            throw new IllegalArgumentException("Mask instance parameters must not be equal");
        }
        
        String name = (String) mask.getPropertyValue(SmisConstants.CP_DEVICE_ID);
        String assocName = (String) assocMask.getPropertyValue(SmisConstants.CP_DEVICE_ID);
        log.warn("MV {} is sharing an initiator with MV {}", name, assocName);
        return false;
    }

    @Override
    protected String getFriendlyId(Initiator initiator) {
        return String.format("%s/%s", initiator.getId(), initiator.getInitiatorPort());
    }

    @Override
    protected CIMObjectPath getCIMObjectPath(Initiator obj) throws Exception {
        try {
            String port = Initiator.normalizePort(obj.getInitiatorPort());
            String instanceID = String.format("%s%s", INSTANCE_ID_PREFIX, port);
            CIMObjectPath[] initiatorPaths = getCimPath().getInitiatorPaths(storage, new String[]{ instanceID });
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

        if (userAddedInitiators == null || userAddedInitiators.isEmpty()) {
            return Collections.emptyList();
        }
        
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

        for (String reqInitiatorURI : reqInitiatorURIs) {
            if (!maskInitiatorURIs.contains(reqInitiatorURI)) {
                String msg = String.format("Requested initiator %s does not belong in mask %s", reqInitiatorURI, exportMask);
                throw new IllegalArgumentException(msg);
            }
        }
    }
}
