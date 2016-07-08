package com.emc.storageos.volumecontroller.impl.validators.vmax;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.ExportMask;
import com.emc.storageos.db.client.model.Initiator;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.volumecontroller.impl.smis.CIMObjectPathFactory;
import com.emc.storageos.volumecontroller.impl.smis.SmisCommandHelper;
import com.emc.storageos.volumecontroller.impl.validators.AbstractDUPValidationFactory;
import com.emc.storageos.volumecontroller.impl.validators.ChainingValidator;
import com.emc.storageos.volumecontroller.impl.validators.DUPreventionValidator;

import java.net.URI;
import java.util.Collection;

/**
 * Factory class for creating VMAX specific validators for DU prevention.
 */
public class VmaxDUPValidationFactory extends AbstractDUPValidationFactory {

    private DbClient dbClient;
    private CIMObjectPathFactory cimPath;
    private SmisCommandHelper helper;

    public DbClient getDbClient() {
        return dbClient;
    }

    public void setDbClient(DbClient dbClient) {
        this.dbClient = dbClient;
    }

    public CIMObjectPathFactory getCimPath() {
        return cimPath;
    }

    public void setCimPath(CIMObjectPathFactory cimPath) {
        this.cimPath = cimPath;
    }

    public SmisCommandHelper getHelper() {
        return helper;
    }

    public void setHelper(SmisCommandHelper helper) {
        this.helper = helper;
    }

    @Override
    public DUPreventionValidator exportMaskDelete(StorageSystem storage, ExportMask exportMask,
                                                  Collection<URI> volumeURIList,
                                                  Collection<Initiator> initiatorList) {
        AbstractVmaxDUPValidator volumes = new ExportMaskVolumesValidator(storage, exportMask, volumeURIList);
        volumes.setFactory(this);

        InitiatorsValidator initiators = new InitiatorsValidator(storage, exportMask.getId(), initiatorList);
        initiators.setFactory(this);

        ChainingValidator chain = new ChainingValidator();
        chain.addValidator(volumes);
        chain.addValidator(initiators);

        return chain;
    }

    @Override
    public DUPreventionValidator removeVolumes(StorageSystem storage, URI exportMaskURI,
                                               Collection<Initiator> initiators) {
        AbstractVmaxDUPValidator validator = new InitiatorsValidator(storage, exportMaskURI, initiators);
        validator.setFactory(this);
        return validator;
    }
}
