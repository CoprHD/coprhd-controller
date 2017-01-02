/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */
package controllers.tenant;

import org.apache.commons.lang.StringUtils;

import play.data.validation.Required;
import play.data.validation.Validation;

public class FileReplicationTopology {

    @Required
    public String sourceVArray;
    public String targetVArray;

    public boolean isEnabled() {
        return StringUtils.isNotBlank(sourceVArray);
    }

    public void validate(String formName) {
        Validation.valid(formName, this);
    }

    public void load(String srcVArray, String tgtVArray) {
        sourceVArray = srcVArray;
        targetVArray = tgtVArray;
    }
}
