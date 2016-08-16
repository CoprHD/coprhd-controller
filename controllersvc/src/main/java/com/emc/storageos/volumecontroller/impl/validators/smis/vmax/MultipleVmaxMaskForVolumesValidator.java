package com.emc.storageos.volumecontroller.impl.validators.smis.vmax;

import com.emc.storageos.db.client.model.BlockObject;
import com.emc.storageos.db.client.model.ExportMask;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.model.StringMap;
import com.google.common.collect.Lists;

import javax.cim.CIMObjectPath;
import java.net.URI;
import java.util.Collection;
import java.util.List;

import static com.emc.storageos.db.client.util.CommonTransformerFunctions.FCTN_VOLUME_URI_TO_STR;
import static com.google.common.collect.Collections2.transform;

/**
 * Sub-class for {@link AbstractMultipleVmaxMaskValidator} in order to validate that a given
 * {@link BlockObject} is not shared by another masking view out of management.
 */
public class MultipleVmaxMaskForVolumesValidator<T extends BlockObject> extends AbstractMultipleVmaxMaskValidator<T> {

    /**
     * Default constructor.
     *
     * @param storage      StorageSystem
     * @param exportMask   ExportMask
     * @param blockObjects List of blockobjects to check.
     *                     May be null, in which case all user added volumes from {@code exportMask} is used.
     */
    public MultipleVmaxMaskForVolumesValidator(StorageSystem storage, ExportMask exportMask, Collection<T> blockObjects) {
        super(storage, exportMask, blockObjects);
    }

    @Override
    protected String getFriendlyId(T blockObject) {
        return String.format("%s/%s", blockObject.getId(), blockObject.getNativeGuid());
    }

    @Override
    protected CIMObjectPath getCIMObjectPath(T obj) {
        return getCimPath().getBlockObjectPath(storage, obj);
    }

    /**
     * Use this to access {@code blockObjects}, since it may be null.
     * @return  Collection of BlockObject instances.
     */
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
                String msg = String.format("Requested volume %s does not belong in mask %s", reqVolumeURI, exportMask);
                throw new IllegalArgumentException(msg);
            }
        }
    }
}
