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

import java.io.File;
import java.io.IOException;
import java.util.List;

import javax.lang.model.element.Modifier;

import com.emc.apidocs.ApiDoclet;
import com.emc.apidocs.DocReporter;
import com.emc.apidocs.model.ApiField;
import com.emc.apidocs.model.ApiMethod;
import com.emc.apidocs.model.ApiService;
import com.emc.storageos.primitives.ViPRPrimitive;
import com.emc.storageos.primitives.input.BasicInputParameter;
import com.emc.storageos.primitives.input.InputParameter;
import com.emc.storageos.primitives.output.OutputParameter;
import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.squareup.javapoet.CodeBlock;
import com.squareup.javapoet.FieldSpec;
import com.squareup.javapoet.JavaFile;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.TypeSpec;
import com.sun.javadoc.DocErrorReporter;
import com.sun.javadoc.LanguageVersion;
import com.sun.javadoc.RootDoc;
import com.sun.tools.javac.util.StringUtils;

/**
 */
public class PrimitiveDoclet {

    private static final String OUTPUT_OPTION = "-d";

    private static final String PACKAGE = "com.emc.storageos.primitives";
    private static final String SOURCE_DIR = "src/main/generated";
    private static String outputDirectory;

    private static final MethodSpec PATH_METHOD = MethodSpec.methodBuilder("path")
            .addAnnotation(Override.class)
            .addModifiers(Modifier.PUBLIC)
            .addStatement("return PATH")
            .returns(String.class)
            .build();
    
    private static final MethodSpec METHOD_METHOD = MethodSpec.methodBuilder("method")
            .addAnnotation(Override.class)
            .addModifiers(Modifier.PUBLIC)
            .addStatement("return METHOD")
            .returns(String.class)
            .build();
    
    private static final MethodSpec BODY_METHOD = MethodSpec.methodBuilder("body")
            .addAnnotation(Override.class)
            .addModifiers(Modifier.PUBLIC)
            .addStatement("return BODY")
            .returns(String.class)
            .build();
    
    private static final ImmutableList<MethodSpec> METHODS = ImmutableList.<MethodSpec>builder().add(PATH_METHOD,METHOD_METHOD,BODY_METHOD).build();
    
    public static boolean start(RootDoc root) {
        List<ApiService> services = ApiDoclet.findApiServices(root.classes());
        for(ApiService service : services) {
            for(ApiMethod method : service.methods) {
                makePrimitive(method);
            }
        }
        return true;
    }
    
    private static void makePrimitive(ApiMethod method) {
        final String name = makePrimitiveName(method);
        System.out.println("Making primitive: " + name); 
        
        TypeSpec primitive = TypeSpec.classBuilder(name)
                .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
                .superclass(ViPRPrimitive.class)
                .addMethods(METHODS)
                .addMethod(makeConstructor(name))
                .addFields(makeFields(method))
                .build();

        JavaFile javaFile = JavaFile.builder(PACKAGE, primitive)
                .build();
        
        try {
            javaFile.writeTo(new File(outputDirectory+SOURCE_DIR));
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }
    
    private static Iterable<FieldSpec> makeFields(final ApiMethod method) {
return ImmutableList.<FieldSpec>builder()
                .add(makeStringConstant("FRIENDLY_NAME", method.brief))
                .add(makeStringConstant("DESCRIPTION", method.description))
                .add(makeStringConstant("SUCCESS_CRITERIA", "code > 199 or code < 300"))
                .add(makeStringConstant("PATH", method.path))
                .add(makeStringConstant("METHOD", method.httpMethod))
                .add(makeStringConstant("BODY", ""))
                .addAll(makeInput(method))
                .add(makeOutput(method)).
                build();
    }
    
    private static MethodSpec makeConstructor(final String name) {
        return MethodSpec.constructorBuilder()
                .addModifiers(Modifier.PUBLIC)
                .addCode("super($L.class.getName(), FRIENDLY_NAME, DESCRIPTION, SUCCESS_CRITERIA, INPUT, OUTPUT);\n", name).build();
    }
    
    private static FieldSpec makeStringConstant(final String name, final String value) {
        return FieldSpec.builder(String.class, name)
                .addModifiers(Modifier.PRIVATE, Modifier.FINAL, Modifier.STATIC)
                .initializer("$S", value)
                .build();
    }
    
    private static Iterable<FieldSpec> makeInput(final ApiMethod method) {
        final ImmutableList.Builder<FieldSpec> builder = ImmutableList.<FieldSpec>builder();
        final ImmutableList.Builder<String> parameters = new ImmutableList.Builder<String>();
        
        for(ApiField pathParameter : method.pathParameters) {
            System.out.println("making path param: " + pathParameter.name); 
            FieldSpec param = makeInputParameter(pathParameter, true);
            parameters.add(param.name);
            builder.add(param);
        }
        
        for(ApiField queryParameter : method.queryParameters) {
            System.out.println("making query param: " + queryParameter.name); 
            FieldSpec param = makeInputParameter(queryParameter, queryParameter.required);
            parameters.add(param.name);
            builder.add(param);
        }

        return builder.add(FieldSpec.builder(InputParameter[].class, "INPUT")
                .addModifiers(Modifier.PRIVATE, Modifier.FINAL, Modifier.STATIC)
                .initializer("{$L}", Joiner.on(",").join(parameters.build()))
                .build()).build();
    }
    
    private static FieldSpec makeInputParameter(final ApiField field, final boolean required) {
        
        return FieldSpec.builder(InputParameter.class, "_"+StringUtils.toUpperCase(field.name).replace('-', '\0'))
                .addModifiers(Modifier.PRIVATE, Modifier.FINAL, Modifier.STATIC)
                .initializer(makeInputParameterInitializer(field, required))
                .build();
    }

    private static CodeBlock makeInputParameterInitializer(
            final ApiField field, final boolean required) {
        return CodeBlock.builder().addStatement("new $T($S, $L, null)",  
                getParameterType(field), 
                field.name, 
                required).build();
    }

    private static Class<? extends BasicInputParameter<?>> getParameterType(
            final ApiField field) {
        if( field.isPrimitive()) {
            if(null != field.primitiveType && !field.primitiveType.isEmpty()) throw new RuntimeException("primitive type not supported: "+field.primitiveType); 
        }
        // TODO: primitives (i.e. boolean) do not have a type set
        // Not sure if this is correct
        final String type;
        if( null == field.type ) {
            type = "Boolean";
        } else {
            type = field.type.name;
        }
        final Class<? extends BasicInputParameter<?>> parameterType;
        switch(type) {
        case "URI":
            parameterType = BasicInputParameter.URIParameter.class;
            break;
        case "String":
            parameterType = BasicInputParameter.StringParameter.class;
            break;
        case "Boolean":
            parameterType = BasicInputParameter.BooleanParameter.class;
            break;
        case "Integer":
            parameterType = BasicInputParameter.IntegerParameter.class;
            break;
        default:
            throw new RuntimeException("Unknown type:" + field.type.name);
        }
        return parameterType;
    }
    
    private static FieldSpec makeOutput(final ApiMethod method) {
        return FieldSpec.builder(OutputParameter[].class, "OUTPUT")
                .addModifiers(Modifier.PRIVATE, Modifier.FINAL, Modifier.STATIC)
                .initializer("{}")
                .build();
    }
    
    private static String makePrimitiveName(ApiMethod method) {
        return  method.apiService.javaClassName+StringUtils.toUpperCase(method.javaMethodName.substring(0, 1))+method.javaMethodName.substring(1);
    }
    /** Required by Doclet, otherwise it does not process Generics correctly */
    public static LanguageVersion languageVersion() {
        return LanguageVersion.JAVA_1_5;
    }

    /** Required by Doclet to check command line options */
    public static int optionLength(String option) {
        if (option.equals(OUTPUT_OPTION)) {
            return 2;
        }
        return 1;
    }

    /** Required by Doclet to process the command line options */
    public static synchronized boolean validOptions(String options[][],
            DocErrorReporter reporter) {
        DocReporter.init(reporter);
        DocReporter.printWarning("Processing Options");
        boolean valid = true;
        boolean outputOptionFound = false;

        // Make sure we have an OUTPUT option
        for (int i = 0; i < options.length; i++) {
            if (options[i][0].equals(OUTPUT_OPTION)) {
                outputOptionFound = true;
                valid = checkOutputOption(options[i][1], reporter);
            }
        }

        if (!outputOptionFound) {
            reporter.printError("Output dir option " + OUTPUT_OPTION + " not specified");
        }

        DocReporter.printWarning("Finished Processing Options");

        return valid && outputOptionFound;
    }
    
    private static synchronized boolean checkOutputOption(String value, DocErrorReporter reporter) {
        File file = new File(value);
        if (!file.exists()) {
            reporter.printError("Output directory (" + OUTPUT_OPTION + ") not found :" + file.getAbsolutePath());
            return false;
        }

        if (!file.isDirectory()) {
            reporter.printError("Output directory (" + OUTPUT_OPTION + ") is not a directory :" + file.getAbsolutePath());
            return false;
        }

        outputDirectory = value;
        if (!outputDirectory.endsWith("/")) {
            outputDirectory = outputDirectory + "/";
        }

        reporter.printWarning("Output Directory " + outputDirectory);

        return true;
    }

}
