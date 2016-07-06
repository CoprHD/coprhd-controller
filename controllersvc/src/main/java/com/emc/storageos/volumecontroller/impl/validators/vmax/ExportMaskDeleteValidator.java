package com.emc.storageos.volumecontroller.impl.validators.vmax;

import com.emc.storageos.db.client.model.ExportMask;
import com.emc.storageos.db.client.model.Initiator;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.model.Volume;
import com.emc.storageos.volumecontroller.impl.smis.SmisConstants;
import com.google.common.base.Joiner;
import com.google.common.collect.Lists;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.cim.CIMObjectPath;
import javax.wbem.CloseableIterator;
import java.net.URI;
import java.util.Collection;
import java.util.List;

import static com.emc.storageos.db.client.util.CommonTransformerFunctions.fctnBlockObjectToNativeID;
import static com.google.common.collect.Collections2.transform;

/**
 * Validator class for VMAX export mask delete operations.
 */
class ExportMaskDeleteValidator extends AbstractVmaxDUPValidator {

    private static final Logger log = LoggerFactory.getLogger(ExportMaskDeleteValidator.class);

    private StorageSystem storage;
    private ExportMask exportMask;
    private Collection<URI> volumeURIs;
    private Collection<Initiator> initiators;

    ExportMaskDeleteValidator(StorageSystem storage, ExportMask exportMask, Collection<URI> volumeURIList, Collection<Initiator> initiatorList) {
        this.storage = storage;
        this.exportMask = exportMask;
        this.volumeURIs = volumeURIList;
        this.initiators = initiatorList;
    }

    @Override
    protected boolean execute() throws Exception {
        log.info("Validating export mask delete: {}", exportMask.getMaskName());
        // Need Mask's volume list for removing volumes from phantom storage group.
        List<Volume> volumes = getDbClient().queryObject(Volume.class, volumeURIs);

        Collection<String> viprIds = transform(volumes, fctnBlockObjectToNativeID());
        log.info("ViPR has: {}", Joiner.on(",").join(viprIds));

        getHelper().callRefreshSystem(storage, null, true);

        CIMObjectPath maskingViewPath = getCimPath().getMaskingViewPath(storage, exportMask.getMaskName());
        CloseableIterator<CIMObjectPath> associatedVolumes = getHelper().getAssociatorNames(storage, maskingViewPath, null, SmisConstants.STORAGE_VOLUME_CLASS, null, null);

        List<String> smisIds = Lists.newArrayList();
        try {
            while (associatedVolumes.hasNext()) {
                CIMObjectPath assocVol = associatedVolumes.next();
                String deviceId = (String) assocVol.getKeyValue(SmisConstants.CP_DEVICE_ID);
                smisIds.add(deviceId);
            }

            log.info("{} has: {}", storage.getSerialNumber(), Joiner.on(',').join(smisIds));
            if (viprIds.size() != smisIds.size()) {
                String msg =
                        String.format("Preventing deletion of export mask %s because it has additional unknown volumes",
                                exportMask.getMaskName());
                throw new RuntimeException(msg);
            }
        } finally {
            if (associatedVolumes != null) {
                try {
                    associatedVolumes.close();
                } catch (Exception e) {
                    // ignore
                }
            }
        }
        return true;
    }
}
