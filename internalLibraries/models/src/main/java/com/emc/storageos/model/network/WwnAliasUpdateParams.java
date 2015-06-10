/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
/**
 *  Copyright (c) 2008-2013 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */
package com.emc.storageos.model.network;

import java.util.ArrayList;
import java.util.List;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

/**
 * This is a list of aliases to update
 */
@XmlRootElement(name="wwn_aliases_update")
public class WwnAliasUpdateParams {

    private List<WwnAliasUpdateParam> updateAliases;
    private String fabricId;

    public WwnAliasUpdateParams() {}

    
    /**
     * The identifier of the fabric of the aliases. It can be either
     * the fabric name or its WWN.
     * <p>
     * This field is required for Brocade only. If provided for Cisco it will ignored.
     * @valid none
     */
    @XmlElement(name="fabric_id")
    public String getFabricId() {
        return fabricId;
    }

    /**
     * Sets the fabric identifier which either its name or WWN.
     * @param fabricId
     */
    public void setFabricId(String fabricId) {
        this.fabricId = fabricId;
    }


    /**
     * A list of updating aliases.
     * @valid none
     */
    @XmlElement(name="wwn_alias_update")
    public List<WwnAliasUpdateParam> getUpdateAliases() {
        if (updateAliases == null) {
            updateAliases = new ArrayList<WwnAliasUpdateParam>();
        }
        return updateAliases;
    }

    public void setUpdateAliases(List<WwnAliasUpdateParam> updateAliases) {
        this.updateAliases = updateAliases;
    }
    
}
