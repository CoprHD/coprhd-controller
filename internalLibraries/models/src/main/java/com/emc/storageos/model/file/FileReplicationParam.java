/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.model.file;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

@XmlRootElement(name = "copies")
public class FileReplicationParam implements Serializable {

    private static final long serialVersionUID = 1L;
    private List<Copy> copies;

    public FileReplicationParam() {
    }

    public FileReplicationParam(List<Copy> copies) {
        this.copies = copies;
    }

    /**
     * A list of copies.
     * 
     * 
     */
    @XmlElement(name = "copy")
    public List<Copy> getCopies() {
        if (copies == null) {
            copies = new ArrayList<Copy>();
        }
        return copies;
    }

    public void setCopies(List<Copy> copies) {
        this.copies = copies;
    }

}
