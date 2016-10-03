/*
 * Copyright 2016 Dell Inc. or its subsidiaries.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */
package com.emc.sa.service.vipr.oe.primitive;

import java.util.HashMap;
import java.util.Map;

import com.emc.sa.service.vipr.oe.primitive.Parameter.Type;
import com.emc.storageos.db.client.model.OEParameterMetaData;
import com.emc.storageos.db.client.model.StringMap;
import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;

public class ParameterHelper {

    private ParameterHelper() {
    }

    public static Map<String, AbstractParameter<?>> toParameterMap(
            final Map<String, String> stringMap) {
        final Map<String, AbstractParameter<?>> parameterMap = new HashMap<String, AbstractParameter<?>>();
        final Map<String, Map<String, Parameter>> lists = new HashMap<String, Map<String, Parameter>>();
        final Gson gson = new Gson();
        for (final String parameterName : stringMap.keySet()) {
            final OEParameterMetaData metaData;
            try {
                metaData = gson.fromJson(stringMap.get(parameterName),
                        OEParameterMetaData.class);
            } catch (final JsonSyntaxException e) {
                // TODO throw a better exception? or don't bother catching this
                throw new RuntimeException();
            }

            final AbstractParameter<?> parameter;
            switch (metaData.getType()) {
            case "LIST":
                parameter = new ParameterList();
                ((ParameterList) parameter)
                        .setValue(new HashMap<String, Parameter>());
                break;
            default:
                parameter = new Parameter();
                ((Parameter) parameter).setValue(metaData.getValue());
                ((Parameter) parameter)
                        .setType(Type.valueOf(metaData.getType()));
                break;
            }
            parameter.setFriendlyName(metaData.getFriendlyName());
            parameter.setLocked(metaData.isLocked());
            parameter.setRequired(metaData.isRequired());

            // If this parameter is a member of a list add it to the list value
            // otherwise add it into the parameter map
            if (metaData.getListName() != null
                    && !metaData.getListName().isEmpty()) {
                if (parameter.isParameterList()) {
                    throw new RuntimeException(
                            "Parameter list cannot contain lists");
                }
                if (!lists.containsKey(metaData.getListName())) {
                    lists.put(metaData.getListName(),
                            new HashMap<String, Parameter>());
                }
                lists.get(metaData.getListName()).put(parameterName,
                        (Parameter) parameter);
            } else {
                if (null != parameterMap.putIfAbsent(parameterName, parameter))
                    throw new RuntimeException("Duplicate parameter name");
            }
        }

        for (final String list : lists.keySet()) {
            final AbstractParameter<?> parameterList = parameterMap.get(list);
            if (null == parameterList) {
                throw new RuntimeException(
                        "Invalid primitive parameter list does not exist");
            } else if (!parameterList.getClass().isAssignableFrom(
                    ParameterList.class)) {
                throw new RuntimeException(
                        "Invalid primitive paramater is not a list");
            }
            ((ParameterList) parameterList).setValue(lists.get(list));
        }

        return parameterMap;
    }

    public static StringMap toStringMap(
            final Map<String, AbstractParameter<?>> parameterMap) {
        final StringMap stringMap = new StringMap();
        final Gson gson = new Gson();
        for (final String parameterName : parameterMap.keySet()) {
            final OEParameterMetaData metaData = new OEParameterMetaData();
            final AbstractParameter<?> parameterValue = parameterMap
                    .get(parameterName);
            if (parameterValue.isParameterList()) {
                metaData.setType("LIST");
                metaData.setValue("");
                for (final String listMemberName : parameterValue
                        .asParameterList().getValue().keySet()) {
                    final Parameter listMemberValue = parameterValue
                            .asParameterList().getValue().get(listMemberName);
                    final OEParameterMetaData listMemberMetaData = new OEParameterMetaData();
                    listMemberMetaData.setFriendlyName(listMemberValue
                            .getFriendlyName());
                    listMemberMetaData.setListName(parameterName);
                    listMemberMetaData.setLocked(listMemberValue.isLocked());
                    listMemberMetaData
                            .setRequired(listMemberValue.isRequired());
                    listMemberMetaData
                            .setType(listMemberValue.getType().name());
                    listMemberMetaData.setValue(listMemberValue.getValue());
                    stringMap.put(listMemberName,
                            gson.toJson(listMemberMetaData));
                }
            } else {
                metaData.setType(parameterValue.asParameter().getType().name());
                metaData.setValue(parameterValue.asParameter().getValue());
            }
            metaData.setFriendlyName(parameterValue.getFriendlyName());
            metaData.setLocked(parameterValue.isLocked());
            metaData.setRequired(parameterValue.isRequired());

            stringMap.put(parameterName, gson.toJson(metaData));
        }
        return stringMap;
    }

    /**
     * @param input
     * @param input2
     * @return
     */
    public static Map<String, AbstractParameter<?>> merge(
            final Map<String, AbstractParameter<?>> child,
            final Map<String, AbstractParameter<?>> parent) {

        return null;
    }
}
