package com.emc.storageos.volumecontroller.impl.validators;

import com.google.common.base.Joiner;
import com.google.common.collect.Lists;

import java.util.List;

/**
 * Chain a series of {@link DUPreventionValidator} instances.
 */
public class ChainingValidator implements DUPreventionValidator{
    private List<DUPreventionValidator> validators;

    public ChainingValidator() {
        validators = Lists.newArrayList();
    }

    public boolean addValidator(DUPreventionValidator validator) {
        return validators.add(validator);
    }

    @Override
    public boolean validate() throws Exception {
        boolean allPassed = true;
        List<String> failureReasons = Lists.newArrayList();

        for (DUPreventionValidator validator : validators) {
            try {
                validator.validate();
            } catch (Exception e) {
                allPassed = false;
                failureReasons.add(e.getMessage());
            }
        }

        if (!allPassed) {
            throw new RuntimeException(Joiner.on(",").join(failureReasons));
        }

        return true;
    }
}
