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

import java.net.URI;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import com.emc.sa.service.vipr.oe.primitive.Parameter.Type;
import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.URIUtil;
import com.emc.storageos.db.client.model.NamedURI;
import com.emc.storageos.db.client.model.OEAbstractParameter;
import com.emc.storageos.db.client.model.OEParameter;
import com.emc.storageos.db.client.model.OEParameterList;
import com.emc.storageos.db.client.model.StringSet;

public class ParameterHelper {

    private ParameterHelper() {
    }

    /**
     * @param input
     * @return
     */
    public static Map<String, AbstractParameter<?>> toParameterMap(
            final DbClient dbClient, final StringSet input) {
        final Map<String, AbstractParameter<?>> parameters = new HashMap<String, AbstractParameter<?>>();
        if (null == input || input.isEmpty()) {
            return parameters;
        } else {
            for (final URI inputUri : URIUtil.toURIList(input)) {
                final Class<?> type = URIUtil.getModelClass(inputUri);
                if (type.isAssignableFrom(OEParameter.class)) {
                    final OEParameter oeParameter = dbClient.queryObject(
                            OEParameter.class, inputUri);
                    parameters.put(oeParameter.getName(),
                            toParameter(oeParameter));

                } else if (type.isAssignableFrom(OEParameterList.class)) {
                    final OEParameterList oeParameterList = dbClient
                            .queryObject(OEParameterList.class, inputUri);
                    parameters.put(oeParameterList.getName(),
                            toParameterList(dbClient, oeParameterList));
                }
            }

            return parameters;
        }
    }

    private static ParameterList toParameterList(final DbClient dbClient,
            final OEParameterList oeParameterList) {
        return new ParameterList(oeParameterList.getName(),
                oeParameterList.getFriendlyName(), toParameterMap(dbClient,
                        oeParameterList.getParameters()),
                oeParameterList.getLocked(), oeParameterList.getRequired());
    }

    private static Parameter toParameter(final OEParameter oeParameter) {
        return new Parameter(oeParameter.getName(),
                oeParameter.getFriendlyName(), oeParameter.getValue(),
                Type.valueOf(oeParameter.getType()), oeParameter.getLocked(),
                oeParameter.getRequired());
    }

    /**
     * @param input
     * @param input2
     */
    public static boolean updateParameterStringSet(final DbClient dbClient,
            final NamedURI primitive,
            final Map<String, AbstractParameter<?>> parameterMap,
            StringSet stringSet) {

        final Set<String> found = new HashSet<String>();
        final Set<String> added = new HashSet<String>();
        final Set<String> removed = new HashSet<String>();
        if (stringSet == null) {
            stringSet = new StringSet();
        } else if (!stringSet.isEmpty()) {
            for (final URI uri : URIUtil.toURIList(stringSet)) {
                final Class<?> type = URIUtil.getModelClass(uri);

                if (type.isAssignableFrom(OEParameter.class)) {
                    final OEParameter oeParameter = dbClient.queryObject(
                            OEParameter.class, uri);
                    found.add(oeParameter.getName());
                    if (parameterMap.containsKey(oeParameter.getName())) {
                        if (!parameterMap.get(oeParameter.getName())
                                .isParameter())
                            throw new RuntimeException();
                        final Parameter parameter = parameterMap.get(
                                oeParameter.getName()).asParameter();
                        if (!equal(parameter, oeParameter)) {
                            if (primitive.equals(oeParameter.getPrimitive())) {
                                dbClient.updateObject(toOEParameter(
                                        oeParameter.getId(), primitive,
                                        parameter));
                            } else {
                                final URI id = URIUtil
                                        .createId(OEParameter.class);
                                dbClient.createObject(toOEParameter(
                                        URIUtil.createId(OEParameter.class),
                                        primitive, parameter));
                                added.add(id.toString());
                                removed.add(oeParameter.getId().toString());
                            }
                        }

                    } else {
                        removed.add(uri.toString());
                        if (primitive.equals(oeParameter.getPrimitive())) {
                            dbClient.markForDeletion(oeParameter);
                        }
                    }

                } else if (type.isAssignableFrom(OEParameterList.class)) {
                    final OEParameterList oeParameterList = dbClient
                            .queryObject(OEParameterList.class, uri);
                    found.add(oeParameterList.getName());
                    if (parameterMap.containsKey(oeParameterList.getName())) {
                        if (!parameterMap.get(oeParameterList.getName())
                                .isParameterList())
                            throw new RuntimeException();
                        final ParameterList parameterList = parameterMap.get(
                                oeParameterList.getName()).asParameterList();

                        if (!equal(parameterList, oeParameterList)
                                || updateParameterStringSet(dbClient,
                                        primitive, parameterList.value(),
                                        oeParameterList.getParameters())) {
                            if (primitive.equals(oeParameterList.getName())) {
                                dbClient.updateObject(toOEParameterList(
                                        oeParameterList.getId(), primitive,
                                        parameterList,
                                        oeParameterList.getParameters()));
                            } else {
                                final URI id = URIUtil
                                        .createId(OEParameterList.class);
                                dbClient.createObject(toOEParameterList(
                                        URIUtil.createId(OEParameterList.class),
                                        primitive, parameterList,
                                        oeParameterList.getParameters()));
                                added.add(id.toString());
                                removed.add(oeParameterList.getId().toString());
                            }
                        }

                    } else {
                        removed.add(uri.toString());
                        if (primitive.equals(oeParameterList.getPrimitive())) {
                            deleteList(dbClient, primitive, oeParameterList);
                        }
                    }
                }
            }
        }
        final Set<String> newParameters = parameterMap.keySet();
        newParameters.removeAll(found);
        for (final String parameterName : newParameters) {
            final URI id;
            if (parameterMap.get(parameterName).isParameter()) {
                id = URIUtil.createId(OEParameter.class);
                dbClient.createObject(toOEParameter(id, primitive, parameterMap
                        .get(parameterName).asParameter()));
            } else {
                final StringSet parameters = new StringSet();
                updateParameterStringSet(dbClient, primitive,
                        parameterMap.get(parameterName).asParameterList()
                                .value(), parameters);
                id = URIUtil.createId(OEParameterList.class);
                dbClient.createObject(toOEParameterList(id, primitive,
                        parameterMap.get(parameterName).asParameterList(),
                        parameters));
            }
            added.add(id.toString());
        }

        return stringSet.removeAll(removed) || stringSet.addAll(added);
    }

    /**
     * @param parameterList
     * @param oeParameterList
     * @return
     */
    private static boolean baseEqual(final AbstractParameter<?> parameter,
            final OEAbstractParameter oeParameter) {
        return parameter.name().equals(oeParameter.getName())
                && parameter.friendlyName().equals(
                        oeParameter.getFriendlyName())
                && parameter.locked() == oeParameter.getLocked()
                && parameter.required() == oeParameter.getRequired();
    }

    /**
     * @param parameterList
     * @param oeParameterList
     * @return
     */
    private static boolean equal(final ParameterList parameterList,
            final OEParameterList oeParameterList) {

        if (null == parameterList) {
            return null == oeParameterList;
        }

        if (null == oeParameterList) {
            return null == parameterList;
        }

        return baseEqual(parameterList, oeParameterList);
    }

    /**
     * @param parameter
     * @param oeParameter
     * @return
     */
    private static boolean equal(final Parameter parameter,
            final OEParameter oeParameter) {
        if (null == parameter) {
            return null == oeParameter;
        }

        if (null == oeParameter) {
            return null == parameter;
        }
        return baseEqual(parameter, oeParameter)
                && parameter.value().equals(oeParameter.getValue())
                && parameter.type().name().equals(oeParameter.getType());
    }

    /**
     * @param dbClient
     * @param primitive
     * @param list
     */
    private static void deleteList(final DbClient dbClient,
            final NamedURI primitive, final OEParameterList list) {
        for (final URI uri : URIUtil.toURIList(list.getParameters())) {
            final Class<?> type = URIUtil.getModelClass(uri);

            if (type.isAssignableFrom(OEParameter.class)) {
                final OEParameter oeParameter = dbClient.queryObject(
                        OEParameter.class, uri);
                if (primitive.equals(oeParameter.getPrimitive())) {
                    dbClient.markForDeletion(oeParameter);
                }

            } else {
                final OEParameterList oeParameterList = dbClient.queryObject(
                        OEParameterList.class, uri);
                if (primitive.equals(oeParameterList.getPrimitive())) {
                    deleteList(dbClient, primitive, oeParameterList);
                }
            }
        }
        dbClient.markForDeletion(list);
    }

    /**
     * @param id
     * @param primitive
     * @param parameterList
     * @return
     */
    private static OEParameterList toOEParameterList(final URI id,
            final NamedURI primitive, final ParameterList parameterList,
            final StringSet parameterStringSet) {
        final OEParameterList oeParameterList = new OEParameterList();
        oeParameterList.setId(id);
        oeParameterList.setPrimitive(primitive);
        oeParameterList.setName(parameterList.name());
        oeParameterList.setFriendlyName(parameterList.friendlyName());
        oeParameterList.setLocked(parameterList.locked());
        oeParameterList.setRequired(parameterList.required());
        oeParameterList.setParameters(parameterStringSet);
        return oeParameterList;
    }

    private static OEParameter toOEParameter(final URI id,
            final NamedURI primitive, final Parameter parameter) {
        final OEParameter oeParameter = new OEParameter();
        oeParameter.setId(id);
        oeParameter.setPrimitive(primitive);
        oeParameter.setName(parameter.name());
        oeParameter.setFriendlyName(parameter.friendlyName());
        oeParameter.setLocked(parameter.locked());
        oeParameter.setRequired(parameter.required());
        oeParameter.setValue(parameter.value());
        oeParameter.setType(parameter.type().name());
        return oeParameter;
    }
}
