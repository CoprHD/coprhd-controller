package com.emc.storageos.volumecontroller.impl.validators;

import com.emc.storageos.db.client.model.ExportMask;
import com.emc.storageos.db.client.model.Initiator;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.model.Volume;

import java.net.URI;
import java.util.Collection;
import java.util.List;

/**
 * Abstract factory interface containing methods for building validators across various storage systems.
 */
public interface StorageSystemValidatorFactory {

    /**
     * Create an {@link Validator} instance for validating an export mask delete operation.
     *
     * @param storage       StorageSystem
     * @param exportMask    ExportMask
     * @param volumeURIList Expected Volume URI list
     * @param initiatorList Expected Initiator list
     * @return  An {@link Validator} instance.
     */
    Validator exportMaskDelete(StorageSystem storage, ExportMask exportMask,
                                               Collection<URI> volumeURIList,
                                               Collection<Initiator> initiatorList);

    /**
     * Create an {@link Validator} instance for validating removal of a volume from an
     * export group.
     *
     * @param storage
     * @param exportMaskURI
     * @param initiators
     * @return
     */
    Validator removeVolumes(StorageSystem storage, URI exportMaskURI,
                                            Collection<Initiator> initiators);
    /**
     * Validates the volumes for a single storage system.
     *
     * @param storageSystem -- Storage System object
     * @param volumes -- list of Volume objects belonging to that StorageSystem
     * @param delete -- if true we are deleting, don't flag errors where entity is missing
     * @param remediate -- if true, attempt remediation
     * @param msgs -- message buffer for actions taken
     * @param checks -- checks to be performed
     * @return -- list of any Volumes that were remediated
     */
    List<Volume> volumes(StorageSystem storageSystem, List<Volume> volumes, boolean delete, boolean remediate, StringBuilder msgs, ValCk[] checks);

    Validator removeInitiators(StorageSystem storage, ExportMask exportMask, Collection<URI> volumeURIList);
}
