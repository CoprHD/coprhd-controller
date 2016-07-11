package com.emc.storageos.volumecontroller.impl.validators.vplex;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.ExportMask;
import com.emc.storageos.db.client.model.Initiator;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.model.Volume;
import com.emc.storageos.volumecontroller.impl.validators.StorageSystemValidatorFactory;
import com.emc.storageos.volumecontroller.impl.validators.ValCk;
import com.emc.storageos.volumecontroller.impl.validators.Validator;
import com.emc.storageos.volumecontroller.impl.validators.ValidatorLogger;
import com.emc.storageos.vplex.api.VPlexApiClient;
import com.emc.storageos.vplex.api.VPlexApiFactory;
import com.emc.storageos.vplexcontroller.VPlexControllerUtils;
import com.google.common.collect.Lists;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.util.Collection;
import java.util.List;

/**
 * Factory for creating Vplex-specific validator instances.
 */
public class VplexSystemValidatorFactory implements StorageSystemValidatorFactory {

    private static final Logger log = LoggerFactory.getLogger(VplexSystemValidatorFactory.class);
    private DbClient dbClient;

    private List<Volume> remediatedVolumes = Lists.newArrayList();
    private VPlexApiClient client;
    private ValidatorLogger logger;

    public void setDbClient(DbClient dbClient) {
        this.dbClient = dbClient;
    }

    @Override
    public Validator exportMaskDelete(StorageSystem storage, ExportMask exportMask, Collection<URI> volumeURIList, Collection<Initiator> initiatorList) {
        return null;
    }

    @Override
    public Validator removeVolumes(StorageSystem storage, URI exportMaskURI, Collection<Initiator> initiators) {
        return null;
    }

    @Override
    public List<Volume> volumes(StorageSystem storageSystem, List<Volume> volumes, boolean delete, boolean remediate,
                                StringBuilder msgs, ValCk[] checks) {
        try {
            client = VPlexControllerUtils.getVPlexAPIClient(VPlexApiFactory.getInstance(), storageSystem, dbClient);
            logger = new ValidatorLogger(log);
            for (Volume volume : volumes) {
                try {
                    log.info(String.format("Validating %s (%s)(%s) checks %s",
                            volume.getLabel(), volume.getNativeId(), volume.getId(), checks.toString()));
                    validateVolume(volume, delete, remediate, msgs, checks);
                } catch (Exception ex) {
                    log.error("Exception validating volume: " + volume.getId(), ex);
                }
            }
        } catch (Exception ex) {
            log.error("Exception validating VPLEX: " + storageSystem.getId(), ex);
        }
        return remediatedVolumes;
    }

    private void validateVolume(Volume volume, boolean delete, boolean remediate, StringBuilder msgs, ValCk[] checks) {
        // TODO Tom's code here.
        logger.logDiff(volume.getId().toString(), "field", "dbValue", "hwValue");
    }


}
