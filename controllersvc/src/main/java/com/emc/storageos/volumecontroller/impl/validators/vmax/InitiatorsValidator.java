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

import static com.emc.storageos.db.client.util.CommonTransformerFunctions.fctnInitiatorToPortName;
import static com.emc.storageos.volumecontroller.impl.smis.SmisConstants.CP_INSTANCE_ID;
import static com.emc.storageos.volumecontroller.impl.smis.SmisConstants.CP_SE_STORAGE_HARDWARE_ID;
import static com.google.common.collect.Collections2.transform;

/**
 * Vmax validator for validating there are no additional initiators in the export mask
 * than what is expected.
 */
public class InitiatorsValidator extends AbstractVmaxValidator {

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
    public boolean validate() throws Exception {
        log.info("Validating remove volume operation");
        getLogger().setLog(log);

        ExportMask exportMask = getDbClient().queryObject(ExportMask.class, exportMaskURI);
        CIMObjectPath maskingViewPath = getCimPath().getMaskingViewPath(storage, exportMask.getMaskName());
        Collection<String> iniPorts = transform(initiators, fctnInitiatorToPortName());

        log.info("ViPR has initiators: {}", Joiner.on(',').join(iniPorts));
        CloseableIterator<CIMObjectPath> assocInitiators = null;

        try {
            getHelper().callRefreshSystem(storage);
            assocInitiators = getHelper().getAssociatorNames(storage, maskingViewPath, null, CP_SE_STORAGE_HARDWARE_ID, null, null);

            List<String> smisInitiators = Lists.newArrayList();
            while (assocInitiators.hasNext()) {
                CIMObjectPath assocInitiator = assocInitiators.next();
                String id = (String) assocInitiator.getKeyValue(CP_INSTANCE_ID);
                smisInitiators.add(normalizePort(id));
            }

            log.info("{} has initiators: {}", storage.getSerialNumber(), Joiner.on(',').join(smisInitiators));
            if (smisInitiators.size() > iniPorts.size()) {
                String smisJoined = Joiner.on(',').join(smisInitiators);
                getLogger().logDiff(exportMask.getId().toString(), "initiators",
                        Joiner.on(',').join(iniPorts), smisJoined);
                throw new RuntimeException("Unknown additional initiators were found: " + smisJoined);
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

    private String normalizePort(String smisPort) {
        return smisPort.replace("W-+-", "");
    }
}
