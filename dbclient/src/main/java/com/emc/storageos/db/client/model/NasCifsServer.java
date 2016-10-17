/*
 * Copyright (c) 2012 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.db.client.model;

import java.util.ArrayList;
import java.util.List;

import javax.xml.bind.annotation.XmlElement;

public class NasCifsServer extends AbstractSerializableNestedObject {

    private static final String NAME = "name";
    private static final String ID = "id";
    private static final String TYPE = "type";
    private static final String MOVER_ID_IS_VDM = "moverIdIsVdm";
    private static final String INTERFACES = "interfaces";
    private static final String DOMAIN = "domain";

    public NasCifsServer() {
    }

    public NasCifsServer(String name, int id, String type, boolean isMoverIsVdm, List<String> interfaces, String domain) {
        setName(name);
        setId(id);
        setType(type);
        setMoverIdIsVdm(isMoverIsVdm);
        setInterfaces(interfaces);
        setDomain(domain);
    }

    /**
     * @return the name
     */
    @XmlElement
    public String getName() {
        return getStringField(NAME);
    }

    public void setName(String name) {
        if (name == null) {
            name = "";
        }
        setField(NAME, name);
    }

    /**
     * @return the id
     */
    @XmlElement
    public String getId() {
        return getStringField(ID);
    }

    public void setId(int id) {
        setField(ID, id);
    }
    
    public void setId(String id) {
        setField(ID, id);
    }
    
    /**
     * @return the type
     */
    @XmlElement
    public String getType() {
        return getStringField(TYPE);
    }

    public void setType(String type) {
        if (type == null) {
            type = "";
        }
        setField(TYPE, type);
    }

    /**
     * @return the moverIdIsVdm
     */
    @XmlElement
    public String getMoverIdIsVdm() {
        return getStringField(MOVER_ID_IS_VDM);
    }

    public void setMoverIdIsVdm(boolean moverIdIsVdm) {
        setField(MOVER_ID_IS_VDM, moverIdIsVdm);
    }

    /**
     * @return the interfaces
     */
    @XmlElement
    public List<String> getInterfaces() {
        return getListOfStringsField(INTERFACES);
    }

    public void setInterfaces(List<String> interfaces) {
        if (interfaces == null) {
            interfaces = new ArrayList<String>();
        }
        setListOfStringsField(INTERFACES, interfaces);
    }

    /**
     * @return the domain
     */
    @XmlElement
    public String getDomain() {
        return getStringField(DOMAIN);
    }

    public void setDomain(String domain) {
        if (domain == null) {
            domain = "";
        }
        setField(DOMAIN, domain);
    }

    @Override
    public String toString() {
        return new StringBuilder().append("name : ").append(getName()).append(" domain : ").append(getDomain()).append(" interfaces : ")
                .append(getInterfaces()).toString();
    }

}