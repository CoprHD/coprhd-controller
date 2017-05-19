package com.emc.storageos.varraygenerators;

import java.util.HashMap;
import java.util.Map;

public class VpoolTemplate {
    private Map<String, String> attrMap = new HashMap<String, String>();

    public Map<String, String> getAttrMap() {
        return attrMap;
    }

    public void setAttrMap(Map<String, String> attrMap) {
        this.attrMap = attrMap;
    }
    
    /**
     * Returns the value of an attribute, or an empty string
     * if there is no setting for the attribute
     * @param attribute - name of attribute
     * @return String value of attribute or "" if not present
     */
    public String getAttribute(String attribute) {
        if (!getAttrMap().containsKey(attribute)) {
            return "";
        }
        return getAttrMap().get(attribute);
    }

}
