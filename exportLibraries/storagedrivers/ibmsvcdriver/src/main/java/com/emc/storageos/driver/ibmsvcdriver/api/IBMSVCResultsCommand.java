/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */
package com.emc.storageos.driver.ibmsvcdriver.api;

import com.emc.storageos.driver.ibmsvcdriver.exceptions.CommandException;

/**
 * This class represents a command that has results that need to be
 * processed into a T type object.
 *
 * @param <T>
 */
public abstract class IBMSVCResultsCommand<T> extends IBMSVCCLICommand {
    protected T results;

    @Override
    protected void processOutput() throws CommandException {
        parseOutput();
    }

    public T getResults() {
        return results;
    }

    public abstract void parseOutput();
}