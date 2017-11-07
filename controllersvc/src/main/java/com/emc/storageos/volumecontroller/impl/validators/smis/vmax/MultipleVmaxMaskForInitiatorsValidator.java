/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.validators.smis.vmax;

import static com.emc.storageos.db.client.util.CommonTransformerFunctions.FCTN_VOLUME_URI_TO_STR;
import static com.google.common.collect.Collections2.transform;
import static com.google.common.collect.Lists.newArrayList;

import java.net.URI;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.cim.CIMInstance;
import javax.cim.CIMObjectPath;
import javax.cim.CIMProperty;
import javax.wbem.CloseableIterator;
import javax.wbem.WBEMException;
import javax.wbem.client.WBEMClient;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.constraint.AlternateIdConstraint;
import com.emc.storageos.db.client.constraint.QueryResultList;
import com.emc.storageos.db.client.constraint.URIQueryResultList;
import com.emc.storageos.db.client.model.ExportMask;
import com.emc.storageos.db.client.model.Initiator;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.model.StringMap;
import com.emc.storageos.volumecontroller.impl.smis.CIMPropertyFactory;
import com.emc.storageos.volumecontroller.impl.smis.SmisConstants;
import com.emc.storageos.volumecontroller.impl.utils.ExportMaskUtils;
import com.google.common.base.Joiner;
import com.google.common.collect.Sets;

/**
 * Sub-class for {@link AbstractMultipleVmaxMaskValidator} in order to validate that a given
 * {@link Initiator} is not shared by another masking view out of management.
 */
class MultipleVmaxMaskForInitiatorsValidator extends AbstractMultipleVmaxMaskValidator<Initiator> {

    private static final String FAILING_MSG = "Failing validation: %s.";

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
     * Initiators are not to be shared across multiple masks, unless ALL the following
     * criteria are met:
     *
     * <ol>
     *     <li>Associated mask is under ViPR management</li>
     *     <li>Both masks must be contained within the same ExportGroup</li>
     *     <li>Both masks must reference the same set of Initiators</li>
     * </ol>
     *
     * @param mask      The export mask in the ViPR request
     * @param assocMask An export mask found to be associated with {@code mask}
     * @return          true if validation passes, false otherwise.
     * @throws WBEMException 
     * @throws IllegalArgumentException if {@code mask} and {@code assocMask} are equal
     */
    @Override
    protected boolean validate(Initiator initiator, CIMInstance mask, CIMInstance assocMask) throws WBEMException {
        if (mask.equals(assocMask)) {
            throw new IllegalArgumentException("Mask instance parameters must not be equal");
        }
        
        String name = (String) mask.getPropertyValue(SmisConstants.CP_DEVICE_ID);
        String assocName = (String) assocMask.getPropertyValue(SmisConstants.CP_DEVICE_ID);

        log.info("MV {} is sharing an initiator with MV {}", name, assocName);

        ExportMask exportMask = ExportMaskUtils.getExportMaskByName(getDbClient(), storage.getId(), assocName);

        // Associated mask is under ViPR management
        if (exportMask == null || hasExistingVolumes(getVolumesFromLunMaskingInstance(assocMask),exportMask)) {
            logFailure("associated mask is not under ViPR management");
            return false;
        }

        return true;
    }
    
    private boolean hasExistingVolumes(Set<String> volumesFromLunMaskingInstance, ExportMask exportMask) {
        // TODO Auto-generated method stub
        for(String volumeWWN : volumesFromLunMaskingInstance) {
            if(!exportMask.hasUserAddedVolume(volumeWWN)) {
                log.info("Mask {} has existing volumes", exportMask.getMaskName());
                return true;
            }
        }
        return false;
    }

    /**
     * Get Volumes from the Masking view.
     * @param client
     * @param instance
     * @return
     * @throws WBEMException
     */
    public Set<String> getVolumesFromLunMaskingInstance(CIMInstance instance) throws WBEMException {
        Set<String> wwnList = new HashSet<String>();
        CloseableIterator<CIMInstance> iterator = null;
        
        try {
            log.info(String.format("getVolumesFromLunMaskingInstance(%s)", instance.getObjectPath().toString()));
            iterator = getHelper().getAssociatorInstances(storage, instance.getObjectPath(), null, SmisConstants.CIM_STORAGE_VOLUME, null, null,
                    SmisConstants.PS_EMCWWN);
            while (iterator.hasNext()) {
                CIMInstance cimInstance = iterator.next();
                String wwn = CIMPropertyFactory.getPropertyValue(cimInstance, SmisConstants.CP_WWN_NAME);
                wwnList.add(wwn);
            }
            log.info(String.format("getVolumesFromLunMaskingInstance(%s)", instance.getObjectPath().toString()));
        } catch (WBEMException we) {
            log.error("Caught an error will attempting to get volume list from " + "masking instance", we);
            throw we;
        } finally {
            if (null != iterator) {
                iterator.close();
            }
        }
        return wwnList;
        
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
        List<URI> initURIs = newArrayList();

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
                String msg = String.format("Requested initiator %s does not belong to mask %s", reqInitiatorURI, exportMask);
                log.warn(msg);
                // COP-27899: This check does not work well when initiator update operations fail to complete, or when
                // external operations (outside of ViPR) remove initiators from a mask, and we're just trying to update
                // our internal data structures.
                //
                // throw new IllegalArgumentException(msg);
            }
        }
    }
    
    private void logFailure(String reason) {
        log.warn(String.format(FAILING_MSG, reason));
    }
}
