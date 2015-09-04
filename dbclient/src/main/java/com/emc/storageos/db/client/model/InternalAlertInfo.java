/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.db.client.model;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import java.net.URI;

/**
 * Existence of an object of this type in the Cassandra DB means that an
 * Alert has been raised for the object identified by the Id and no new
 * alert should be logged until the entry has been removed from the DB
 *
 */
@Cf("InternalAlertInfo")
@XmlRootElement(name = "internal_alerts_info")
public class InternalAlertInfo extends DataObject {
    private static final Logger log = LoggerFactory.getLogger(InternalAlertInfo.class);

    public static enum Type {
        VPOOL_FREE_SPACE_CRITICAL,
        // combined free space in all the devices associated with a Virtual Pool is less than 5% of the total
        // capacity
        FILE_NOT_FOUND,        // file containing object data or index data not found
        FILE_DATA_CORRUPTED,
        CHUNK_NOT_FOUND// chunk info not found in CT
    }

    public InternalAlertInfo() {

    }

    public InternalAlertInfo(URI subjectObjectId, Type type) {
        try {
            setId(new URI(String.format("%1$s:type:%2$s", subjectObjectId.toString(), type.toString())));
        } catch (Exception e) {
            log.warn("generate id hit exception, ", e);
            // now the ID will be null
        }
        setSubjectObjectId(subjectObjectId);
        setType(type.toString());
    }

    /*
     * Stored for convenience even though both ID of the object who is
     * the subject of this alert and type can be parsed and retrieved
     * from the ID
     */
    private URI _subjectObjectId;

    private Type _type;

    @XmlElement(name = "subject_object_id")
    @Name("subject_object_id")
    public URI getSubjectObjectId() {
        return _subjectObjectId;
    }

    public void setSubjectObjectId(URI objId) {
        _subjectObjectId = objId;
        setChanged("subject_object_id");
    }

    @XmlElement(name = "type")
    @Name("type")
    public String getType() {
        if (_type == null) {
            return null;
        }
        return _type.toString();
    }

    public void setType(String strType) {
        _type = Type.valueOf(strType);
        setChanged("type");
    }
}
