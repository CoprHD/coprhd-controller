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
package com.emc.vipr.primitives;

import java.net.URI;
import java.util.List;
import java.util.Map;

import javax.lang.model.element.Modifier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.apidocs.model.ApiClass;
import com.emc.apidocs.model.ApiField;
import com.emc.apidocs.model.ApiMethod;
import com.emc.apidocs.model.ApiService;
import com.emc.storageos.primitives.CustomServicesPrimitive.InputType;
import com.emc.storageos.primitives.input.BasicInputParameter;
import com.emc.storageos.primitives.input.InputParameter;
import com.emc.storageos.primitives.java.vipr.CustomServicesViPRPrimitive;
import com.emc.storageos.primitives.output.BasicOutputParameter;
import com.emc.storageos.primitives.output.OutputParameter;
import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableList.Builder;
import com.google.common.collect.ImmutableMap;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.CodeBlock;
import com.squareup.javapoet.FieldSpec;
import com.squareup.javapoet.JavaFile;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.ParameterizedTypeName;
import com.squareup.javapoet.TypeName;
import com.squareup.javapoet.TypeSpec;
import com.sun.tools.javac.util.StringUtils;

/**
 * Static class that creates primitive java source files
 *
 */
public final class ApiPrimitiveMaker {

    private static final String PACKAGE = "com.emc.storageos.primitives";
    private static final String TASK_SUCCESS = "#task.state = ready";
    private static final String HTTP_SUCCESS = "code > 199 and code < 300";
    
    private static final MethodSpec FRIENDLY_NAME_METHOD = MethodSpec
            .methodBuilder("friendlyName").addAnnotation(Override.class)
            .addModifiers(Modifier.PUBLIC).addStatement("return FRIENDLY_NAME")
            .returns(String.class).build();
    
    private static final MethodSpec DESCRIPTION_METHOD = MethodSpec
            .methodBuilder("description").addAnnotation(Override.class)
            .addModifiers(Modifier.PUBLIC).addStatement("return DESCRIPTION")
            .returns(String.class).build();
    
    private static final MethodSpec SUCCESS_CRITERIA_METHOD = MethodSpec
            .methodBuilder("successCriteria").addAnnotation(Override.class)
            .addModifiers(Modifier.PUBLIC).addStatement("return SUCCESS_CRITERIA")
            .returns(String.class).build();
    
    private static final MethodSpec INPUT_METHOD = MethodSpec
            .methodBuilder("input").addAnnotation(Override.class)
            .addModifiers(Modifier.PUBLIC).addStatement("return INPUT")
            .returns(ParameterizedTypeName.get(ClassName.get(Map.class), TypeName.get(InputType.class), 
                    ParameterizedTypeName.get(List.class, InputParameter.class))).build();
    
    private static final MethodSpec OUTPUT_METHOD = MethodSpec
            .methodBuilder("output").addAnnotation(Override.class)
            .addModifiers(Modifier.PUBLIC).addStatement("return OUTPUT")
            .returns(ParameterizedTypeName.get(List.class, OutputParameter.class)).build();
    
    private static final MethodSpec ATTRIBUTES_METHOD = MethodSpec
            .methodBuilder("attributes").addAnnotation(Override.class)
            .addModifiers(Modifier.PUBLIC).addStatement("return ATTRIBUTES")
            .returns(ParameterizedTypeName.get(Map.class, String.class, String.class)).build();
    
    private static final MethodSpec PATH_METHOD = MethodSpec
            .methodBuilder("path").addAnnotation(Override.class)
            .addModifiers(Modifier.PUBLIC).addStatement("return PATH")
            .returns(String.class).build();

    private static final MethodSpec METHOD_METHOD = MethodSpec
            .methodBuilder("method").addAnnotation(Override.class)
            .addModifiers(Modifier.PUBLIC).addStatement("return METHOD")
            .returns(String.class).build();

    private static final MethodSpec BODY_METHOD = MethodSpec
            .methodBuilder("body").addAnnotation(Override.class)
            .addModifiers(Modifier.PUBLIC).addStatement("return BODY")
            .returns(String.class).build();

    private static final ImmutableList<MethodSpec> METHODS = ImmutableList
            .<MethodSpec> builder()
            .add(FRIENDLY_NAME_METHOD, DESCRIPTION_METHOD, SUCCESS_CRITERIA_METHOD,
                    INPUT_METHOD, OUTPUT_METHOD, ATTRIBUTES_METHOD,
                    PATH_METHOD, METHOD_METHOD, BODY_METHOD).build();

    private static ImmutableList<String> METHOD_BLACK_LIST = ImmutableList
            .<String> builder().add("assignTags").add("getTags").add("search")
            .add("getBulkIds").add("getBulkResources").add("getTasks").build();

    final private static Logger _log = LoggerFactory
            .getLogger(ApiPrimitiveMaker.class);

    private ApiPrimitiveMaker() {
    }

    /**
     * Accept a list of ApiServices and make primitives for each HTTP method in
     * the service
     * 
     * @param services
     *            - a list of services
     * 
     * @return a JavaFile representing java source code for the primitive
     */
    public static Iterable<JavaFile> makePrimitives(
            final List<ApiService> services) {

        final Builder<JavaFile> builder = ImmutableList.<JavaFile> builder();

        for (ApiService service : services) {
            for (ApiMethod method : service.methods) {
                if (blackListed(method)) {
                    _log.info("Method "
                            + method.apiService.getFqJavaClassName() + "::"
                            + method.javaMethodName + " is black listed");
                } else if (method.isDeprecated) {
                    _log.info("Method "
                            + method.apiService.getFqJavaClassName() + "::"
                            + method.javaMethodName + " is deprecated");
                } else {
                    builder.add(makePrimitive(method));
                }
            }
        }

        return builder.build();
    }

    private static boolean blackListed(final ApiMethod method) {
        return (METHOD_BLACK_LIST.contains(method.javaMethodName) || METHOD_BLACK_LIST
                .contains(method.apiService.getFqJavaClassName() + "::"
                        + method.javaMethodName));
    }

    /**
     * Make the primitive class for this method
     */
    private static JavaFile makePrimitive(final ApiMethod method) {
        final String name = makePrimitiveName(method);
        final String friendlyName = makeFriendlyName(name, method);

        TypeSpec primitive = TypeSpec.classBuilder(name)
                .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
                .superclass(CustomServicesViPRPrimitive.class).addMethods(METHODS)
                .addMethod(makeConstructor(name))
                .addFields(makeFields(method, friendlyName)).build();

        return JavaFile.builder(PACKAGE, primitive).build();
    }

    /**
     * Make static fields for the primitive metadata/input/output
     * 
     * @param method
     *            The REST api method that is used to generate the primitive
     * 
     * @return List of static fields in the primitive
     * 
     */
    private static Iterable<FieldSpec> makeFields(final ApiMethod method,
            final String friendlyName) {

        return ImmutableList
                .<FieldSpec> builder()
                .add(makeStringConstant("FRIENDLY_NAME", friendlyName))
                .add(makeStringConstant("DESCRIPTION", method.description))
                .add(makeStringConstant("SUCCESS_CRITERIA",
                        makeSuccessCriteria(method)))
                .add(makeStringConstant("PATH", method.path))
                .add(makeStringConstant("METHOD", method.httpMethod))
                .add(makeStringConstant("BODY", makeBody(method.input)))
                .addAll(makeInput(method)).addAll(makeOutput(method))
                .add(makeAttributes()).build();
    }

    /**
     * Make the JSON template for the REST request body
     * 
     * @param input
     *            The api class that is the request entity
     * 
     * @return JSON template for the request entity
     */
    private static String makeBody(final ApiClass input) {
        // If there is no request body just return an empty string;
        if (null == input) {
            return "";
        }

        // If the input was not empty but there are no fields
        // throw an exception
        if (null == input.fields) {
            throw new RuntimeException("input with no fields!!");
        }

        final StringBuilder body = new StringBuilder();
        String separator = "{\n";
        if (null != input.fields) {

            for (ApiField field : input.fields) {
                final String prefix;
                final String suffix;
                final String name;

                if (field.collection) {
                    prefix = "[";
                    suffix = "]";
                } else {
                    prefix = "";
                    suffix = "";
                }

                if (null != field.wrapperName && !field.wrapperName.isEmpty()) {
                    name = field.wrapperName;
                } else {
                    name = field.name;
                }

                body.append(separator + "\"" + name + "\": " + prefix);
                if (field.isPrimitive()) {
                    body.append("$" + field.name);
                } else {
                    body.append(makeBody(field.type));
                }
                body.append(suffix);
                separator = ",\n";
            }
        }

        if (body.length() > 0) {
            body.append("\n}");
        }

        // We don't use attributes in the ViPR API
        if (null != input.attributes && input.attributes.size() > 0) {
            throw new RuntimeException("Attributes not supported");
        }

        return body.toString();
    }

    /**
     * The criteria that is used to determine success of the REST API method
     * 
     * @param method
     * @return
     */
    private static String makeSuccessCriteria(final ApiMethod method) {
        return method.isTaskResponse ? TASK_SUCCESS : HTTP_SUCCESS;
    }

    private static MethodSpec makeConstructor(final String name) {
        final String id = URI.create(
                String.format("urn:storageos:%1$s:%2$s:",
                        CustomServicesViPRPrimitive.class.getSimpleName(), name)).toString();
        return MethodSpec
                .constructorBuilder()
                .addModifiers(Modifier.PUBLIC)
                .addStatement(
                        "super($T.create($S), $L.class.getName())",
                        URI.class, id, name).build();
    }

    private static FieldSpec makeStringConstant(final String name,
            final String value) {
        return FieldSpec
                .builder(String.class, name)
                .addModifiers(Modifier.PRIVATE, Modifier.FINAL, Modifier.STATIC)
                .initializer("$S", value).build();
    }

    /**
     * Make the Input parameter fields and the input parameter array for this
     * primitive
     * 
     * @param method
     *            ApiMethod that is used to generate the primitive
     * 
     * @return the List of input fields
     */
    private static Iterable<FieldSpec> makeInput(final ApiMethod method) {
        final ImmutableList.Builder<FieldSpec> builder = ImmutableList
                .<FieldSpec> builder();
        final ImmutableList.Builder<String> parameters = new ImmutableList.Builder<String>();
        final ParameterFieldName.Input name = new ParameterFieldName.Input();

        for (ApiField pathParameter : method.pathParameters) {
            FieldSpec param = makeInputParameter(name, pathParameter, true);
            parameters.add(param.name);
            builder.add(param);
        }

        for (ApiField queryParameter : method.queryParameters) {
            FieldSpec param = makeInputParameter(name, queryParameter,
                    queryParameter.required);
            parameters.add(param.name);
            builder.add(param);
        }

        if (null != method.input && null != method.input.fields) {
            for (final ApiField field : method.input.fields) {
                final ImmutableList<FieldSpec> requestParameters = makeRequestParameters(
                        name, "", field);
                for (final FieldSpec requestParameter : requestParameters) {
                    parameters.add(requestParameter.name);
                }
                builder.addAll(requestParameters);
            }
        }

        builder.add(FieldSpec.builder(
                ParameterizedTypeName.get(ImmutableList.class, InputParameter.class), "INPUT_LIST")
                .addModifiers(Modifier.PRIVATE, Modifier.FINAL,Modifier.STATIC)
                .initializer("new $T().add($L).build()", 
                        ParameterizedTypeName.get(ImmutableList.Builder.class,InputParameter.class),
                        Joiner.on(",").join(parameters.build())).build());
        
        return builder.add(
                FieldSpec
                        .builder(ParameterizedTypeName.get(ClassName.get(Map.class), ClassName.get(InputType.class), 
                                ParameterizedTypeName.get(List.class, InputParameter.class)), "INPUT")
                        .addModifiers(Modifier.PRIVATE, Modifier.FINAL,Modifier.STATIC)
                        .initializer("$T.of($T.$L, $L)", ImmutableMap.class, InputType.class, InputType.INPUT_PARAMS.name(), "INPUT_LIST")
                        .build()).build();
    }

    /**
     * Make a list of output fields for this primitive
     * 
     * @param method
     *            the ApiMethod that is used to generate the primitive
     * 
     * @return the list of output fields
     */
    private static Iterable<FieldSpec> makeOutput(final ApiMethod method) {

        final ImmutableList.Builder<FieldSpec> builder = ImmutableList
                .<FieldSpec> builder();
        final ImmutableList.Builder<String> parameters = new ImmutableList.Builder<String>();

        final ParameterFieldName.Output fieldName = new ParameterFieldName.Output();

        if (null != method.output && null != method.output.fields) {
            for (final ApiField field : method.output.fields) {
                final ImmutableList<FieldSpec> responseParameters = makeResponseParameters(
                        fieldName, method.output.name, field);
                for (final FieldSpec responseParameter : responseParameters) {
                    parameters.add(responseParameter.name);
                }
                builder.addAll(responseParameters);
            }
        }

        return builder.add(
                FieldSpec
                        .builder(ParameterizedTypeName.get(List.class, OutputParameter.class), "OUTPUT")
                        .addModifiers(Modifier.PRIVATE, Modifier.FINAL, Modifier.STATIC)
                        .initializer("new $T().add($L).build()", 
                                ParameterizedTypeName.get(ImmutableList.Builder.class,OutputParameter.class),
                                Joiner.on(",").join(parameters.build()))
                                .build()).build();

    }

    private static FieldSpec makeAttributes() {
        return FieldSpec.builder(
                ParameterizedTypeName.get(Map.class, String.class, String.class), "ATTRIBUTES")
                .initializer("$T.of()", ImmutableMap.class).build();
    }
    /**
     * Make a list of request parameters in this request
     * 
     * @param fieldName
     *            field name generator
     * @param prefix
     *            prefix for the parameter name
     * @param field
     *            The ApiField in the request
     * @return The list of fields in this request
     */
    private static ImmutableList<FieldSpec> makeRequestParameters(
            final ParameterFieldName.Input fieldName, final String prefix,
            final ApiField field) {
        final ImmutableList.Builder<FieldSpec> builder = ImmutableList
                .<FieldSpec> builder();
        final String parameterName = makeParameterName(prefix, field);
        if (field.isPrimitive()) {
            builder.add(makeInputParameter(fieldName, parameterName, field,
                    field.required));
        } else {
            for (ApiField subField : field.type.fields) {
                final String subPrefix;
                if (subField.hasChildElements()) {
                    subPrefix = makeParameterName(parameterName, subField);
                } else {
                    subPrefix = parameterName;
                }
                builder.addAll(makeRequestParameters(fieldName, subPrefix,
                        subField));
            }
        }
        return builder.build();
    }

    /**
     * Make the parameters in this response entity
     * 
     * @param fieldName
     *            output field name generator
     * @param prefix
     *            A prefix to prepend the output field name
     * @param field
     *            The field in this response
     * @return A list of fields in the response entity
     */
    private static ImmutableList<FieldSpec> makeResponseParameters(
            final ParameterFieldName.Output fieldName, final String prefix,
            final ApiField field) {
        final ImmutableList.Builder<FieldSpec> builder = ImmutableList
                .<FieldSpec> builder();
        final String parameterName = makeParameterName(prefix, field);
        if (field.isPrimitive()) {
            builder.add(makeOutputParameter(fieldName, parameterName, field));
        } else {
            for (ApiField subField : field.type.fields) {
                final String subPrefix;
                if (subField.hasChildElements()) {
                    subPrefix = makeParameterName(parameterName, subField);
                } else {
                    subPrefix = parameterName;
                }
                builder.addAll(makeResponseParameters(fieldName, subPrefix,
                        subField));
            }
        }
        return builder.build();
    }

    private static FieldSpec makeInputParameter(
            final ParameterFieldName.Input fieldName, final ApiField field,
            final boolean required) {
        return makeInputParameter(fieldName, field.name, field, required);
    }

    private static FieldSpec makeInputParameter(
            final ParameterFieldName.Input fieldName,
            final String parameterName, final ApiField field,
            final boolean required) {
        return FieldSpec
                .builder(InputParameter.class, fieldName.generateName())
                .addModifiers(Modifier.PRIVATE, Modifier.FINAL, Modifier.STATIC)
                .initializer(
                        makeInputParameterInitializer(parameterName, field,
                                required)).build();
    }

    private static FieldSpec makeOutputParameter(
            final ParameterFieldName.Output fieldName,
            final String parameterName, final ApiField field) {
        return FieldSpec
                .builder(OutputParameter.class, fieldName.generateName())
                .addModifiers(Modifier.PRIVATE, Modifier.FINAL, Modifier.STATIC)
                .initializer(
                        makeOutputParameterInitializer(parameterName, field))
                .build();
    }

    private static CodeBlock makeInputParameterInitializer(final String name,
            final ApiField field, final boolean required) {
        return CodeBlock
                .builder()
                .add("new $T($S, $L, null)", getInputParameterType(field),
                        name, required).build();
    }

    private static CodeBlock makeOutputParameterInitializer(final String name,
            final ApiField field) {
        return CodeBlock.builder()
                .add("new $T($S)", getOutputParameterType(field), name).build();
    }

    private static Class<? extends BasicInputParameter<?>> getInputParameterType(
            final ApiField field) {

        final String type;
        if (field.isPrimitive()) {
            if (null != field.primitiveType && !field.primitiveType.isEmpty()) {
                type = field.primitiveType;
            } else {
                // TODO: some primitives (i.e. boolean) do not have a type set
                // Not sure if this is correct
                type = "Boolean";
            }
        } else {
            type = field.type.name;
        }

        switch (type) {
        case "URI":
            return BasicInputParameter.URIParameter.class;
        case "String":
            return BasicInputParameter.StringParameter.class;
        case "Boolean":
            return BasicInputParameter.BooleanParameter.class;
        case "Integer":
            return BasicInputParameter.IntegerParameter.class;
        case "Long":
            return BasicInputParameter.LongParameter.class;
        case "Map":
            return BasicInputParameter.NameValueListParameter.class;
        case "DateTime":
            return BasicInputParameter.DateTimeParameter.class;
        default:
            throw new RuntimeException("Unknown type:" + type);
        }
    }

    private static Class<? extends BasicOutputParameter> getOutputParameterType(
            final ApiField field) {

        final String type;
        if (field.isPrimitive()) {
            if (null != field.primitiveType && !field.primitiveType.isEmpty()) {
                type = field.primitiveType;
            } else {
                // TODO: some primitives (i.e. boolean) do not have a type set
                // Not sure if this is correct
                type = "Boolean";
            }
        } else {
            type = field.type.name;
        }

        switch (type) {
        case "URI":
            return BasicOutputParameter.URIParameter.class;
        case "String":
            return BasicOutputParameter.StringOutputParameter.class;
        case "Boolean":
            return BasicOutputParameter.BooleanParameter.class;
        case "Integer":
            return BasicOutputParameter.IntegerParameter.class;
        case "Short":
            return BasicOutputParameter.ShortParameter.class;
        case "Long":
            return BasicOutputParameter.LongParameter.class;
        case "Double":
            return BasicOutputParameter.DoubleParameter.class;
        case "Map":
            return BasicOutputParameter.NameValueListParameter.class;
        case "DateTime":
            return BasicOutputParameter.DateTimeParameter.class;
        case "Date":
            return BasicOutputParameter.DateParameter.class;
        default:
            throw new RuntimeException("Unknown type:" + type);
        }
    }

    private static String makePrimitiveName(ApiMethod method) {
        return method.apiService.javaClassName
                + StringUtils
                        .toUpperCase(method.javaMethodName.substring(0, 1))
                + method.javaMethodName.substring(1);
    }

    private static String makeFriendlyName(final String name,
            final ApiMethod method) {
        if (null == method.brief || method.brief.isEmpty()) {
            return name;
        } else {
            return method.brief;
        }
    }

    private static String makeParameterName(final String prefix,
            final ApiField field) {
        final StringBuilder name = new StringBuilder();

        if (!prefix.isEmpty()) {
            name.append(prefix + ".");
        }

        if (field.wrapperName != null && !field.wrapperName.isEmpty()) {
            name.append(field.wrapperName);
            name.append('.');
        }

        if (field.collection) {
            name.append('@');
        }

        name.append(field.name);

        return name.toString();
    }

    private static abstract class ParameterFieldName {

        private final String prefix;
        private int index;

        ParameterFieldName(final String prefix) {
            index = 0;
            this.prefix = prefix;
        }

        public String generateName() {
            return prefix + index++;
        }

        private static class Input extends ParameterFieldName {

            Input() {
                super("INPUT_");
            }
        }

        private static class Output extends ParameterFieldName {

            Output() {
                super("OUTPUT_");
            }
        }
    }

}
