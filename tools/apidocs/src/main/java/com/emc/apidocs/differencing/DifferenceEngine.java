/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.apidocs.differencing;

import com.emc.apidocs.model.*;
import com.google.common.collect.Lists;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class DifferenceEngine {
    private static List<ApiService> oldApi;
    private static List<ApiService> newApi;

    public static synchronized ApiDifferences calculateDifferences(List<ApiService> oldServices, List<ApiService> newServices) {
        oldApi = oldServices;
        newApi = newServices;

        ApiDifferences apiDifferences = new ApiDifferences();
        apiDifferences.newServices = findNewServices();
        apiDifferences.removedServices = findRemovedServices();
        apiDifferences.modifiedServices = findModifiedServices();
        return apiDifferences;
    }

    private static List<ApiService> findNewServices() {
        List<ApiService> newServices = Lists.newArrayList();

        for (ApiService apiService : newApi) {
            if (!containsService(apiService.getFqJavaClassName(), oldApi)) {
                newServices.add(apiService);
            }
        }

        return newServices;
    }

    private static List<ApiService> findRemovedServices() {
        List<ApiService> removedServices = Lists.newArrayList();

        for (ApiService apiService : oldApi) {
            if (!containsService(apiService.getFqJavaClassName(), newApi)) {
                removedServices.add(apiService);
            }
        }

        return removedServices;
    }

    private static List<ApiServiceChanges> findModifiedServices() {
        List<ApiServiceChanges> changedServices = Lists.newArrayList();

        for (ApiService newService : newApi) {
            ApiService oldService = findService(newService.getFqJavaClassName(), oldApi);

            if (oldService != null) {
                ApiServiceChanges serviceChanges = new ApiServiceChanges();
                serviceChanges.service = newService;
                serviceChanges.newMethods = findNewMethods(newService, oldService);
                serviceChanges.removedMethods = findRemovedMethods(oldService, newService);
                serviceChanges.modifiedMethods = findModifiedMethods(oldService, newService);

                if (serviceChanges.containsChanges()) {
                    changedServices.add(serviceChanges);
                }
            }
        }

        // Sort Alphabetically
        Collections.sort(changedServices, new Comparator<ApiServiceChanges>() {

            @Override
            public int compare(ApiServiceChanges o1, ApiServiceChanges o2) {
                return o1.service.getTitle().compareTo(o2.service.getTitle());
            }
        });

        return changedServices;
    }

    private static List<ApiMethod> findDeprecatedMethods(ApiService newApiService) {
        List<ApiMethod> deprecatedMethods = Lists.newArrayList();
        for (ApiMethod apiMethod : newApiService.methods) {
            if (apiMethod.isDeprecated) {
                deprecatedMethods.add(apiMethod);
            }
        }

        return deprecatedMethods;
    }

    private static List<ApiMethod> findNewMethods(ApiService newApiService, ApiService oldApiService) {
        List<ApiMethod> newMethods = Lists.newArrayList();

        for (ApiMethod newApiMethod : newApiService.methods) {
            if (!containsMethod(newApiMethod.javaMethodName, oldApiService.methods)) {
                newMethods.add(newApiMethod);
            }
        }

        return newMethods;
    }

    private static List<ApiMethod> findRemovedMethods(ApiService oldApiService, ApiService newApiService) {
        List<ApiMethod> removedMethods = Lists.newArrayList();

        for (ApiMethod oldApiMethod : oldApiService.methods) {
            if (!containsMethod(oldApiMethod.javaMethodName, newApiService.methods)) {
                removedMethods.add(oldApiMethod);
            }
        }

        return removedMethods;
    }

    private static List<ApiMethodChanges> findModifiedMethods(ApiService oldService, ApiService newService) {
        List<ApiMethodChanges> methodChanges = Lists.newArrayList();

        for (ApiMethod newMethod : newService.methods) {
            ApiMethod oldMethod = findMethod(newMethod.javaMethodName, oldService.methods);
            if (oldMethod != null) {
                ApiMethodChanges apiMethodChanges = new ApiMethodChanges();
                apiMethodChanges.method = newMethod;

                apiMethodChanges.newRoles = findAddedValues(oldMethod.roles, newMethod.roles);
                apiMethodChanges.removedRoles = findAddedValues(oldMethod.roles, newMethod.roles);

                apiMethodChanges.headerParameters = generateMergedFields(oldMethod.headerParameters, newMethod.headerParameters);
                apiMethodChanges.requestHeadersChanged = containsChanges(apiMethodChanges.headerParameters);

                apiMethodChanges.pathParameters = generateMergedFields(oldMethod.pathParameters, newMethod.pathParameters);
                apiMethodChanges.pathParametersChanged = containsChanges(apiMethodChanges.pathParameters);

                apiMethodChanges.queryParameters = generateMergedFields(oldMethod.queryParameters, newMethod.queryParameters);
                apiMethodChanges.queryParametersChanged = containsChanges(apiMethodChanges.queryParameters);

                apiMethodChanges.responseHeaders = generateMergedFields(oldMethod.responseHeaders, newMethod.responseHeaders);
                apiMethodChanges.responseHeadersChanged = containsChanges(apiMethodChanges.responseHeaders);

                // Get Request Payload Changes
                if (oldMethod.input != null && newMethod.input != null) {
                    apiMethodChanges.input = generateMergedClass(oldMethod.input, newMethod.input);
                    apiMethodChanges.requestPayloadChanged = containsChanges(apiMethodChanges.input);
                }

                // Get Response Payload Changes
                if (oldMethod.output != null && newMethod.output != null) {
                    apiMethodChanges.output = generateMergedClass(oldMethod.output, newMethod.output);
                    apiMethodChanges.responsePayloadChanged = containsChanges(apiMethodChanges.output);
                }

                if (apiMethodChanges.containsChanges()) {
                    methodChanges.add(apiMethodChanges);
                }
            }
        }

        return methodChanges;
    }

    // Creates a new Class which is a mix of the old and new class, fields are marked with change markers
    public static ApiClass compareClasses(ApiClass oldClass, ApiClass newClass) {
        List<ApiField> oldFields = oldClass.fields;
        List<ApiField> newFields = newClass.fields;

        ApiClass diffClass = new ApiClass();

        int oldClassPos = 0;
        for (int newClassPos = 0; newClassPos < newClass.fields.size(); newClassPos++) {
            ApiField newClassField = newFields.get(newClassPos);
            ApiField oldClassField = oldFields.get(oldClassPos);

            ChangeState fieldChangeState = ChangeState.NOT_CHANGED;
            if (oldClassField.name.equals(newClassField.name)) {
                fieldChangeState = ChangeState.NOT_CHANGED;
                oldClassPos++;
            }
            else {
                // Search forward in the new Class to see if this field appears (which means fields were deleted)
                int foundPosition = -1;
                for (int t = oldClassPos; t < oldFields.size(); t++) {
                    if (oldFields.get(t).name.equals(newClassField.name)) {
                        foundPosition = t;
                        break;
                    }
                }

                if (foundPosition == -1) {
                    fieldChangeState = ChangeState.REMOVED;
                    // Loop forward on old class until back in sync?

                }
                else {
                    fieldChangeState = ChangeState.ADDED;
                }
            }

            ApiField fieldCopy = copyField(newClassField);
            fieldCopy.changeState = fieldChangeState;

            if (!fieldCopy.isPrimitive()) {
                fieldCopy.type = compareClasses(oldClassField.type, newClassField.type);
            }

            diffClass.addField(fieldCopy);
        }

        // run through any fields still left on Old
        for (int f = newFields.size(); f < oldFields.size(); f++) {
            ApiField fieldCopy = copyField(oldFields.get(f));
            fieldCopy.changeState = ChangeState.REMOVED;
            diffClass.addField(fieldCopy);
        }

        return diffClass;
    }

    /**
     * Create a copy of a field, expect for the type
     */
    private static ApiField copyField(ApiField apiField) {
        ApiField fieldCopy = new ApiField();
        fieldCopy.name = apiField.name;
        fieldCopy.required = apiField.required;
        fieldCopy.primitiveType = apiField.primitiveType;
        fieldCopy.wrapperName = apiField.wrapperName;
        fieldCopy.description = apiField.description;
        fieldCopy.validValues = Lists.newArrayList(apiField.validValues);
        fieldCopy.collection = apiField.collection;
        fieldCopy.min = apiField.min;
        fieldCopy.max = apiField.max;

        return fieldCopy;
    }

    private static List<ApiField> findAddedFields(List<ApiField> oldList, List<ApiField> newList) {
        List<ApiField> addedField = Lists.newArrayList();
        for (ApiField newField : newList) {
            if (!containsField(newField.name, oldList)) {
                addedField.add(newField);
            }
        }

        return addedField;

    }

    private static List<ApiField> findRemovedFields(List<ApiField> oldList, List<ApiField> newList) {
        List<ApiField> removedFields = Lists.newArrayList();
        for (ApiField oldField : newList) {
            if (!containsField(oldField.name, newList)) {
                removedFields.add(oldField);
            }
        }

        return removedFields;
    }

    private static List<String> findRemovedValues(List<String> oldList, List<String> newList) {
        List<String> removedValues = Lists.newArrayList();
        for (String oldValue : oldList) {
            if (!newList.contains(oldValue)) {
                removedValues.add(oldValue);
            }
        }

        return removedValues;
    }

    private static List<String> findAddedValues(List<String> oldList, List<String> newList) {
        List<String> addedValues = Lists.newArrayList();
        for (String newValue : newList) {
            if (!oldList.contains(newValue)) {
                addedValues.add(newValue);
            }
        }

        return addedValues;
    }

    private static void findClassChanges(ApiClass oldClass, ApiClass newClass) {
        System.out.println("COMPARING CLASS " + oldClass.name);
        // Check for removed fields
        for (ApiField oldField : oldClass.fields) {
            if (!containsField(oldField.name, newClass.fields)) {
                System.out.println("Field REMOVED : " + oldField.name);
            }
        }

        // Check for removed fields
        for (ApiField newField : newClass.fields) {
            if (!containsField(newField.name, oldClass.fields)) {
                System.out.println("Field ADDED : " + newField.name);
            }
        }

        for (ApiField newField : newClass.fields) {
            ApiField oldField = findField(newField.name, oldClass.fields);

            if (oldField != null) {
                findClassChanges(oldField.type, newField.type);
            }
        }
    }

    public static boolean containsService(String className, List<ApiService> services) {
        return findService(className, services) != null;
    }

    private static ApiService findService(String className, List<ApiService> services) {
        for (ApiService service : services) {
            if (service.getFqJavaClassName().equals(className)) {
                return service;
            }
        }

        return null;
    }

    public static boolean containsMethod(String javaMethodName, List<ApiMethod> methods) {
        return findMethod(javaMethodName, methods) != null;
    }

    private static ApiMethod findMethod(String javaMethodName, List<ApiMethod> methods) {
        for (ApiMethod method : methods) {
            if (method.javaMethodName.equals(javaMethodName)) {
                return method;
            }
        }

        return null;
    }

    public static boolean containsField(String fieldName, List<ApiField> fields) {
        return findField(fieldName, fields) != null;
    }

    public static ApiField findField(String fieldName, List<ApiField> fields) {
        for (ApiField field : fields) {
            if (field.name.equals(fieldName)) {
                return field;
            }
        }

        return null;
    }

    /**
     * For more information on the LCS algorithm, see http://en.wikipedia.org/wiki/Longest_common_subsequence_problem
     */
    private static int[][] computeLcs(List<ApiField> sequenceA, List<ApiField> sequenceB) {
        int[][] lcs = new int[sequenceA.size() + 1][sequenceB.size() + 1];

        for (int i = 0; i < sequenceA.size(); i++) {

            for (int j = 0; j < sequenceB.size(); j++) {

                if (sequenceA.get(i).compareTo(sequenceB.get(j)) == 0) {
                    lcs[i + 1][j + 1] = lcs[i][j] + 1;
                } else {
                    lcs[i + 1][j + 1] = Math.max(lcs[i][j + 1], lcs[i + 1][j]);
                }
            }
        }

        return lcs;
    }

    public static ApiClass generateMergedClass(ApiClass oldClass, ApiClass newClass) {
        ApiClass mergedClass = new ApiClass();

        if (oldClass == null) {
            throw new RuntimeException("Old Class NULL " + newClass.name);
        }

        if (newClass == null) {
            throw new RuntimeException("New Class NULL");
        }

        mergedClass.name = newClass.name;
        mergedClass.fields = generateMergedFields(oldClass.fields, newClass.fields);

        return mergedClass;
    }

    /**
     * Generates a merged list with changes
     */
    public static List<ApiField> generateMergedFields(List<ApiField> oldFields, List<ApiField> newFields)
    {
        int[][] lcs = computeLcs(oldFields, newFields);

        List<ApiField> mergedFields = Lists.newArrayList();

        int aPos = oldFields.size();
        int bPos = newFields.size();

        while (aPos > 0 || bPos > 0) {

            if (aPos > 0 && bPos > 0 && oldFields.get(aPos - 1).compareTo(newFields.get(bPos - 1)) == 0) {
                ApiField field = oldFields.get(aPos - 1);
                field.changeState = ChangeState.NOT_CHANGED;
                mergedFields.add(field);

                aPos--;
                bPos--;
            } else if (bPos > 0 && (aPos == 0 || lcs[aPos][bPos - 1] >= lcs[aPos - 1][bPos])) {
                ApiField field = newFields.get(bPos - 1);
                field.changeState = ChangeState.ADDED;
                mergedFields.add(field);
                bPos--;
            } else {
                ApiField field = oldFields.get(aPos - 1);
                field.changeState = ChangeState.REMOVED;
                mergedFields.add(field);
                aPos--;
            }
        }

        // Backtracking generates the list from back to front,
        // so reverse it to get front-to-back.
        Collections.reverse(mergedFields);

        return mergedFields;
    }

    public static boolean containsChanges(List<ApiField> fields) {
        for (ApiField field : fields) {
            if (field.changeState != ChangeState.NOT_CHANGED) {
                return true;
            }
        }

        return false;
    }

    /**
     * @return Indicates if this class contains ANY changes (directly or within a fields type)
     */
    public static boolean containsChanges(ApiClass apiClass) {
        if (apiClass == null) {
            return false;
        }

        for (ApiField field : apiClass.fields) {
            if (field.changeState != ChangeState.NOT_CHANGED) {
                return true;
            }
        }

        for (ApiField field : apiClass.fields) {
            if (!field.isPrimitive()) {
                boolean containsChanges = containsChanges(field.type);

                if (containsChanges) {
                    return true;
                }
            }
        }

        return false;
    }
}
