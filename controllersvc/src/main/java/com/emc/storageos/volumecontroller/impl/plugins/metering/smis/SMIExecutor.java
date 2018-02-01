/*
 * Copyright (c) 2008-2011 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.plugins.metering.smis;

import java.util.regex.Pattern;

import com.emc.storageos.plugins.BaseCollectionException;
import com.emc.storageos.plugins.common.Constants;
import com.emc.storageos.plugins.common.Executor;
import com.emc.storageos.plugins.common.domainmodel.Operation;
import com.emc.storageos.plugins.metering.smis.SMIPluginException;

public class SMIExecutor extends Executor {

    private static Pattern patternVerForContFile = Pattern.compile(
            "[\\.|\\)|\\(| ]|(?<=\\D)(?=\\d)|(?<=\\d)(?=\\D)",
            Pattern.DOTALL);
    private static Pattern patternVerProvided = Pattern.compile(
            "[\\.|\\)|\\(| ]|(?<=\\D)(?=\\d)|(?<=\\d)(?=\\D)",
            Pattern.DOTALL);

    @Override
    protected void customizeException(Exception e, Operation operation)
            throws BaseCollectionException {
        throw new SMIPluginException(e.getMessage());
    }

    /**
     * Returns true if the supportedVersion option is specified in Operation bean
     * false in other cases.
     * 
     * @param operation
     * @return true - Executes the operation when supportedVersion matches with keyMap value.
     *         false - Skip the operation.
     */
    @Override
    protected boolean isSupportedOperation(Operation operation) {
        String operationSupportedVer = operation.getSupportedVersion();
        // Run version matching only if version is specified in Operation bean
        // and also in the KeyMap.
        if (_keyMap.containsKey(Constants.VERSION)
                && null != operationSupportedVer) {
            String versionFromKeyMap = (String) _keyMap.get(Constants.VERSION);
            // split by dots, parentheses, and adjoining letters and numbers
            String[] versionFromContextFile = patternVerForContFile.split(operationSupportedVer);
            String[] versionProvided = patternVerProvided.split(versionFromKeyMap);

            // check only major version
            // VMAX Accept both 8.x and 9.x for 8.x
            if (versionFromContextFile[0].equals("8")) {
                return (versionProvided[0].equals("8") || versionProvided[0].equals("9"));
            }
            // Others (mainly VNX), make sure they match
            return (versionFromContextFile[0].equals(versionProvided[0]));
        }
        // set true if the version string is not populated keyMap or
        // version is not specified in Operation bean.
        return true;
    }
}
