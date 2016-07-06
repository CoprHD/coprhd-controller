package com.emc.storageos.volumecontroller.impl.validators.xtremio;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.ExportMask;
import com.emc.storageos.db.client.model.Initiator;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.volumecontroller.impl.validators.AbstractDUPValidationFactory;
import com.emc.storageos.volumecontroller.impl.validators.DUPreventionValidator;
import com.emc.storageos.xtremio.restapi.XtremIOClientFactory;

import java.net.URI;
import java.util.Collection;

/**
 * Factory class for creating XtremIO specific validators for DU prevention.
 */
public class XtremIODUPValidationFactory extends AbstractDUPValidationFactory {

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
    public DUPreventionValidator exportMaskDelete(StorageSystem storage, ExportMask exportMask,
                                                  Collection<URI> volumeURIList, Collection<Initiator> initiatorList) {
        // TODO
        return null;
    }
}
