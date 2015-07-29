/*
 * Copyright (c) 2008-2011 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.api.service.impl.response;

import javax.xml.bind.annotation.*;
import javax.xml.bind.annotation.adapters.XmlJavaTypeAdapter;

import com.emc.storageos.db.client.model.DataObject;
import com.emc.storageos.db.client.model.ScopedLabelSet;
import com.emc.storageos.model.RestLinkRep;
import com.emc.storageos.model.adapters.CalendarAdapter;

import java.net.URI;
import java.util.Calendar;

@XmlAccessorType(XmlAccessType.PROPERTY)
@Deprecated
public abstract class LegacyDataObjectRestRep
{
    private class ConcreteDataObject extends DataObject {
    }

    protected DataObject _resource;

    public LegacyDataObjectRestRep() {
        _resource = new ConcreteDataObject();
    }

    public LegacyDataObjectRestRep(DataObject resource) {
        _resource = resource;
    }

    protected DataObject getData() {
        return _resource;
    }

    @XmlElement
    public String getName() {
        return _resource.getLabel();
    }

    public void setName(String name) {
        _resource.setLabel(name);
    }

    @XmlElement(name = "id")
    public URI getId() {
        return _resource.getId();
    }

    public void setId(URI id) {
        _resource.setId(id);
    }

    @XmlElement(name = "link")
    public RestLinkRep getRestLink() {
        return new RestLinkRep("self", RestLinkFactory.newLink(_resource));
    }

    public void setRestLink(RestLinkRep link) {
    }

    @XmlElement(name = "creation_time")
    @XmlJavaTypeAdapter(CalendarAdapter.class)
    public Calendar getCreationTime() {
        return _resource.getCreationTime();
    }

    public void setCreationTime(Calendar creationTime) {
        _resource.setCreationTime(creationTime);
    }

    @XmlElement
    public Boolean getInactive() {
        return _resource.getInactive();
    }

    public void setInactive(Boolean inactive) {
        _resource.setInactive(inactive);
    }

    @XmlElementWrapper(name = "tags")
    public ScopedLabelSet getTag() {
        return _resource.getTag();
    }

    public void setTag(ScopedLabelSet tags) {
        _resource.setTag(tags);
    }
}
