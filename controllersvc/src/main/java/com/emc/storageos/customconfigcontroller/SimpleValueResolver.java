/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.customconfigcontroller;

import com.emc.storageos.customconfigcontroller.exceptions.CustomConfigControllerException;
import com.emc.storageos.db.client.model.StringMap;

public class SimpleValueResolver extends CustomConfigResolver {

    private static final long serialVersionUID = -5310184248459285708L;
    private static final String INTEGER = "Integer";
    private static final String FLOAT = "Float";

    @Override
    public void validate(CustomConfigType configType, StringMap scope,
            String value) {
        // validate value type
        String type = configType.getType();
        try {
            if (type.equals(INTEGER)) {
                Integer.parseInt(value);
            } else if (type.equals(FLOAT)) {
                Float.parseFloat(value);
            }
        } catch (NumberFormatException e) {
            throw CustomConfigControllerException.exceptions.invalidValueType(
                    type, value, configType.getName());
        }

    }

    @Override
    public String resolve(CustomConfigType configType, StringMap scope,
            String value, DataSource datasource) {

        return value;
    }

}
