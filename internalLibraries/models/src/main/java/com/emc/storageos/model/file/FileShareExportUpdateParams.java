/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.model.file;

import javax.xml.bind.annotation.XmlRootElement;

@XmlRootElement(name = "filesystem_export_update")
public class FileShareExportUpdateParams extends FileExportUpdateParams {

    private static final long serialVersionUID = 8179052693846752477L;

    public FileShareExportUpdateParams() {
    }

}