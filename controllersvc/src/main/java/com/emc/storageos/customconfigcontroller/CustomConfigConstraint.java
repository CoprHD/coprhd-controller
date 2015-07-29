/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
/**
 *  Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */

package com.emc.storageos.customconfigcontroller;

import java.io.Serializable;

import com.emc.storageos.customconfigcontroller.exceptions.CustomConfigControllerException;

/**
 * The base class for a customizable configuration constraint. A constraint is a
 * condition or rule placed on a configuration such as the min or max value of a
 * numeric-type configuration, or the maxlength for a string-type configuration.
 * 
 */
public abstract class CustomConfigConstraint implements Serializable {

    private static final long serialVersionUID = 5163674371119131136L;

    private String description;

    private String name;

    /**
     * A description of the constraint.
     * 
     * @return
     */
    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    /**
     * Accepts a value that may or may not comply with the constraint. Do
     * necessary changes to make it comply.
     * 
     * @param value
     *            the value to be checked and changed.
     * @param systemType
     *            the system type (vnx, vmax, etc.) to which the value
     *            corresponds.
     * @return The new value that complies with the constraint, other wise, the
     *         value unchanged if it already complies with the constraint.
     */
    public abstract String applyConstraint(String value, String systemType);

    /**
     * Checks if a value complies with the constraint.
     * 
     * @param value
     *            the value to be checked and changed.
     * @param systemType
     *            the system type (vnx, vmax, etc.) to which the value
     *            corresponds.
     * @throws CustomConfigControllerException
     *             if the value is not compliant.
     */
    public abstract void validate(String value, String systemType);

    /**
     * Returns the description of the constraint.
     */
    public String toString() {
        return description;
    }

}
