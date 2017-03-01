/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.validators.smis.vmax;

import com.emc.storageos.db.client.constraint.AlternateIdConstraint;
import com.emc.storageos.db.client.constraint.ContainmentConstraint;
import com.emc.storageos.db.client.model.BlockObject;
import com.emc.storageos.db.client.model.ExportGroup;
import com.emc.storageos.db.client.model.ExportMask;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.model.StringMap;
import com.emc.storageos.db.client.util.CustomQueryUtility;
import com.emc.storageos.volumecontroller.impl.smis.SmisConstants;
import com.emc.storageos.volumecontroller.impl.utils.ExportMaskUtils;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.cim.CIMInstance;
import javax.cim.CIMObjectPath;
import javax.wbem.CloseableIterator;
import javax.wbem.WBEMException;
import java.net.URI;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import static com.emc.storageos.db.client.util.CommonTransformerFunctions.FCTN_VOLUME_URI_TO_STR;
import static com.google.common.collect.Collections2.transform;
import static java.lang.String.format;

/**
 * Sub-class for {@link AbstractMultipleVmaxMaskValidator} in order to validate that a given
 * {@link BlockObject} is not shared by another masking view out of management.
 */
class MultipleVmaxMaskForVolumesValidator<T extends BlockObject> extends AbstractMultipleVmaxMaskValidator<T> {

    private static final Logger log = LoggerFactory.getLogger(MultipleVmaxMaskForVolumesValidator.class);

    /**
     * Default constructor.
     *
     * @param storage      StorageSystem
     * @param exportMask   ExportMask
     * @param blockObjects List of blockobjects to check.
     *                     May be null, in which case all user added volumes from {@code exportMask} is used.
     */
    MultipleVmaxMaskForVolumesValidator(StorageSystem storage, ExportMask exportMask, Collection<T> blockObjects) {
        super(storage, exportMask, blockObjects);
    }

    /**
     * Validation should fail if:
     * 1. The associated mask has no ViPR export group
     * 2. The storage group is shared between them
     *
     * @param mask      The export mask in the ViPR request
     * @param assocMask An export mask found to be associated with {@code mask}.
     * @return          True, if validation passed, false otherwise.
     */
    @Override
    protected boolean validate(BlockObject blockObject, CIMInstance mask, CIMInstance assocMask) throws WBEMException {
        boolean isSharingStorageGroups;
        boolean assocMaskHasExportGroup = false;
        String name = (String) mask.getPropertyValue(SmisConstants.CP_DEVICE_ID);
        String assocName = (String) assocMask.getPropertyValue(SmisConstants.CP_DEVICE_ID);

        // Does ViPR know about this other mask?
        ExportMask em = ExportMaskUtils.getExportMaskByName(getDbClient(), storage.getId(), assocName);

        if (em != null) {
            log.info("MV {} is tracked by {}", assocName, em.getId());
            // Check if it's part of an ExportGroup
            List<ExportGroup> exportGroups = CustomQueryUtility.queryActiveResourcesByConstraint(getDbClient(),
                    ExportGroup.class, ContainmentConstraint.Factory.getExportMaskExportGroupConstraint(em.getId()));
            assocMaskHasExportGroup = !exportGroups.isEmpty();
            log.info("MV {} has {} export group(s)", assocName, exportGroups.size());
        } else {
            log.info("MV {} is not tracked by any ExportMask", assocName);
        }

        // True, if associated export mask has no export group and it's sharing a storage group.
        isSharingStorageGroups = isSharingStorageGroups(mask, assocMask);
        log.info(format("MV %s is sharing a storage group with %s?  %s", assocName, name, isSharingStorageGroups));

        /*
         * FIXME COP-24841 - Stop making dangerous assumptions about impacted masking views.
         *
         * Here, we may allow validation to pass based simply on the associated ExportMask being part of
         * an ExportGroup.  We assume that the orchestration layer has also generated a step to be run
         * in parallel for removing the volume from this other mask.  Instead, we should acquire
         * an explicit list of impacted masking views and consider it when determining a pass/fail result.
         */
        return assocMaskHasExportGroup || !isSharingStorageGroups;
    }

    private boolean isSharingStorageGroups(CIMInstance mask, CIMInstance assocMask) throws WBEMException {
        Set<String> maskSGs = getStorageGroupIds(mask);
        Set<String> assocMaskSGs = getStorageGroupIds(assocMask);

        // Are the 2 sets disjointed?  i.e. have no common elements
        return !Collections.disjoint(maskSGs, assocMaskSGs);
    }

    private Set<String> getStorageGroupIds(CIMInstance mask) throws WBEMException {
        CloseableIterator<CIMObjectPath> assocSG = null;
        Set<String> instanceIds = Sets.newHashSet();

        try {
            assocSG = getHelper().getAssociatorNames(storage, mask.getObjectPath(), null,
                    SmisConstants.SE_DEVICE_MASKING_GROUP, null, null);
            while (assocSG.hasNext()) {
                CIMObjectPath next = assocSG.next();
                instanceIds.add(next.getKeyValue(SmisConstants.CP_INSTANCE_ID).toString());
            }
        } finally {
            if (assocSG != null) {
                assocSG.close();
            }
        }

        return instanceIds;
    }

    @Override
    protected String getFriendlyId(BlockObject blockObject) {
        return format("%s/%s", blockObject.getId(), blockObject.getNativeGuid());
    }

    @Override
    protected CIMObjectPath getCIMObjectPath(BlockObject obj) {
        return getCimPath().getBlockObjectPath(storage, obj);
    }

    /**
     * Use this to access {@code blockObjects}, since it may be null.
     * @return  Collection of BlockObject instances.
     */
    @SuppressWarnings("unchecked")
    protected Collection<T> getDataObjects() {
        if (dataObjects != null) {
            // When first calling this method, caller has provided a list of block objects
            // so we must ensure they actually exist in the mask itself.
            checkRequestedVolumesBelongToMask();
            return dataObjects;
        }

        // Caller did not provide a list of block objects, so use ExportMask userAddedVolumes instead.
        StringMap userAddedVolumes = exportMask.getUserAddedVolumes();
        List<URI> boURIs = Lists.newArrayList();
        for (String boURI : userAddedVolumes.values()) {
            boURIs.add(URI.create(boURI));
        }

        dataObjects = (Collection<T>) BlockObject.fetchAll(getDbClient(), boURIs);
        return dataObjects;
    }

    private void checkRequestedVolumesBelongToMask() {
        StringMap userAddedVolumes = exportMask.getUserAddedVolumes();

        if (userAddedVolumes == null) {
            return;
        }

        Collection<String> masKVolumeURIs = userAddedVolumes.values();
        Collection<String> reqVolumeURIs = transform(dataObjects, FCTN_VOLUME_URI_TO_STR);

        for (String reqVolumeURI : reqVolumeURIs) {
            if (!masKVolumeURIs.contains(reqVolumeURI)) {
                String msg = format("Requested volume %s does not belong in mask %s", reqVolumeURI, exportMask);
                throw new IllegalArgumentException(msg);
            }
        }
    }
}
