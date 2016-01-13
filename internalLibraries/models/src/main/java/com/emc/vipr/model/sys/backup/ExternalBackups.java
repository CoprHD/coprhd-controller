package com.emc.vipr.model.sys.backup;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlElementWrapper;

import java.util.ArrayList;
import java.util.List;

@XmlRootElement(name = "external_backups")
public class ExternalBackups {
    private List<String> backupsName;

    public ExternalBackups() {
    }

    public ExternalBackups(List<String> backupsName) {
        this.backupsName = backupsName;
    }

    @XmlElementWrapper(name = "backups_name")
    @XmlElement(name = "name")
    public List<String> getBackupsName() {
        if (backupsName == null) {
            backupsName = new ArrayList<String>();
        }
        return backupsName;
    }

    public void setBackupsName(List<String> backupsName) {
        this.backupsName = backupsName;
    }
}
