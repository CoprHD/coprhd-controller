/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.vipr.model.catalog;

import java.net.URI;
import java.util.Calendar;
import java.util.Set;

import javax.xml.bind.annotation.XmlElement;

import com.emc.storageos.model.DataObjectRestRep;
import com.emc.storageos.model.RestLinkRep;

public class SortedIndexRestRep extends DataObjectRestRep {

    private Integer sortedIndex;

    public SortedIndexRestRep() {
    }

    public SortedIndexRestRep(String name, URI id, RestLinkRep link, Calendar creationTime, Boolean inactive,
            Set<String> tags) {
        super(name, id, link, creationTime, inactive, tags);
    }

    /**
     * Sorted index. Used to determine the display order
     * 
     */
    @XmlElement(name = "sorted_index")
    public Integer getSortedIndex() {
        return sortedIndex;
    }

    public void setSortedIndex(Integer sortedIndex) {
        this.sortedIndex = sortedIndex;
    }

}
