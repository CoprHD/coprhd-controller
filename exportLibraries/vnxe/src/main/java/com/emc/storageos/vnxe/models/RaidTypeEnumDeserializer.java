/**
* Copyright 2015 EMC Corporation
* All Rights Reserved
 */
/**
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */

package com.emc.storageos.vnxe.models;

import java.io.IOException;


import com.emc.storageos.vnxe.models.RaidTypeEnum;

import org.codehaus.jackson.JsonParser;
import org.codehaus.jackson.JsonProcessingException;
import org.codehaus.jackson.map.DeserializationContext;
import org.codehaus.jackson.map.JsonDeserializer;

public class RaidTypeEnumDeserializer extends JsonDeserializer<RaidTypeEnum> {
    
    public RaidTypeEnum deserialize(final JsonParser parser, final DeserializationContext context) 
        throws IOException, JsonProcessingException    {
        final int jsonValue = parser.getIntValue();
        for (final RaidTypeEnum enumValue : RaidTypeEnum.values()) {
            if (enumValue.getValue() ==jsonValue) {
                return enumValue;
            }
        }
        return null;
    }

}
