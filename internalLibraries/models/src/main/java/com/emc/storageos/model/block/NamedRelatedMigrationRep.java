/*
 * Copyright (c) 2017 Dell EMC
 * All Rights Reserved
 */
package com.emc.storageos.model.block;

import java.net.URI;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAccessType;
import com.emc.storageos.model.NamedRelatedResourceRep;
import com.emc.storageos.model.RestLinkRep;
import org.codehaus.jackson.annotate.JsonProperty;

@XmlAccessorType(XmlAccessType.PROPERTY)
public class NamedRelatedMigrationRep extends NamedRelatedResourceRep {
    private String migrationStatus;

    public NamedRelatedMigrationRep() {
    }

    public NamedRelatedMigrationRep(URI id, RestLinkRep selfLink, String name, String migrationStatus) {
        super(id, selfLink, name);
        this.migrationStatus = migrationStatus;
    }

    /**
     * The status of the migration.
     * 
     * @return The migration status.
     */
    @XmlElement(name = "status")
    @JsonProperty("status")
    public String getMigrationStatus() {
        return migrationStatus;
    }

    public void setMigrationStatus(String migrationStatus) {
        this.migrationStatus = migrationStatus;
    }
}
