package com.emc.storageos.volumecontroller.impl.validators.xtremio;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.model.Initiator;
import com.emc.storageos.exceptions.DeviceControllerException;
import com.emc.storageos.volumecontroller.impl.validators.Validator;
import com.emc.storageos.volumecontroller.impl.validators.ValidatorLogger;
import com.google.common.collect.ArrayListMultimap;

public class XtremIOExportMaskValidator implements Validator {

    private static final Logger log = LoggerFactory.getLogger(XtremIOExportMaskValidator.class);

    private final ValidatorLogger logger;
    private final XtremIOExportMaskInitiatorsValidator initiatorsValidator;
    private final XtremIOExportMaskVolumesValidator volumesValidator;

    public XtremIOExportMaskValidator(XtremIOExportMaskInitiatorsValidator initiatorsValidator,
            XtremIOExportMaskVolumesValidator volumesValidator, ValidatorLogger logger) {
        this.initiatorsValidator = initiatorsValidator;
        this.volumesValidator = volumesValidator;
        this.logger = logger;
    }

    public void setInitiatorToIGMap(ArrayListMultimap<String, Initiator> initiatorToIGMap) {
        initiatorsValidator.setInitiatorToIGMap(initiatorToIGMap);
        volumesValidator.setIgNames(initiatorToIGMap.keySet());
    }

    public void setKnownInitiatorToIGMap(ArrayListMultimap<String, Initiator> knownInitiatorToIGMap) {
        initiatorsValidator.setKnownInitiatorToIGMap(knownInitiatorToIGMap);
    }

    @Override
    public boolean validate() throws Exception {
        try {
            // First validate for both volumes and initiators and then throw combined validation error if any.
            volumesValidator.setErrorOnMismatch(false);
            initiatorsValidator.setErrorOnMismatch(false);

            volumesValidator.validate();
            initiatorsValidator.validate();
        } catch (Exception ex) {
            log.info("Unexpected exception validating ExportMask: " + ex.getMessage(), ex);
            throw DeviceControllerException.exceptions.unexpectedCondition(
                    "Unexpected exception validating ExportMask: " + ex.getMessage());
        }

        if (logger.hasErrors()) {
            throw DeviceControllerException.exceptions.validationError(
                    "Export Mask", logger.getMsgs().toString(), ValidatorLogger.CONTACT_EMC_SUPPORT);
        }

        return true;
    }

}
