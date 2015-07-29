/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.hds.xmlgen.beans;

import java.util.List;

public class Operation {
    private String name;
    private String models;
    private String xmlElementSequence;
    private String parent;
    private List<XmlElementSequenceAttribute> xmlElementSequenceAttributeList;
    private String xmlElementsToClose;

    /**
     * @return the name
     */
    public String getName() {
        return name;
    }

    /**
     * @param name the name to set
     */
    public void setName(String name) {
        this.name = name;
    }

    /**
     * @return the models
     */
    public String getModels() {
        return models;
    }

    /**
     * @param models the models to set
     */
    public void setModels(String models) {
        this.models = models;
    }

    /**
     * @return the parent
     */
    public String getParent() {
        return parent;
    }

    /**
     * @param parent the parent to set
     */
    public void setParent(String parent) {
        this.parent = parent;
    }

    /**
     * @return the xmlElementSequence
     */
    public String getXmlElementSequence() {
        return xmlElementSequence;
    }

    /**
     * @param xmlElementSequence the xmlElementSequence to set
     */
    public void setXmlElementSequence(String xmlElementSequence) {
        this.xmlElementSequence = xmlElementSequence;
    }

    /**
     * @return the xmlElementSequenceAttributeList
     */
    public List<XmlElementSequenceAttribute> getXmlElementSequenceAttributeList() {
        return xmlElementSequenceAttributeList;
    }

    /**
     * @param xmlElementSequenceAttributeList the xmlElementSequenceAttributeList to set
     */
    public void setXmlElementSequenceAttributeList(
            List<XmlElementSequenceAttribute> xmlElementSequenceAttributeList) {
        this.xmlElementSequenceAttributeList = xmlElementSequenceAttributeList;
    }

    /**
     * @return the xmlElementsToClose
     */
    public String getXmlElementsToClose() {
        return xmlElementsToClose;
    }

    /**
     * @param xmlElementsToClose the xmlElementsToClose to set
     */
    public void setXmlElementsToClose(String xmlElementsToClose) {
        this.xmlElementsToClose = xmlElementsToClose;
    }
}
