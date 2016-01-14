/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.vipr.model.sys.backup;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlElementWrapper;

import java.util.ArrayList;
import java.util.List;

@XmlRootElement(name = "external_backups")
public class ExternalBackups {
    private List<String> backups;

    public ExternalBackups() {
    }

    public ExternalBackups(List<String> backups) {
        this.backups = backups;
    }

    @XmlElementWrapper(name = "backups")
    @XmlElement(name = "name")
    public List<String> getBackups() {
        return backups;
    }

    public void setBackups(List<String> backups) {
        this.backups = backups;
    }

    @Override
    public String toString() {
        StringBuffer sb = new StringBuffer();
        sb.append("Backups:");
        sb.append(getBackups());

        return sb.toString();
    }
}
