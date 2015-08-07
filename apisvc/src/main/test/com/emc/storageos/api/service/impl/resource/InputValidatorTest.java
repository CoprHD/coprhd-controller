/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.api.service.impl.resource;

import com.emc.storageos.model.valid.Endpoint;
import com.emc.storageos.model.valid.EnumType;
import com.emc.storageos.model.valid.Length;
import com.emc.storageos.model.valid.Range;
import com.emc.storageos.svcs.errorhandling.resources.BadRequestException;
import org.junit.Test;
import javax.xml.bind.annotation.XmlElement;
import java.util.ArrayList;
import java.util.List;

public class InputValidatorTest {

    @Test
    public void testHappyPath() {
        validate(newFieldObject());
    }

    /*
     * Field Tests
     */

    @Test(expected = BadRequestException.class)
    public void testFieldsFailRequired() {
        ValidateFields testObj = newFieldObject();
        testObj.requiredField = null;
        validate(testObj);
    }

    @Test(expected = BadRequestException.class)
    public void testFieldsFailNillable() {
        ValidateFields testObj = newFieldObject();
        testObj.requiredField = "";
        validate(testObj);
    }

    @Test(expected = BadRequestException.class)
    public void testFieldsFailEnum() {
        ValidateFields testObj = newFieldObject();
        testObj.enumeration = "BAD";
        validate(testObj);
    }

    @Test(expected = BadRequestException.class)
    public void testFieldsMaxNumber() {
        ValidateFields testObj = newFieldObject();
        testObj.number = 5000;
        validate(testObj);
    }

    @Test(expected = BadRequestException.class)
    public void testFieldsMinNumber() {
        ValidateFields testObj = newFieldObject();
        testObj.number = 0;
        validate(testObj);
    }

    @Test(expected = BadRequestException.class)
    public void testFieldsLongString() {
        ValidateFields testObj = newFieldObject();
        testObj.label = "asdfasdfasdfasdfasdfasdfasdfasdfasdfassajlkafsdljkdfsakjldafsjkladfskljdfasjkladfslkjadfsljkfdsalkjdfsakldfsaljkafdsljkafskjlsafdljkfsdaljkafsdljkfadslkjaflsdjklfasdkjlfjkadsljkfasddfasdfjhadsfjkhfdhjkdsfhjfdshjkdfskhjsdfkjhsdfkjhsdf";
        validate(testObj);
    }

    @Test(expected = BadRequestException.class)
    public void testFieldsShortString() {
        ValidateFields testObj = newFieldObject();
        testObj.label = "a";
        validate(testObj);
    }

    @Test(expected = BadRequestException.class)
    public void testFieldsCollection() {
        ValidateFields testObj = newFieldObject();
        testObj.values.add("a");
        validate(testObj);
    }

    @Test(expected = BadRequestException.class)
    public void testFieldsNonIp() {
        ValidateFields testObj = newFieldObject();
        testObj.ip = "3.34";
        validate(testObj);
    }

    private ValidateFields newFieldObject() {
        ValidateFields testObj = new ValidateFields();
        testObj.requiredField = "hi";
        testObj.nillable = "";
        testObj.enumeration = ValidateEnum.B.toString();
        testObj.number = 3;
        testObj.label = "abc";
        testObj.values.add("abc");
        testObj.ip = "127.0.0.1";
        return testObj;
    }

    /*
     * Properties Tests
     */

    @Test(expected = BadRequestException.class)
    public void testPropsFailRequired() {
        ValidateProperties testObj = newPropObject();
        testObj.setRequiredField(null);
        validate(testObj);
    }

    @Test(expected = BadRequestException.class)
    public void testPropsFailNillable() {
        ValidateProperties testObj = newPropObject();
        testObj.setRequiredField("");
        validate(testObj);
    }

    @Test(expected = BadRequestException.class)
    public void testPropsFailEnum() {
        ValidateProperties testObj = newPropObject();
        testObj.setEnumeration("BAD");
        validate(testObj);
    }

    @Test(expected = BadRequestException.class)
    public void testPropsMaxNumber() {
        ValidateProperties testObj = newPropObject();
        testObj.setNumber(5000);
        validate(testObj);
    }

    @Test(expected = BadRequestException.class)
    public void testPropsMinNumber() {
        ValidateProperties testObj = newPropObject();
        testObj.setNumber(0);
        validate(testObj);
    }

    @Test(expected = BadRequestException.class)
    public void testPropsLongString() {
        ValidateProperties testObj = newPropObject();
        testObj.setLabel("asdfasdfasdfasdfasdfasdfasdfasdfasdfassajlkafsdljkdfsakjldafsjkladfskljdfasjkladfslkjadfsljkfdsalkjdfsakldfsaljkafdsljkafskjlsafdljkfsdaljkafsdljkfadslkjaflsdjklfasdkjlfjkadsljkfasddfasdfjhadsfjkhfdhjkdsfhjfdshjkdfskhjsdfkjhsdfkjhsdf");
        validate(testObj);
    }

    @Test(expected = BadRequestException.class)
    public void testPropsShortString() {
        ValidateProperties testObj = newPropObject();
        testObj.setLabel("a");
        validate(testObj);
    }

    @Test(expected = BadRequestException.class)
    public void testPropsCollection() {
        ValidateProperties testObj = newPropObject();
        testObj.getValues().add("a");
        validate(testObj);
    }

    @Test(expected = BadRequestException.class)
    public void testPropsNonIp() {
        ValidateProperties testObj = newPropObject();
        testObj.setIp("3.34");
        validate(testObj);
    }

    private ValidateProperties newPropObject() {
        ValidateProperties testObj = new ValidateProperties();
        testObj.setRequiredField("hi");
        testObj.setNillable("");
        testObj.setEnumeration(ValidateEnum.B.toString());
        testObj.setNumber(3);
        testObj.setLabel("abc");
        testObj.getValues().add("abc");
        testObj.setIp("127.0.0.1");
        return testObj;
    }

    private void validate(Object obj) {
        InputValidator.getInstance().validate(obj);
    }

    private enum ValidateEnum {
        A, B, C
    }

    private class ValidateFields {
        @XmlElement(required = true, nillable = false)
        public String requiredField;

        @XmlElement(nillable = true)
        public String nillable;

        @EnumType(ValidateEnum.class)
        public String enumeration;

        @Range(min = 2, max = 128)
        public Integer number;

        @Length(min = 2, max = 128)
        public String label;

        @Length(min = 2, max = 128)
        public List<String> values = new ArrayList<String>();

        @Endpoint(type = Endpoint.EndpointType.IPV4)
        public String ip;
    }

    private class ValidateProperties {
        private String requiredField;
        private String nillable;
        private String enumeration;
        private Integer number;
        private String label;
        private List<String> values;
        private String ip;

        @XmlElement(required = true, nillable = false)
        public String getRequiredField() {
            return requiredField;
        }

        public void setRequiredField(String requiredField) {
            this.requiredField = requiredField;
        }

        @XmlElement(nillable = true)
        public String getNillable() {
            return nillable;
        }

        public void setNillable(String nillable) {
            this.nillable = nillable;
        }

        @EnumType(ValidateEnum.class)
        public String getEnumeration() {
            return enumeration;
        }

        public void setEnumeration(String enumeration) {
            this.enumeration = enumeration;
        }

        @Range(min = 2, max = 128)
        public Integer getNumber() {
            return number;
        }

        public void setNumber(Integer number) {
            this.number = number;
        }

        @Length(min = 2, max = 128)
        public String getLabel() {
            return label;
        }

        public void setLabel(String label) {
            this.label = label;
        }

        @Length(min = 2, max = 128)
        public List<String> getValues() {
            if (values == null) {
                values = new ArrayList<String>();
            }
            return values;
        }

        public void setValues(List<String> values) {
            this.values = values;
        }

        @Endpoint(type = Endpoint.EndpointType.IPV4)
        public String getIp() {
            return ip;
        }

        public void setIp(String ip) {
            this.ip = ip;
        }
    }
}
