package com.emc.storageos.volumecontroller.impl.validators.smis.vmax;

import com.emc.storageos.db.client.constraint.AlternateIdConstraint;
import com.emc.storageos.db.client.model.DataObject;
import com.emc.storageos.db.client.model.ExportMask;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.util.CustomQueryUtility;
import com.emc.storageos.volumecontroller.impl.smis.SmisConstants;
import com.emc.storageos.volumecontroller.impl.validators.ValidatorLogger;
import com.emc.storageos.volumecontroller.impl.validators.smis.AbstractSMISValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.cim.CIMInstance;
import javax.cim.CIMObjectPath;
import javax.wbem.CloseableIterator;
import java.util.Collection;
import java.util.List;

/**
 * Abstract template class for validating that a resource has only associated masking views that are
 * managed by ViPR.
 */
public abstract class AbstractMultipleVmaxMaskValidator<T extends DataObject> extends AbstractSMISValidator {

    private static final Logger log = LoggerFactory.getLogger(AbstractMultipleVmaxMaskValidator.class);
    protected StorageSystem storage;
    protected ExportMask exportMask;
    protected Collection<T> dataObjects;

    /**
     * Default constructor.
     *
     * @param storage       StorageSystem
     * @param exportMask    ExportMask
     * @param dataObjects   List of dataObjects to check.
     *                      May be null, in which case all user added volumes from {@code exportMask} is used.
     */
    public AbstractMultipleVmaxMaskValidator(StorageSystem storage, ExportMask exportMask,
                                             Collection<T> dataObjects) {
        this.storage = storage;
        this.exportMask = exportMask;
        this.dataObjects = dataObjects;
    }

    /**
     * Validate that for each {@code DataObject}, any associated {@code Symm_LunMaskingView} instances
     * from {@code storage} are managed by ViPR.
     *
     * @return true - errors are logged by the shared {@link ValidatorLogger}
     * @throws Exception
     */
    @Override
    public boolean validate() throws Exception {
        // Check for each resource, that only known masks are found to be associated with it on the array
        for (T dataObject : getDataObjects()) {
            CIMObjectPath path = getCIMObjectPath(dataObject);

            if (path == null) {
                log.warn("No CIM object path exists for {}", dataObject.getId());
                continue;
            }

            CloseableIterator<CIMInstance> assocMasks = getHelper().getAssociatorInstances(storage, path, null,
                    SmisConstants.SYMM_LUNMASKINGVIEW, null, null, null);

            while (assocMasks.hasNext()) {
                CIMInstance assocMask = assocMasks.next();
                String name = (String) assocMask.getPropertyValue(SmisConstants.CP_DEVICE_ID);

                log.info("{} has associated mask {}", dataObject.getId(), name);
                if (!exportMask.getMaskName().equals(name)) {
                    // Does ViPR know about this other mask?
                    List<ExportMask> exportMasks = CustomQueryUtility.queryActiveResourcesByConstraint(getDbClient(),
                            ExportMask.class, AlternateIdConstraint.Factory.getExportMaskByNameConstraint(name));

                    if (exportMasks.isEmpty()) {
                        getLogger().logDiff(dataObject.getId().toString(), "associated masks", exportMask.getMaskName(), name);
                    }
                }
            }
        }
        return true;
    }

    protected abstract CIMObjectPath getCIMObjectPath(T obj) throws Exception;

    protected abstract Collection<T> getDataObjects();

}
