/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.sa.descriptor;

import java.io.Serializable;

import org.apache.commons.lang.builder.ToStringBuilder;
import org.apache.commons.lang.builder.ToStringStyle;

public class ValidationDefinition implements Serializable {
    private static final long serialVersionUID = -6168151316516275L;

    /** Minimum value/size for number/text types, respectively. */
    public Integer min;

    /** Maximum value/size for number/text types, respectively. */
    public Integer max;

    /** Regular expression to apply to text fields. */
    public String regEx;

    /** Message key if value does not pass validation. */
    public String errorKey;

    @Override
    public String toString() {
        ToStringBuilder builder = new ToStringBuilder(this, ToStringStyle.SHORT_PREFIX_STYLE);
        builder.append("min", min);
        builder.append("max", max);
        builder.append("regEx", regEx);
        builder.append("errorKey", errorKey);
        return builder.toString();
    }
}