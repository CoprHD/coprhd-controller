/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.validators.smis.vmax;

import com.emc.storageos.db.client.constraint.AlternateIdConstraint;
import com.emc.storageos.db.client.constraint.ContainmentConstraint;
import com.emc.storageos.db.client.model.ExportGroup;
import com.emc.storageos.db.client.model.ExportMask;
import com.emc.storageos.db.client.model.Initiator;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.model.StringMap;
import com.emc.storageos.db.client.util.CustomQueryUtility;
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
     * Returns true if:
     * 1. ViPR is managing the associated mask
     * AND
     * 2. The associated mask has an export group
     *
     * @param mask      The export mask in the ViPR request
     * @param assocMask An export mask found to be associated with {@code mask}.
     * @return
     */
    @Override
    protected boolean validate(CIMInstance mask, CIMInstance assocMask) {
        boolean assocMaskHasExportGroup = false;
        String assocName = (String) assocMask.getPropertyValue(SmisConstants.CP_DEVICE_ID);

        // Does ViPR know about this other mask?
        List<ExportMask> exportMasks = CustomQueryUtility.queryActiveResourcesByConstraint(getDbClient(),
                ExportMask.class, AlternateIdConstraint.Factory.getExportMaskByNameConstraint(assocName));

        if (!exportMasks.isEmpty()) {
            for (ExportMask em : exportMasks) {
                log.info("MV {} is tracked by {}", assocName, em.getId());
                // Check if it's part of an ExportGroup
                List<ExportGroup> exportGroups = CustomQueryUtility.queryActiveResourcesByConstraint(getDbClient(),
                        ExportGroup.class, ContainmentConstraint.Factory.getExportMaskExportGroupConstraint(em.getId()));

                if (!exportGroups.isEmpty()) {
                    assocMaskHasExportGroup = true;
                    log.info("MV {} has {} export group(s)", assocName, exportGroups.size());
                    break;
                }
            }
        } else {
            log.info("MV {} is not tracked by any ExportMask", assocName);
        }

        /*
         * FIXME COP-24841 - Stop making dangerous assumptions about impacted masking views.
         *
         * Here, we allow validation to pass based simply on the associated ExportMask being part of
         * an ExportGroup.  We assume that the orchestration layer has also generated a step to be run
         * in parallel for removing the initiator from this other mask.  Instead, we should acquire
         * an explicit list of impacted masking views and consider it when determining a pass/fail result.
         */
        return assocMaskHasExportGroup;
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

        if (userAddedInitiators == null || userAddedInitiators.isEmpty()) {
            return Collections.EMPTY_LIST;
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
