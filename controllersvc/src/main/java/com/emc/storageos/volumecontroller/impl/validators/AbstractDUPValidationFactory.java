package com.emc.storageos.volumecontroller.impl.validators;

import com.emc.storageos.db.client.model.ExportMask;
import com.emc.storageos.db.client.model.Initiator;
import com.emc.storageos.db.client.model.StorageSystem;

import java.net.URI;
import java.util.Collection;

/**
 * Abstract class containing methods for building validators across various storage systems.
 */
public abstract class AbstractDUPValidationFactory {

    /**
     * Create an {@link DUPreventionValidator} instance for validating an export mask delete operation.
     *
     * @param storage       StorageSystem
     * @param exportMask    ExportMask
     * @param volumeURIList Expected Volume URI list
     * @param initiatorList Expected Initiator list
     * @return  An {@link DUPreventionValidator} instance.
     */
    public abstract DUPreventionValidator exportMaskDelete(StorageSystem storage, ExportMask exportMask,
                                                           Collection<URI> volumeURIList,
                                                           Collection<Initiator> initiatorList);

    /**
     * Create an {@link DUPreventionValidator} instance for validating removal of a volume from an
     * export group.
     *
     * @param storage
     * @param exportMaskURI
     * @param initiators
     * @return
     */
    public abstract DUPreventionValidator removeVolumes(StorageSystem storage, URI exportMaskURI,
                                                        Collection<Initiator> initiators);
}
