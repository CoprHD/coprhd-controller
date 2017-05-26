package com.emc.storageos.varraygenerators;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
    
    /**
     * Returns true if template has the given attribute define
     * @param attribute - String name of attribute
     * @return - true if attribute defined
     */
    public boolean hasAttribute(String attribute) {
        return (getAttrMap().containsKey(attribute));
    }
    
    /**
     * Returns the system_type, if specified.
     * @return - String of system type
     */
    public String getSystemType() {
        if (attrMap.containsKey("arrayInfoDetails")) {
            Pattern pattern = Pattern.compile("system_type=([a-z_]*)");
            Matcher matcher = pattern.matcher(getAttribute("arrayInfoDetails"));
            if (matcher.find() && matcher.groupCount() >= 1) {
                return matcher.group(1);
            }
        }
        return null;
    }

}
