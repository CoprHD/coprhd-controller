/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.model;

import javax.xml.bind.annotation.XmlRootElement;

import java.util.LinkedHashSet;
import java.util.Set;

import com.emc.storageos.model.valid.Length;

/**
 * Input parameter for tag addition and removal
 */
@XmlRootElement(name = "tag_changes")
public class TagAssignment {
    /**
     * Tags to add
     */
    private Set<String> add;

    /**
     * Tags to remove
     */
    private Set<String> remove;

    public TagAssignment() {
    }

    public TagAssignment(Set<String> add, Set<String> remove) {
        this.add = add;
        this.remove = remove;
    }

    @Length(min = 2, max = 128)
    public Set<String> getAdd() {
        if (add == null) {
            add = new LinkedHashSet<String>();
        }
        return add;
    }

    public void setAdd(Set<String> add) {
        this.add = add;
    }

    @Length(min = 2, max = 128)
    public Set<String> getRemove() {
        if (remove == null) {
            remove = new LinkedHashSet<String>();
        }
        return remove;
    }

    public void setRemove(Set<String> remove) {
        this.remove = remove;
    }
}
