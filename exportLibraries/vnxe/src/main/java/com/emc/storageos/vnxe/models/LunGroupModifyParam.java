/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
/**
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */

package com.emc.storageos.vnxe.models;

import java.util.List;

import org.codehaus.jackson.map.annotate.JsonSerialize;

@JsonSerialize(include=JsonSerialize.Inclusion.NON_NULL)
public class LunGroupModifyParam extends LunGroupCreateParam{
    private List<LunModifyParam> lunModify;
    private List<LunAddParam> lunRemove;
    private List<LunAddParam> lunDelete;
    public List<LunModifyParam> getLunModify() {
        return lunModify;
    }
    public void setLunModify(List<LunModifyParam> lunModify) {
        this.lunModify = lunModify;
    }
    
    public List<LunAddParam> getLunRemove() {
        return lunRemove;
    }
    public void setLunRemove(List<LunAddParam> lunRemove) {
        this.lunRemove = lunRemove;
    }
    public List<LunAddParam> getLunDelete() {
        return lunDelete;
    }
    public void setLunDelete(List<LunAddParam> lunDelete) {
        this.lunDelete = lunDelete;
    }

    
}
