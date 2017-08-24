/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.db.client.model.uimodels;

import com.emc.storageos.db.client.model.Cf;
import com.emc.storageos.db.client.model.ModelObject;
import com.emc.storageos.db.client.model.Name;
import com.emc.storageos.db.client.model.RelationIndex;

import java.net.URI;

@Cf("OrderParameter")
public class OrderParameter extends ModelObject implements SortedIndexDataObject {

    public static final String LABEL = "label";
    public static final String VALUE = "value";
    public static final String FRIENDLY_LABEL = "friendlyLabel";
    public static final String FRIENDLY_VALUE = "friendlyValue";
    public static final String USER_INPUT = "userInput";
    public static final String ENCRYPTED = "encrypted";
    public static final String SORTED_INDEX = "sortedIndex";
    public static final String ORDER_ID = "orderId";

    private String value;

    private String friendlyLabel;

    private String friendlyValue;

    private Boolean userInput = Boolean.TRUE;

    private Boolean encrypted = Boolean.FALSE;

    private Integer sortedIndex;

    private URI orderId;

    @Name(VALUE)
    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
        setChanged(VALUE);
    }

    @Name(FRIENDLY_LABEL)
    public String getFriendlyLabel() {
        return friendlyLabel;
    }

    public void setFriendlyLabel(String friendlyLabel) {
        this.friendlyLabel = friendlyLabel;
        setChanged(FRIENDLY_LABEL);
    }

    @Name(FRIENDLY_VALUE)
    public String getFriendlyValue() {
        return friendlyValue;
    }

    public void setFriendlyValue(String friendlyValue) {
        this.friendlyValue = friendlyValue;
        setChanged(FRIENDLY_VALUE);
    }

    @Name(USER_INPUT)
    public Boolean getUserInput() {
        return userInput;
    }

    public void setUserInput(Boolean userInput) {
        this.userInput = userInput;
        setChanged(USER_INPUT);
    }

    @Name(ENCRYPTED)
    public Boolean getEncrypted() {
        return encrypted;
    }

    public void setEncrypted(Boolean encrypted) {
        this.encrypted = encrypted;
        setChanged(ENCRYPTED);
    }

    @Name(SORTED_INDEX)
    public Integer getSortedIndex() {
        return sortedIndex;
    }

    public void setSortedIndex(Integer sortedIndex) {
        this.sortedIndex = sortedIndex;
        setChanged(SORTED_INDEX);
    }

    @RelationIndex(cf = "RelationIndex", type = Order.class)
    @Name(ORDER_ID)
    public URI getOrderId() {
        return orderId;
    }

    public void setOrderId(URI orderId) {
        this.orderId = orderId;
        setChanged(ORDER_ID);
    }

    @Override
    public Object[] auditParameters() {
        return new Object[] { getLabel(), getId() };
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        builder.append(getFriendlyLabel())
                .append(":")
                .append(getFriendlyValue())
                .append("\n");

        return builder.toString();
    }
}
