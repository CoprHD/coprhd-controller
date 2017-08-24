/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.model.block;

import java.util.ArrayList;
import java.util.List;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

/**
 * List of com.emc.storageos.model.block.Copy to be operated on
 */
@XmlRootElement(name = "copies")
public class CopiesParam {

    private List<Copy> copies;

    public CopiesParam() {
    }

    public CopiesParam(List<Copy> copies) {
        this.copies = copies;
    }

    /**
     * A list of copies.
     * 
     */
    @XmlElement(name = "copy")
    public List<Copy> getCopies() {
        if (copies == null) {
            copies = new ArrayList<>();
        }
        return copies;
    }

    public void setCopies(List<Copy> copies) {
        this.copies = copies;
    }

}
