/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.vnxe.models;

import java.io.IOException;

import com.emc.storageos.vnxe.models.CifsShareACE.ACEAccessLevelEnum;

import org.codehaus.jackson.JsonParser;
import org.codehaus.jackson.JsonProcessingException;
import org.codehaus.jackson.map.DeserializationContext;
import org.codehaus.jackson.map.JsonDeserializer;

public class ACEAccessLevelEnumDeserializer extends JsonDeserializer<ACEAccessLevelEnum> {

    public ACEAccessLevelEnum deserialize(final JsonParser parser, final DeserializationContext context)
            throws IOException, JsonProcessingException {
        final int jsonValue = parser.getIntValue();
        for (final ACEAccessLevelEnum enumValue : ACEAccessLevelEnum.values()) {
            if (enumValue.getValue() == jsonValue) {
                return enumValue;
            }
        }
        return null;
    }

}
