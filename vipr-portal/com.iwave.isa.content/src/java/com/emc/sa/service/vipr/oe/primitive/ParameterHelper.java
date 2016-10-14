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
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.emc.sa.service.vipr.oe.primitive.Parameter.Type;
import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.URIUtil;
import com.emc.storageos.db.client.model.OEAbstractParameter;
import com.emc.storageos.db.client.model.OEParameter;
import com.emc.storageos.db.client.model.OEParameterList;
import com.emc.storageos.db.client.model.StringSet;

/**
 * Helper class to load/save parameters in persistence so that the UI/execution
 * engine doesn't need to understand primitive inheritance
 */
public final class ParameterHelper {

    private static final Change.NoChange NO_CHANGE = new Change.NoChange();

    private ParameterHelper() {}

    /**
     * Convert a StringSet of parameters to a parameter map.
     * 
     * @return A map of parameters keyed with the parameter name
     */
    public static Map<String, AbstractParameter<?>> toParameterMap(final DbClient dbClient, final StringSet input) {
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

    /**
     * Update a StringSet of persisted parameters with the contents of the
     * parameter map.
     * 
     * @param dbClient
     *            - interface to the database
     * @param primitive
     *            - The URI of the primitive that will own any new parameters
     *            that are created in the database
     * @param parameterMap
     *            - Map of parameters to use to update the StringSet
     * @param stringSet
     *            - Set of parameter URIs that will be updated
     * @return whether the StringSet was updated
     */
    public static boolean updateParameterStringSet(final DbClient dbClient, final URI primitive, final Map<String, AbstractParameter<?>> parameterMap, final StringSet stringSet) {

        final Set<String> found = new HashSet<String>();
        final Set<OEAbstractParameter> toAdd = new HashSet<OEAbstractParameter>();
        final Set<OEAbstractParameter> toRemove = new HashSet<OEAbstractParameter>();
        final Set<OEAbstractParameter> toUpdate = new HashSet<OEAbstractParameter>();

        for (final URI uri : safeUriList(stringSet)) {
            final OEAbstractParameter oeParameter = query(dbClient, uri);
            found.add(oeParameter.getName());
            if (parameterMap.containsKey(oeParameter.getName())) {
                final Change change = updateOrReplaceParameter(dbClient,
                        primitive, oeParameter,
                        parameterMap.get(oeParameter.getName()));
                if (change.isReplace()) {
                    toAdd.add(change.asReplace().add());
                    toRemove.add(change.asReplace().remove());
                } else if (change.isUpdate()) {
                    toUpdate.add(change.asUpdate().update());
                }
            } else {
                toRemove.add(oeParameter);
            }
        }

        final Set<String> newParameters = parameterMap.keySet();
        newParameters.removeAll(found);
        for (final String parameterName : newParameters) {
            final URI id;
            final OEAbstractParameter oeParameter;
            if (parameterMap.get(parameterName).isParameter()) {
                id = URIUtil.createId(OEParameter.class);
                oeParameter = toOEParameter(id, primitive,
                        parameterMap.get(parameterName).asParameter());
            } else {
                final StringSet parameters = new StringSet();
                updateParameterStringSet(dbClient, primitive,
                        parameterMap.get(parameterName).asParameterList()
                                .value(), parameters);
                id = URIUtil.createId(OEParameterList.class);
                oeParameter = toOEParameterList(id, primitive, parameterMap
                        .get(parameterName).asParameterList(), parameters);
            }
            toAdd.add(oeParameter);
        }

        for (final OEAbstractParameter oeParameter : toUpdate) {
            dbClient.updateObject(oeParameter);
        }

        for (final OEAbstractParameter oeParameter : toRemove) {
            if (primitive.equals(oeParameter.getPrimitive())) {
                dbClient.markForDeletion(oeParameter);
                if (oeParameter.isParameterList()) {
                    deleteList(dbClient, primitive,
                            oeParameter.asParameterList());
                }
            }
            stringSet.remove(oeParameter.getId().toString());
        }

        for (final OEAbstractParameter oeParameter : toAdd) {
            dbClient.createObject(oeParameter);
            stringSet.add(oeParameter.getId().toString());
        }

        return !toRemove.isEmpty() || !toAdd.isEmpty();
    }

    /**
     * @param oeParameter
     * @param parameter
     * @return
     */
    private static Change updateOrReplaceParameter(final DbClient dbClient, final URI primitive, final OEAbstractParameter oeParameter, final AbstractParameter<?> parameter) {
        if (!typesMatch(oeParameter, parameter)) {
            throw new IllegalStateException("Parameter type cannot be changed");
        }

        final boolean doUpdate = ((parameter.isParameterList() && updateParameterStringSet(
                dbClient, primitive, parameter.asParameterList().value(),
                oeParameter.asParameterList().getParameters())) || !equal(
                oeParameter, parameter));

        if (!doUpdate) {
            return NO_CHANGE;
        } else if (primitive.equals(oeParameter.getPrimitive())) {
            return new Change.Update(updateParameter(primitive, oeParameter,
                    parameter));
        } else {
            return new Change.Replace(replaceParameter(dbClient, primitive,
                    oeParameter, parameter), oeParameter);
        }
    }

    /**
     * @param primitive
     * @param oeParameter
     * @param parameter
     * @return
     */
    private static OEAbstractParameter replaceParameter(final DbClient dbClient, final URI primitive, final OEAbstractParameter oeParameter, final AbstractParameter<?> parameter) {
        if (parameter.isParameter()) {
            return toOEParameter(URIUtil.createId(OEParameter.class),
                    primitive, parameter.asParameter());
        } else {
            return toOEParameterList(URIUtil.createId(OEParameterList.class),
                    primitive, parameter.asParameterList(), oeParameter
                            .asParameterList().getParameters());
        }
    }

    /**
     * @param primitive
     * @param oeParameter
     * @param parameter
     * @return
     */
    private static OEAbstractParameter updateParameter(final URI primitive, final OEAbstractParameter oeParameter, final AbstractParameter<?> parameter) {
        if (parameter.isParameter()) {
            return toOEParameter(oeParameter.getId(), primitive,
                    parameter.asParameter());
        } else {
            return toOEParameterList(oeParameter.getId(), primitive,
                    parameter.asParameterList(), oeParameter.asParameterList()
                            .getParameters());
        }
    }

    /**
     * Convert from the parameter list persistence object into the ParameterList
     * 
     * @return A ParamaterList that was created from the database object
     */
    private static ParameterList toParameterList(final DbClient dbClient, final OEParameterList oeParameterList) {
        return new ParameterList(oeParameterList.getName(),
                oeParameterList.getFriendlyName(), toParameterMap(dbClient,
                        oeParameterList.getParameters()),
                oeParameterList.getLocked(), oeParameterList.getRequired());
    }

    /**
     * Convert from a parameter database object into a Parameter
     * 
     * @return The Parameter representation of the database object
     */
    private static Parameter toParameter(final OEParameter oeParameter) {
        return new Parameter(oeParameter.getName(),
                oeParameter.getFriendlyName(), oeParameter.getValue(),
                Type.valueOf(oeParameter.getType()), oeParameter.getLocked(),
                oeParameter.getRequired());
    }

    /**
     * The URIUtil.toURIList() method returns null if the list that is passed
     * into it is empty. This method will return an empty list in that case
     */
    private static List<URI> safeUriList(final StringSet uris) {
        return uris.isEmpty() ? Collections.emptyList() : URIUtil
                .toURIList(uris);

    }

    /**
     * Query the parameter of the correct type from the database
     */
    private static OEAbstractParameter query(final DbClient dbClient, final URI uri) {
        final Class<?> type = URIUtil.getModelClass(uri);

        if (type.isAssignableFrom(OEParameter.class)) {
            return dbClient.queryObject(OEParameter.class, uri);
        } else if (type.isAssignableFrom(OEParameterList.class)) {
            return dbClient.queryObject(OEParameterList.class, uri);
        } else {
            throw new RuntimeException("Unknown parameter type: "
                    + type.getSimpleName());
        }
    }

    /**
     * Check if the types of the parameters match
     */
    private static boolean typesMatch(final OEAbstractParameter oeParameter, final AbstractParameter<?> parameter) {
        return (oeParameter.isParameter() && parameter.isParameter())
                || (oeParameter.isParameterList() && parameter
                        .isParameterList());
    }

    /**
     * Check if a an abstract paremeter is equal to the peristed parameter
     */
    private static boolean equal(final OEAbstractParameter oeParameter, final AbstractParameter<?> parameter) {
        return oeParameter.isParameter() ? parameterEqual(
                oeParameter.asParameter(), parameter.asParameter())
                : parameterListEqual(oeParameter.asParameterList(),
                        parameter.asParameterList());
    }

    /**
     * Check if the base metadata of the parameter object is equal to the
     * persisted metadata
     */
    private static boolean baseEqual(final AbstractParameter<?> parameter, final OEAbstractParameter oeParameter) {
        return parameter.name().equals(oeParameter.getName())
                && parameter.friendlyName().equals(
                        oeParameter.getFriendlyName())
                && parameter.locked() == oeParameter.getLocked()
                && parameter.required() == oeParameter.getRequired();
    }

    /**
     * Check if this parameter list object is equal to the persisted parameter
     * list
     */
    private static boolean parameterListEqual(final OEParameterList oeParameterList, final ParameterList parameterList) {

        if (null == parameterList) {
            return null == oeParameterList;
        }

        if (null == oeParameterList) {
            return null == parameterList;
        }

        return baseEqual(parameterList, oeParameterList);
    }

    /**
     * Check if the parameter is equal to the persisted parameter
     */
    private static boolean parameterEqual(final OEParameter oeParameter, final Parameter parameter) {
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
     * Delete parameters in a list if they are owned by the given primitive
     */
    private static void deleteList(final DbClient dbClient, final URI primitive, final OEParameterList list) {
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
     * Convert From a ParameterList to a parameter list data object
     */
    private static OEParameterList toOEParameterList(final URI id, final URI primitive, final ParameterList parameterList, final StringSet parameterStringSet) {
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

    /**
     * Convert from a Parameter to a parameter database object
     */
    private static OEAbstractParameter toOEParameter(final URI id, final URI primitive, final Parameter parameter) {
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

    /**
     * Container class to track the type of parameter change that is necessary
     */
    private static abstract class Change {

        public abstract boolean isNoChange();

        public abstract NoChange asNoChange();

        public abstract boolean isUpdate();

        public abstract Update asUpdate();

        public abstract boolean isReplace();

        public abstract Replace asReplace();

        private static class NoChange extends Change {

            @Override public boolean isNoChange() { return true; }

            @Override public NoChange asNoChange() { return this; }

            @Override public boolean isUpdate() { return false; }

            @Override public Update asUpdate() { return null; }

            @Override public boolean isReplace() { return false; }

            @Override public Replace asReplace() { return null; }
        }

        private static class Update extends Change {
            private final OEAbstractParameter _update;

            public Update(final OEAbstractParameter update) {
                _update = update;
            }

            public OEAbstractParameter update() {
                return _update;
            }

            @Override public boolean isNoChange() { return false; }

            @Override public NoChange asNoChange() { return null; }

            @Override public boolean isUpdate() { return true; }

            @Override public Update asUpdate() { return this; }

            @Override public boolean isReplace() { return false; }

            @Override public Replace asReplace() { return null; }
        }

        private static class Replace extends Change {
            private final OEAbstractParameter _add;
            private final OEAbstractParameter _remove;

            public Replace(final OEAbstractParameter add,
                    final OEAbstractParameter remove) {
                _add = add;
                _remove = remove;
            }

            public OEAbstractParameter add() {
                return _add;
            }

            public OEAbstractParameter remove() {
                return _remove;
            }

            @Override public boolean isNoChange() { return false; }

            @Override public NoChange asNoChange() { return null; }

            @Override public boolean isUpdate() { return false; }

            @Override public Update asUpdate() { return null; }

            @Override public boolean isReplace() { return true; }

            @Override public Replace asReplace() { return this; }
        }
    }
}
