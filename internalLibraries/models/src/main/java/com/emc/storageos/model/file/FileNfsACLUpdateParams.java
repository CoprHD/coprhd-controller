/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.model.file;

import javax.xml.bind.annotation.XmlRootElement;

@XmlRootElement(name = "filesystem_share_acl_update")
public class FileNfsACLUpdateParams extends CifsShareACLUpdateParams {

    private static final long serialVersionUID = -3923546614453117683L;

    public FileNfsACLUpdateParams() {

    }

}
