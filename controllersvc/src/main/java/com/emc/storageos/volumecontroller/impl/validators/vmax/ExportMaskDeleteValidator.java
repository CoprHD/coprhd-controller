package com.emc.storageos.volumecontroller.impl.validators.vmax;

import com.emc.storageos.db.client.model.ExportMask;
import com.emc.storageos.db.client.model.Initiator;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.model.Volume;
import com.google.common.base.Joiner;
import com.google.common.collect.Lists;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.cim.CIMObjectPath;
import javax.wbem.CloseableIterator;
import javax.wbem.WBEMException;
import java.net.URI;
import java.util.Collection;
import java.util.List;

import static com.emc.storageos.db.client.util.CommonTransformerFunctions.fctnBlockObjectToNativeID;
import static com.emc.storageos.volumecontroller.impl.smis.SmisConstants.CP_DEVICE_ID;
import static com.emc.storageos.volumecontroller.impl.smis.SmisConstants.CP_INSTANCE_ID;
import static com.emc.storageos.volumecontroller.impl.smis.SmisConstants.CP_SE_STORAGE_HARDWARE_ID;
import static com.emc.storageos.volumecontroller.impl.smis.SmisConstants.STORAGE_VOLUME_CLASS;
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

    ExportMaskDeleteValidator(StorageSystem storage, ExportMask exportMask,
                              Collection<URI> volumeURIList,
                              Collection<Initiator> initiatorList) {
        this.storage = storage;
        this.exportMask = exportMask;
        this.volumeURIs = volumeURIList;
        this.initiators = initiatorList;
    }

    @Override
    protected boolean execute() throws Exception {
        log.info("Validating export mask delete: {}", exportMask.getMaskName());
        CIMObjectPath maskingViewPath = getCimPath().getMaskingViewPath(storage, exportMask.getMaskName());
        List<String> failureReasons = Lists.newArrayList();

        getHelper().callRefreshSystem(storage, null, true);
        validateExpectedVolumes(maskingViewPath, failureReasons);
        validateExpectedInitiators(maskingViewPath, failureReasons);

        if (!failureReasons.isEmpty()) {
            String msgFmt = "Preventing deletion of export mask %s: %s.";
            String msg = String.format(msgFmt, exportMask.getMaskName(), Joiner.on(", ").join(failureReasons));
            throw new RuntimeException(msg); // TODO Create DUP-specific exception
        }

        return true;
    }

    private void validateExpectedVolumes(CIMObjectPath maskingViewPath, List<String> failureReasons) throws WBEMException {
        List<Volume> volumes = getDbClient().queryObject(Volume.class, volumeURIs);
        Collection<String> viprIds = transform(volumes, fctnBlockObjectToNativeID());
        log.info("ViPR has volumes: {}", Joiner.on(",").join(viprIds));
        CloseableIterator<CIMObjectPath> associatedVolumes = null;

        try {
            associatedVolumes = getHelper().getAssociatorNames(storage, maskingViewPath, null, STORAGE_VOLUME_CLASS, null, null);

            List<String> smisIds = Lists.newArrayList();
            while (associatedVolumes.hasNext()) {
                CIMObjectPath assocVol = associatedVolumes.next();
                String deviceId = (String) assocVol.getKeyValue(CP_DEVICE_ID);
                smisIds.add(deviceId);
            }

            log.info("{} has volumes: {}", storage.getSerialNumber(), Joiner.on(',').join(smisIds));
            if (smisIds.size() > viprIds.size()) {
                failureReasons.add("unknown additional volumes were found");
            }
        } catch (WBEMException e) {
            log.error("Failure occurred whilst validating volumes for export mask {}", exportMask.getMaskName(), e);
            throw e;
        } finally {
            if (associatedVolumes != null) {
                try {
                    associatedVolumes.close();
                } catch (Exception e) {
                    // ignore
                }
            }
        }
    }

    private void validateExpectedInitiators(CIMObjectPath maskingViewPath, List<String> failureReasons) throws WBEMException {
        log.info("ViPR has initiators: {}", Joiner.on(',').join(initiators));
        CloseableIterator<CIMObjectPath> assocInitiators = null;

        try {
            assocInitiators = getHelper().getAssociatorNames(storage, maskingViewPath, null, CP_SE_STORAGE_HARDWARE_ID, null, null);

            List<String> smisInitiators = Lists.newArrayList();
            while (assocInitiators.hasNext()) {
                CIMObjectPath assocInitiator = assocInitiators.next();
                String id = (String) assocInitiator.getKeyValue(CP_INSTANCE_ID);
                smisInitiators.add(id);
            }

            log.info("{} has initiators: {}", storage.getSerialNumber(), Joiner.on(',').join(smisInitiators));
            if (smisInitiators.size() > initiators.size()) {
                failureReasons.add("unknown additional initiators were found");
            }
        } catch (WBEMException e) {
            log.error("Failure occurred whilst validating initiators for export mask {}", exportMask.getMaskName(), e);
            throw e;
        } finally {
            if (assocInitiators != null) {
                try {
                    assocInitiators.close();
                } catch (Exception e) {
                    // ignore
                }
            }
        }
    }
}
