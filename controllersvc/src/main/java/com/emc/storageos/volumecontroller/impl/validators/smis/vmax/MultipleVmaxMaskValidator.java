package com.emc.storageos.volumecontroller.impl.validators.smis.vmax;

import com.emc.storageos.db.client.model.BlockObject;
import com.emc.storageos.db.client.model.ExportMask;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.model.StringMap;
import com.emc.storageos.volumecontroller.impl.smis.SmisConstants;
import com.emc.storageos.volumecontroller.impl.validators.smis.AbstractSMISValidator;
import com.google.common.collect.Lists;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.cim.CIMInstance;
import javax.cim.CIMObjectPath;
import javax.wbem.CloseableIterator;
import java.net.URI;
import java.util.Collection;
import java.util.List;

import static com.emc.storageos.db.client.util.CommonTransformerFunctions.FCTN_VOLUME_URI_TO_STR;
import static com.google.common.collect.Collections2.transform;

/**
 * Validates that a VMAX volume exists only for 1 ViPR managed export mask.
 */
public class MultipleVmaxMaskValidator extends AbstractSMISValidator {

    private static final Logger log = LoggerFactory.getLogger(MultipleVmaxMaskValidator.class);
    private StorageSystem storage;
    private ExportMask exportMask;
    private Collection<? extends BlockObject> blockObjects;

    /**
     * Default constructor.
     *
     * @param storage       StorageSystem
     * @param exportMask    ExportMask
     * @param blockObjects  List of blockobjects to check.
     *                      May be null, in which case all user added volumes from {@code exportMask} is used.
     */
    public MultipleVmaxMaskValidator(StorageSystem storage, ExportMask exportMask,
                                     Collection<? extends BlockObject> blockObjects) {
        this.storage = storage;
        this.exportMask = exportMask;
        this.blockObjects = blockObjects;
    }

    @Override
    public boolean validate() throws Exception {
        // Check for each volume that only known masks are found on array.
        for (BlockObject volume : getBlockObjects()) {
            CIMObjectPath volumePath = getCimPath().getBlockObjectPath(storage, volume);
            CloseableIterator<CIMInstance> assocMasks = getHelper().getAssociatorInstances(storage, volumePath, null,
                    SmisConstants.SYMM_LUNMASKINGVIEW, null, null, null);

            while (assocMasks.hasNext()) {
                CIMInstance assocMask = assocMasks.next();
                String name = (String) assocMask.getPropertyValue(SmisConstants.CP_DEVICE_ID);

                log.info("{} has associated mask {}", volume.getNativeId(), name);
                if (!exportMask.getMaskName().equals(name)) {
                    getLogger().logDiff(volume.getId().toString(), "associated masks", exportMask.getMaskName(), name);
                }
            }
        }
        return true;
    }

    /**
     * Use this to access {@code blockObjects}, since it may be null.
     * @return  Collection of BlockObject instances.
     */
    private Collection<? extends BlockObject> getBlockObjects() {
        if (blockObjects != null) {
            // When first calling this method, caller has provided a list of block objects
            // so we must ensure they actually exist in the mask itself.
            checkRequestedVolumesBelongToMask();
            return blockObjects;
        }

        // Caller did not provide a list of block objects, so use ExportMask userAddedVolumes instead.
        StringMap userAddedVolumes = exportMask.getUserAddedVolumes();
        List<URI> boURIs = Lists.newArrayList();
        for (String boURI : userAddedVolumes.values()) {
            boURIs.add(URI.create(boURI));
        }

        blockObjects = BlockObject.fetchAll(getDbClient(), boURIs);
        return blockObjects;
    }

    private void checkRequestedVolumesBelongToMask() {
        StringMap userAddedVolumes = exportMask.getUserAddedVolumes();

        if (userAddedVolumes == null) {
            return;
        }

        Collection<String> masKVolumeURIs = userAddedVolumes.values();
        Collection<String> reqVolumeURIs = transform(blockObjects, FCTN_VOLUME_URI_TO_STR);

        for (String reqVolumeURI : reqVolumeURIs) {
            if (!masKVolumeURIs.contains(reqVolumeURI)) {
                String msg = String.format("Requested volume %s does not belong in mask %s", reqVolumeURI, exportMask);
                throw new IllegalArgumentException(msg);
            }
        }
    }
}
