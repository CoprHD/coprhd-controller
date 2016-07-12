package com.emc.storageos.volumecontroller.impl.validators;

import com.google.common.base.Joiner;
import com.google.common.collect.Lists;

import java.util.List;

/**
 * Chain a series of {@link Validator} instances.
 */
public class ChainingValidator implements Validator {
    private List<Validator> validators;

    public ChainingValidator() {
        validators = Lists.newArrayList();
    }

    public boolean addValidator(Validator validator) {
        return validators.add(validator);
    }

    @Override
    public boolean validate() throws Exception {
        boolean allPassed = true;
        List<String> failureReasons = Lists.newArrayList();

        for (Validator validator : validators) {
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
