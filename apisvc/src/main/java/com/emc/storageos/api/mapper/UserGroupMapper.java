/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.api.mapper;

import com.emc.storageos.db.client.model.UserGroup;
import com.emc.storageos.model.usergroup.UserAttributeParam;
import com.emc.storageos.model.usergroup.UserGroupCreateParam;
import com.emc.storageos.model.usergroup.UserGroupRestRep;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.CollectionUtils;

import java.util.Map;
import java.util.TreeMap;

import static com.emc.storageos.api.mapper.DbObjectMapper.mapDataObjectFields;

/**
 * This class maps between UserGroup object model and rest representations
 */

public class UserGroupMapper {
    private static final Logger _log = LoggerFactory.getLogger(UserGroupMapper.class);

    public static final UserGroupRestRep map(UserGroup from) {
        if (from == null) {
            _log.info("Invalid user group");
            return null;
        }

        UserGroupRestRep to = new UserGroupRestRep();
        mapDataObjectFields(from, to);
        to.setDomain(from.getDomain());

        if (CollectionUtils.isEmpty(from.getAttributes())) {
            _log.error("Empty attributes list in user group {}", from.getLabel());
            return to;
        }

        for (String attributeString : from.getAttributes()) {
            if (StringUtils.isBlank(attributeString)) {
                _log.info("Invalid attribute param string in user group {}", from.getLabel());
                continue;
            }

            UserAttributeParam attribute = UserAttributeParam.fromString(attributeString);
            to.getAttributes().add(attribute);
        }

        return to;
    }

    public static final UserGroup map(UserGroupCreateParam from) {
        if (from == null) {
            _log.info("Invalid user group create param");
            return null;
        }

        UserGroup to = new UserGroup();
        to.setDomain(from.getDomain());
        to.setLabel(from.getLabel());

        Map<String, UserAttributeParam> attributeParamMap = new TreeMap<String, UserAttributeParam>(String.CASE_INSENSITIVE_ORDER);
        if (!CollectionUtils.isEmpty(from.getAttributes())) {
            for (UserAttributeParam attribute : from.getAttributes()) {
                UserAttributeParam userAttributeParam = attributeParamMap.get(attribute.getKey());
                if (userAttributeParam == null) {
                    userAttributeParam = new UserAttributeParam(attribute.getKey(), attribute.getValues());
                    attributeParamMap.put(attribute.getKey(), userAttributeParam);
                } else {
                    userAttributeParam.getValues().addAll(attribute.getValues());
                }
            }
        } else {
            _log.error("Empty attributes list in user group create param {}", from.getLabel());
        }

        if (!CollectionUtils.isEmpty(attributeParamMap)) {
            for (UserAttributeParam attribute : attributeParamMap.values()) {
                to.getAttributes().add(attribute.toString());
            }
        } else {
            _log.warn("Mapping from UserGroupCreateParam {} to UserGroup did not create any attributes list", from.getLabel());
        }

        return to;
    }
}
