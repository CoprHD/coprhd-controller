/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.model.customconfig;

import java.util.List;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

@XmlRootElement(name = "rules")
public class CustomConfigRuleList {
    private List<String> rules;

    public CustomConfigRuleList() {
    }

    public CustomConfigRuleList(List<String> rules) {
        this.rules = rules;
    }

    @XmlElement(name = "rule")
    public List<String> getRules() {
        return rules;
    }

    public void setRules(List<String> rules) {
        this.rules = rules;
    }

}
