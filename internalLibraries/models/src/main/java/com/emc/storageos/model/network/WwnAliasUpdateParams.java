/*
 * Copyright (c) 2008-2013 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.model.network;

import java.util.ArrayList;
import java.util.List;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

/**
 * This is a list of aliases to update
 */
@XmlRootElement(name = "wwn_aliases_update")
public class WwnAliasUpdateParams {

    private List<WwnAliasUpdateParam> updateAliases;
    private String fabricId;

    public WwnAliasUpdateParams() {
    }

    /**
     * The identifier of the fabric of the aliases. It can be either
     * the fabric name or its WWN.
     * <p>
     * This field is required for Brocade only. If provided for Cisco it will ignored.
     * 
     */
    @XmlElement(name = "fabric_id")
    public String getFabricId() {
        return fabricId;
    }

    /**
     * Sets the fabric identifier which either its name or WWN.
     * 
     * @param fabricId
     */
    public void setFabricId(String fabricId) {
        this.fabricId = fabricId;
    }

    /**
     * A list of updating aliases.
     * 
     */
    @XmlElement(name = "wwn_alias_update")
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
