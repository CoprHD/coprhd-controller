/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.db.client.model;

import com.emc.storageos.model.usergroup.UserAttributeParam;
import org.apache.commons.lang.StringUtils;
import org.codehaus.jackson.map.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.CollectionUtils;

import java.io.IOException;

/**
 * ViPR user group configuration data object
 */

@Cf("UserGroup")
@AllowedGeoVersion(version = "2.3")
@DbKeyspace(DbKeyspace.Keyspaces.GLOBAL)
public class UserGroup extends DataObject {
    private static final Logger _log = LoggerFactory.getLogger(UserGroup.class);

    private String _domain;
    private StringSet _attributes;

    @Name("domain")
    @PrefixIndex(cf = "DomainPrefixIndex", minChars = 1)
    public String getDomain() {
        return _domain;
    }

    public void setDomain(String _domain) {
        this._domain = _domain;
        setChanged("domain");
    }

    @Name("attributes")
    public StringSet getAttributes() {
        if (_attributes == null) {
            _attributes = new StringSet();
        }
        return _attributes;
    }

    public void setAttributes(StringSet _attributes) {
        this._attributes = _attributes;
        setChanged("attributes");
    }

    @Override
    public String toString() {
        ObjectMapper mapper = new ObjectMapper();
        try {
            _domain = _domain.toLowerCase();
            return mapper.writeValueAsString(this);
        } catch (IOException e) {
            _log.error("Failed to convert user group to string.", e);
        }
        return null;
    }

    public static UserGroup fromString(String userMappingString) {
        ObjectMapper mapper = new ObjectMapper();
        try {
            return mapper.readValue(userMappingString, UserGroup.class);
        } catch (IOException e) {
            _log.error("Failed to convert user group from string.", e);
        }
        return null;
    }

    private StringSet getAttributeKeySet() {
        StringSet attributeKeySet = new StringSet();

        for (String attributeParamString : _attributes) {
            UserAttributeParam attributeParam = UserAttributeParam.fromString(attributeParamString);
            if (attributeParam == null) {
                _log.warn("Failed to convert attributes param string {} to object.", attributeParamString);
                continue;
            }

            attributeKeySet.add(attributeParam.getKey());
        }

        return attributeKeySet;
    }

    /***
     * Common compare method for both isEqual and overlap.
     * 
     * @param userGroup to be compared.
     * @return true if userGroup's _domain matches with
     *         the object otherwise false.
     */
    private boolean commonCompare(UserGroup userGroup) {
        if (userGroup == null) {
            _log.warn("Invalid user group to compare");
            return false;
        }

        _log.debug("Comparing user groups {}, {}",
                userGroup.toString(), this.toString());

        // Now compare the domain of both user group.
        if (StringUtils.isBlank(this._domain) ||
                StringUtils.isBlank(userGroup._domain) &&
                !this._domain.equalsIgnoreCase(userGroup._domain)) {
            String[] toTrace = { this._domain, this.getLabel(), userGroup._domain, userGroup.getLabel() };
            _log.debug("Domain {} of {} does not match with domain {} of {}.", toTrace);
            return false;
        }

        return true;
    }

    /***
     * Checks if the given userGroup is matching with the object or not
     * The comparison is done based on the _domain and _attributes.
     * 
     * @param userGroup to be compared.
     * @return true if userGroup's _domain and _attributes matches with
     *         the object otherwise false.
     */
    public boolean isEqual(UserGroup userGroup) {
        boolean isEqual = false;

        if (!commonCompare(userGroup)) {
            return isEqual;
        }

        // Now compare the size of attributes list.
        if (CollectionUtils.isEmpty(this._attributes) ||
                CollectionUtils.isEmpty(userGroup._attributes) ||
                this._attributes.size() != userGroup._attributes.size()) {
            _log.debug("Invalid attributes or attributes size does not match.");
            return isEqual;
        }

        // Now compare the each individual attribute of the group.
        for (String attributeParamString : this._attributes) {
            isEqual = compareUserAttributeParam(userGroup, attributeParamString);
            if (!isEqual) {
                break;
            }
        }

        return isEqual;
    }

    /***
     * Checks if the object's _attributes is available in the
     * userGroup's attributes or not .
     * 
     * @param userGroup to be compared.
     * @param attributeParamString attribute of the object to be compared.
     * @return true if object's _attribute matches with one of
     *         userGroup's _attributes matches with otherwise false.
     */
    private boolean compareUserAttributeParam(UserGroup userGroup, String attributeParamString) {
        boolean isEqual = false;

        if (StringUtils.isBlank(attributeParamString)) {
            _log.warn("Invalid attribute string {} in user group {}.", attributeParamString, this.getLabel());
            return isEqual;
        }

        UserAttributeParam attributeParam = UserAttributeParam.fromString(attributeParamString);
        if (attributeParam == null) {
            _log.warn("Failed to convert attributes param string {} to object.", attributeParamString);
            return isEqual;
        }

        if (CollectionUtils.isEmpty(userGroup._attributes)) {
            _log.info("No attributes to compare");
            return isEqual;
        }

        for (String comparingAttributeParamString : userGroup._attributes) {
            if (StringUtils.isBlank(comparingAttributeParamString)) {
                _log.info("Invalid attribute string {}", comparingAttributeParamString);
                break;
            }

            UserAttributeParam comparingAttributeParam = UserAttributeParam.fromString(comparingAttributeParamString);
            if (comparingAttributeParam == null) {
                _log.info("Failed to convert attributes param string {} to object.", comparingAttributeParamString);
                return isEqual;
            }

            if (comparingAttributeParam.isEqual(attributeParam)) {
                _log.debug("Attributes {} match with {}", attributeParamString, comparingAttributeParamString);
                isEqual = true;
                break;
            }
        }

        return isEqual;
    }

    /***
     * Checks if the keys of object's all the _attributes is available
     * in the userGroup's attributes or not .
     * 
     * @param userGroup to be compared.
     * @return true if all the keys of object attributes are also available
     *         in the userGroup's attributes otherwise false.
     */
    private boolean checkIfAllKeysAvailable(UserGroup userGroup) {
        boolean isAllKeysFound = true;

        StringSet objectAttributeKeySet = getAttributeKeySet();
        StringSet comparingAttributeKeySet = userGroup.getAttributeKeySet();

        for (String objectKey : objectAttributeKeySet) {
            boolean keyFound = false;
            for (String comparingKey : comparingAttributeKeySet) {
                if (comparingKey.equalsIgnoreCase(objectKey)) {
                    keyFound = true;
                    break;
                }
            }
            if (!keyFound) {
                isAllKeysFound = false;
                break;
            }
        }
        return isAllKeysFound;
    }

    /***
     * Checks if the object's _attribute overlaps with
     * userGroup's attributes or not .
     * 
     * @param userGroup to be compared.
     * @param attributeParamString attribute of the object to be compared.
     * @return true if object's _attribute overlaps with one of
     *         userGroup's _attributes otherwise false.
     */
    private boolean checkOverlappingAttributes(UserGroup userGroup, String attributeParamString) {
        boolean overlaps = false;

        if (StringUtils.isBlank(attributeParamString)) {
            _log.warn("Invalid attribute string {} in user group {}.", attributeParamString, this.getLabel());
            return overlaps;
        }

        UserAttributeParam attributeParam = UserAttributeParam.fromString(attributeParamString);
        if (attributeParam == null) {
            _log.warn("Failed to convert attributes param string {} to object.", attributeParamString);
            return overlaps;
        }

        if (CollectionUtils.isEmpty(userGroup._attributes)) {
            _log.info("No attributes to compare");
            return overlaps;
        }

        for (String comparingAttributeParamString : userGroup._attributes) {
            if (StringUtils.isBlank(comparingAttributeParamString)) {
                _log.info("Invalid attribute string {}", comparingAttributeParamString);
                break;
            }

            UserAttributeParam comparingAttributeParam = UserAttributeParam.fromString(comparingAttributeParamString);
            if (comparingAttributeParam == null) {
                _log.info("Failed to convert attributes param string {} to object.", comparingAttributeParamString);
                return overlaps;
            }

            if (comparingAttributeParam.containsOverlappingAttributeValues(attributeParam)) {
                _log.debug("Attributes {} match with {}", attributeParamString, comparingAttributeParamString);
                overlaps = true;
                break;
            }
        }

        return overlaps;
    }

    /***
     * Checks if the given userGroup overlapping with the object or not.
     * The comparison is done based on the _domain and _attributes.
     * 
     * @param userGroup to be compared.
     * @return true if userGroup's _domain and _attributes become overlaps with
     *         object otherwise false.
     */
    public boolean overlap(UserGroup userGroup) {
        boolean overlaps = false;

        if (!commonCompare(userGroup)) {
            return overlaps;
        }

        // Now compare the attribute keys. To overlap, object's all the attribute keys
        // should be available in the userGroup.
        if (!checkIfAllKeysAvailable(userGroup)) {
            _log.debug("User group {} does not contain all the attribute keys of {}", userGroup.getLabel(), this.getLabel());
            return overlaps;
        }

        // Now compare the each individual attribute of the group.
        for (String attributeParamString : this._attributes) {
            overlaps = checkOverlappingAttributes(userGroup, attributeParamString);
            if (!overlaps) {
                break;
            }
        }

        return overlaps;
    }
}
