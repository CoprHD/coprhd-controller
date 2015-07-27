/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package controllers.tenant;

import java.util.List;

import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang.StringUtils;

import com.emc.storageos.model.tenant.UserMappingAttributeParam;
import com.emc.storageos.model.tenant.UserMappingParam;
import com.google.common.collect.Lists;

public class UserMappingForm {
    private static String LINE_BREAK = "\r\n";

    public String domain = "";
    public List<AttributeMapping> attributes = Lists.newArrayList();
    public String groups = "";

    public void validate() {
       //getAttributeValues();
    }

    public UserMappingParam createUserMappingParam() {
        UserMappingParam userMappingParam = new UserMappingParam();

        userMappingParam.setDomain(domain);

        for (String group : groups.split(LINE_BREAK)) {
            if (!StringUtils.isBlank(group)) {
                userMappingParam.getGroups().add(group.trim());
            }
        }

        List<UserMappingAttributeParam> attributeParams = Lists.newArrayList();
        if (!attributes.isEmpty()) {
            for (AttributeMapping mapping : attributes) {
                if (mapping != null)  {
                    attributeParams.add(mapping.createAttributeParam());
                }
            }
        }
        userMappingParam.setAttributes(attributeParams);

        return userMappingParam;
    }

    public static List<UserMappingForm> loadUserMappingForms(List<UserMappingParam> userMappingParamEntries) {
        List<UserMappingForm> userMappingForms = Lists.newArrayList();

        if (userMappingParamEntries != null) {

            for (UserMappingParam userMappingParam : userMappingParamEntries) {
                List<AttributeMapping> attributes = Lists.newArrayList();
                for (UserMappingAttributeParam attributeParam : userMappingParam.getAttributes()) {
                    AttributeMapping mapping = new AttributeMapping();
                    mapping.name = attributeParam.getKey();
                    mapping.values = StringUtils.join(attributeParam.getValues(), LINE_BREAK);

                    attributes.add(mapping);
                }

                UserMappingForm form = new UserMappingForm();
                form.domain = userMappingParam.getDomain();

                form.groups = StringUtils.join(userMappingParam.getGroups(), LINE_BREAK);
                form.attributes = attributes;

                userMappingForms.add(form);
            }
        }

        return userMappingForms;
    }


    public static List<UserMappingParam> getAddedMappings(List<UserMappingParam> existingUserMappings, List<UserMappingForm> mappingForms) {
        List<UserMappingParam> added = Lists.newArrayList();
        for (UserMappingForm userMappingForm : mappingForms) {
            if (userMappingForm != null) {
                boolean found = false;
                UserMappingParam userMappingParam = userMappingForm.createUserMappingParam();
                for (UserMappingParam existingMapping : existingUserMappings) {
                    if (isSameMapping(userMappingParam, existingMapping)) {
                        found = true;
                        break;
                    }
                }
                if (!found) {
                    added.add(userMappingParam);
                }
            }
        }
        return added;
    }

    public static List<UserMappingParam> getRemovedMappings(List<UserMappingParam> existingUserMappings, List<UserMappingForm> mappingForms) {
        List<UserMappingParam> removed = Lists.newArrayList();
        for (UserMappingParam existingMapping : existingUserMappings) {
            if (existingMapping != null) {
                boolean found = false;
                for (UserMappingForm userMappingForm : mappingForms) {
                    if (userMappingForm != null) {
                        UserMappingParam userMappingParam = userMappingForm.createUserMappingParam();
                        if (isSameMapping(userMappingParam, existingMapping)) {
                            found = true;
                        }
                    }
                }
                if (!found) {
                    removed.add(existingMapping);
                }
            }
        }
        return removed;
    }

    public static boolean isSameMapping(UserMappingParam left, UserMappingParam right) {
        if (left == null && right == null) {
            return true;
        }

        if (left == null || right == null) {
        	return false;
        }
        
        if (!left.getDomain().equals(right.getDomain())) { //NOSONAR (Suppressing null pointer dereference of left. When left is null, previous if handles it. 
            return false;
        }

        // Check Groups
        if (!CollectionUtils.isEqualCollection(left.getGroups(), right.getGroups())) {
            return false;
        }

        // Check Attributes
        if (left.getAttributes().size() != right.getAttributes().size()) {
            return false;
        }

        for (UserMappingAttributeParam leftAttribute : left.getAttributes()) {
            boolean found = false;
            for (UserMappingAttributeParam rightAttribute : right.getAttributes()) {
                if (leftAttribute.getKey().equals(rightAttribute.getKey())) {
                    if (!CollectionUtils.isEqualCollection(leftAttribute.getValues(), rightAttribute.getValues())) {
                         return false;
                    }

                    found = true;
                    break;
                }
            }

            if (!found) {
                return false;
            }
        }

        return true;
    }

    public static class AttributeMapping {
        public String name;
        public String values;

        public UserMappingAttributeParam createAttributeParam() {
            UserMappingAttributeParam attributeParam = new UserMappingAttributeParam();
            attributeParam.setKey(name);

            List<String> valueList = Lists.newArrayList();

            if (!StringUtils.isBlank(values)) {
                String[] vals = values.split(LINE_BREAK);
                for (String val : vals) {
                    valueList.add(val.trim());
                }
            }
            attributeParam.setValues(valueList);

            return attributeParam;
        }
    }
}
