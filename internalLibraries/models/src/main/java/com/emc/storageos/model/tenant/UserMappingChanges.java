/*
 * Copyright (c) 2012 - 2013 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.model.tenant;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElementWrapper;

import java.util.ArrayList;
import java.util.List;

public class UserMappingChanges {

    private List<UserMappingParam> add;
    private List<UserMappingParam> remove;

    public UserMappingChanges() {
    }

    public UserMappingChanges(List<UserMappingParam> add,
            List<UserMappingParam> remove) {
        this.add = add;
        this.remove = remove;
    }

    @XmlElementWrapper(name = "add")
    /*
     * User mapping to add in this change
     * 
     * @valid none
     */
    @XmlElement(name = "user_mapping")
    public List<UserMappingParam> getAdd() {
        if (add == null) {
            add = new ArrayList<UserMappingParam>();
        }
        return add;
    }

    public void setAdd(List<UserMappingParam> add) {
        this.add = add;
    }

    @XmlElementWrapper(name = "remove")
    /**
     * User mapping to remove in this change
     * @valid none
     */
    @XmlElement(name = "user_mapping")
    public List<UserMappingParam> getRemove() {
        if (remove == null) {
            remove = new ArrayList<UserMappingParam>();
        }
        return remove;
    }

    public void setRemove(List<UserMappingParam> remove) {
        this.remove = remove;
    }

}
