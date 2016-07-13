package com.emc.storageos.volumecontroller.impl.validators.xtremio;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.ExportMask;
import com.emc.storageos.db.client.model.Initiator;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.model.Volume;
import com.emc.storageos.volumecontroller.impl.validators.StorageSystemValidatorFactory;
import com.emc.storageos.volumecontroller.impl.validators.ValCk;
import com.emc.storageos.volumecontroller.impl.validators.Validator;
import com.emc.storageos.xtremio.restapi.XtremIOClientFactory;

import java.net.URI;
import java.util.Collection;
import java.util.List;

/**
 * Factory class for creating XtremIO specific validators for DU prevention.
 */
public class XtremioSystemValidatorFactory implements StorageSystemValidatorFactory {

    private DbClient dbClient;
    private XtremIOClientFactory clientFactory;

    public DbClient getDbClient() {
        return dbClient;
    }

    public void setDbClient(DbClient dbClient) {
        this.dbClient = dbClient;
    }

    public XtremIOClientFactory getClientFactory() {
        return clientFactory;
    }

    public void setClientFactory(XtremIOClientFactory clientFactory) {
        this.clientFactory = clientFactory;
    }

    @Override
    public Validator exportMaskDelete(StorageSystem storage, ExportMask exportMask,
                                      Collection<URI> volumeURIList, Collection<Initiator> initiatorList) {
        // TODO
        return null;
    }

    @Override
    public Validator removeVolumes(StorageSystem storage, URI exportMaskURI, Collection<Initiator> initiators) {
        // TODO
        return null;
    }

    @Override
    public List<Volume> volumes(StorageSystem storageSystem, List<Volume> volumes, boolean delete, boolean remediate,
                                ValCk[] checks) {
        return null;
    }

    @Override
    public Validator removeInitiators(StorageSystem storage, ExportMask exportMask, Collection<URI> volumeURIList) {
        return null;
    }
}
