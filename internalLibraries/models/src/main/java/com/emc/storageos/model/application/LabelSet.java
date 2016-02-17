/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.model.application;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * A set of labels
 */
@XmlRootElement(name = "labels")
public class LabelSet {
    private Set<String> labels;

    public LabelSet() {
    }

    public LabelSet(Set<String> labels) {
        this.labels = labels;
    }

    /**
     * A set of labels
     */
    @XmlElement(name = "label")
    public Set<String> getLabels() {
        if (labels == null) {
            labels = new LinkedHashSet<String>();
        }
        return labels;
    }

    public void setLabels(Set<String> labels) {
        this.labels = labels;
    }
}
