/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.model.file;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import java.util.ArrayList;
import java.util.List;

/**
 * List of all file system exports
 */
@XmlRootElement(name = "filesystem_exports")
public class FileSystemExportList {

    private List<FileSystemExportParam> exportList;

    public FileSystemExportList() {
    }

    public FileSystemExportList(List<FileSystemExportParam> exportList) {
        this.exportList = exportList;
    }

    /**
     * List of file system exports
     * 
     */
    @XmlElement(name = "filesystem_export")
    public List<FileSystemExportParam> getExportList() {
        if (exportList == null) {
            exportList = new ArrayList<FileSystemExportParam>();
        }
        return exportList;
    }

    public void setExportList(List<FileSystemExportParam> exportList) {
        this.exportList = exportList;
    }

}
