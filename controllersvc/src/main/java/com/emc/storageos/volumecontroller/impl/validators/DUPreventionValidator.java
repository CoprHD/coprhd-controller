package com.emc.storageos.volumecontroller.impl.validators;

/**
 * An interface for the purpose of validating DU prevention cases.
 */
public interface DUPreventionValidator {

    boolean validate() throws Exception;
}
