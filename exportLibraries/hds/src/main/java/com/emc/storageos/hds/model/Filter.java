package com.emc.storageos.hds.model;

import com.emc.storageos.hds.HDSConstants;
import com.emc.storageos.hds.xmlgen.XMLConstants;

public class Filter {
    Condition condition;

    /**
     * @return the condition
     */
    public Condition getCondition() {
        return condition;
    }

    /**
     * @param condition the condition to set
     */
    public void setCondition(Condition condition) {
        this.condition = condition;
    }

    public String toXMLString() {
        return "";
    }

    public String getChildNodeXMLString() {
        StringBuilder childNodeXmlString = new StringBuilder();
        if (null != this.condition) {
            childNodeXmlString.append(XMLConstants.LESS_THAN_OP).append(HDSConstants.CONDITION)
                    .append(this.condition.toXMLString()).append(XMLConstants.XML_CLOSING_TAG);

        }
        return childNodeXmlString.toString();
    }

}
