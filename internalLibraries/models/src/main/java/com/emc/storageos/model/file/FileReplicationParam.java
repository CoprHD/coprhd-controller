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

@XmlRootElement(name = "file_copies")
public class FileReplicationParam implements Serializable {

    private static final long serialVersionUID = 1L;
    private List<FileCopy> copies;

    public FileReplicationParam() {
    }

    public FileReplicationParam(List<FileCopy> copies) {
        this.copies = copies;
    }

    /**
     * A list of copies.
     * 
     * 
     */
    @XmlElement(name = "file_copy")
    public List<FileCopy> getCopies() {
        if (copies == null) {
            copies = new ArrayList<FileCopy>();
        }
        return copies;
    }

    public void setCopies(List<FileCopy> copies) {
        this.copies = copies;
    }

}
