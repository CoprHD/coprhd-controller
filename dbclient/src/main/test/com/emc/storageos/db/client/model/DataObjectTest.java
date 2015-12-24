package com.emc.storageos.db.client.model;

import org.junit.Test;

public class DataObjectTest {
    private DummyObject object = new DummyObject();
    private static final String VALID_LABEL = "abc";
    private static final String INVALID_LABEL = "A";
    
    @Test
    public void shouldWorksIfLabelLengthNoLessThanTwo() {
        object.setLabel(VALID_LABEL);
    }
    
    @Test(expected=Exception.class)
    public void shouldThrowExceptionIfLabelLengthLessThanTwo() {
        object.setLabel(INVALID_LABEL);
    }
    
    class DummyObject extends DataObject {
    }
}
