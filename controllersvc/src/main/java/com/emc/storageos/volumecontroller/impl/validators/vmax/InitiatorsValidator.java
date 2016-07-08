package com.emc.storageos.volumecontroller.impl.validators.vmax;

import com.emc.storageos.db.client.model.ExportMask;
import com.emc.storageos.db.client.model.Initiator;
import com.emc.storageos.db.client.model.StorageSystem;
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

import static com.emc.storageos.volumecontroller.impl.smis.SmisConstants.CP_INSTANCE_ID;
import static com.emc.storageos.volumecontroller.impl.smis.SmisConstants.CP_SE_STORAGE_HARDWARE_ID;

/**
 * TODO
 */
public class InitiatorsValidator extends AbstractVmaxDUPValidator {

    private static final Logger log = LoggerFactory.getLogger(InitiatorsValidator.class);
    private StorageSystem storage;
    private URI exportMaskURI;
    private Collection<Initiator> initiators;

    public InitiatorsValidator(StorageSystem storage, URI exportMaskURI, Collection<Initiator> initiators) {
        this.storage = storage;
        this.exportMaskURI = exportMaskURI;
        this.initiators = initiators;
    }

    @Override
    protected boolean execute() throws Exception {
        log.info("Validating remove volume operation");

        ExportMask exportMask = getDbClient().queryObject(ExportMask.class, exportMaskURI);
        CIMObjectPath maskingViewPath = getCimPath().getMaskingViewPath(storage, exportMask.getMaskName());

        log.info("ViPR has initiators: {}", Joiner.on(',').join(initiators));
        CloseableIterator<CIMObjectPath> assocInitiators = null;

        try {
            getHelper().callRefreshSystem(storage, null, true);
            assocInitiators = getHelper().getAssociatorNames(storage, maskingViewPath, null, CP_SE_STORAGE_HARDWARE_ID, null, null);

            List<String> smisInitiators = Lists.newArrayList();
            while (assocInitiators.hasNext()) {
                CIMObjectPath assocInitiator = assocInitiators.next();
                String id = (String) assocInitiator.getKeyValue(CP_INSTANCE_ID);
                smisInitiators.add(id);
            }

            log.info("{} has initiators: {}", storage.getSerialNumber(), Joiner.on(',').join(smisInitiators));
            if (smisInitiators.size() > initiators.size()) {
                throw new RuntimeException("Unknown additional initiators were found");
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

        return true;
    }
}
