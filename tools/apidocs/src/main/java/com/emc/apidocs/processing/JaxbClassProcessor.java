/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.apidocs.processing;

import com.emc.apidocs.AnnotationUtils;
import com.emc.apidocs.KnownAnnotations;
import com.emc.apidocs.Utils;
import com.emc.apidocs.model.ApiClass;
import com.emc.apidocs.model.ApiField;
import com.sun.javadoc.*;

/**
 * Converts a Java JAXB class into an ApiClass definition
 */
public class JaxbClassProcessor {

    public static ApiClass convertToApiClass(ClassDoc classDoc) {
        ApiClass classDescriptor = new ApiClass();
        classDescriptor.name = AnnotationUtils.getAnnotationValue(classDoc, KnownAnnotations.XMLElement_Annotation, "name", null);

        if (classDescriptor.name == null) {
            classDescriptor.name = AnnotationUtils.getAnnotationValue(classDoc, KnownAnnotations.XMLRoot_Annotation, "name",
                    classDoc.simpleTypeName());
        }

        ClassDoc currentClass = classDoc;
        while (currentClass.qualifiedName().startsWith("com.emc")) {
            String xmlAccessType = getXmlAccessType(classDoc);

            // Read Fields
            for (FieldDoc field : currentClass.fields()) {
                if (shouldIncludeField(field, xmlAccessType)) {
                    ApiField fieldDescriptor = new ApiField();
                    fieldDescriptor.name = AnnotationUtils.getAnnotationValue(field, KnownAnnotations.XMLElement_Annotation, "name",
                            field.name());
                    fieldDescriptor.required = AnnotationUtils.getAnnotationValue(field, KnownAnnotations.XMLElement_Annotation,
                            "required", false);
                    fieldDescriptor.description = field.commentText();

                    if (AnnotationUtils.hasAnnotation(field, KnownAnnotations.XMLElementWrapper_Annotation)) {
                        fieldDescriptor.wrapperName = AnnotationUtils.getAnnotationValue(field,
                                KnownAnnotations.XMLElementWrapper_Annotation, "name", Utils.lowerCaseFirstChar(field.name()));
                    }

                    addFieldType(field.type(), fieldDescriptor);
                    addValidValues(field, fieldDescriptor);

                    classDescriptor.addField(fieldDescriptor);
                }

                if (AnnotationUtils.hasAnnotation(field, KnownAnnotations.XMLAttribute_Annotation)) {
                    ApiField attributeDescriptor = new ApiField();
                    attributeDescriptor.name = AnnotationUtils.getAnnotationValue(field, KnownAnnotations.XMLAttribute_Annotation, "name",
                            field.name());
                    attributeDescriptor.required = AnnotationUtils.getAnnotationValue(field, KnownAnnotations.XMLAttribute_Annotation,
                            "required", false);
                    attributeDescriptor.description = field.commentText();

                    addFieldType(field.type(), attributeDescriptor);
                    addValidValues(field, attributeDescriptor);

                    classDescriptor.addAttribute(attributeDescriptor);
                }
            }

            // Read Public Property Methods
            for (MethodDoc method : currentClass.methods()) {
                if (shouldIncludeMethod(method, xmlAccessType, currentClass)) {
                    ApiField methodDescriptor = new ApiField();
                    methodDescriptor.name = AnnotationUtils
                            .getAnnotationValue(method, KnownAnnotations.XMLElement_Annotation, "name", null);

                    if (methodDescriptor.name == null) {
                        if (method.name().startsWith("get")) {
                            methodDescriptor.name = Utils.lowerCaseFirstChar(method.name().substring(3));
                        }
                        else {
                            methodDescriptor.name = method.name();
                        }
                    }

                    methodDescriptor.required = AnnotationUtils.getAnnotationValue(method, KnownAnnotations.XMLElement_Annotation,
                            "required", false);
                    methodDescriptor.description = method.commentText();

                    if (AnnotationUtils.hasAnnotation(method, KnownAnnotations.XMLElementWrapper_Annotation)) {
                        methodDescriptor.wrapperName = AnnotationUtils.getAnnotationValue(method,
                                KnownAnnotations.XMLElementWrapper_Annotation, "name", null);

                        if (methodDescriptor.wrapperName == null) {
                            if (method.name().startsWith("get")) {
                                methodDescriptor.wrapperName = Utils.lowerCaseFirstChar(method.name().substring(3));
                            }
                            else if (method.name().startsWith("is")) {
                                methodDescriptor.wrapperName = Utils.lowerCaseFirstChar(method.name().substring(2));
                            }
                            else {
                                throw new RuntimeException("Unable to work out JavaBean property name " + method.qualifiedName());
                            }
                        }
                    }

                    // process JsonProperty annotation
                    String jsonName = AnnotationUtils.getAnnotationValue(method, KnownAnnotations.JsonProperty_Annotation,
                            KnownAnnotations.Value_Element, null);
                    if (jsonName != null) {
                        methodDescriptor.jsonName = jsonName;
                    }

                    addFieldType(method.returnType(), methodDescriptor);
                    addValidValues(method, methodDescriptor);

                    classDescriptor.addField(methodDescriptor);
                }
            }

            currentClass = currentClass.superclass();
        }

        return classDescriptor;
    }

    /** Returns true of false if the fields should be included based on the accessType */
    private static boolean shouldIncludeField(FieldDoc field, String accessType) {
        if (field.isStatic() ||
                AnnotationUtils.hasAnnotation(field, KnownAnnotations.XMLTransient_Annotation) ||
                AnnotationUtils.hasAnnotation(field, KnownAnnotations.XMLAttribute_Annotation)) {
            return false;
        }

        if (accessType.equals("FIELD")) {
            // Every non static, non transient field in a JAXB-bound class will be automatically bound to XML, unless annotated by
            // XmlTransient.
            return !field.isStatic() && !field.isTransient();
        }
        else if (accessType.equals("PUBLIC_MEMBER")) {
            // Every public getter/setter pair and every public field will be automatically bound to XML, unless annotated by XmlTransient.
            return field.isPublic();
        }
        else if (accessType.equals("PROPERTY")) {
            // Every getter/setter pair in a JAXB-bound class will be automatically bound to XML, unless annotated by XmlTransient.
            return false;
        }
        else if (accessType.equals("NONE")) {
            // None of the fields or properties is bound to XML unless they are specifically annotated with some of the JAXB annotations.
            return false;
        }

        return false;
    }

    /** Returns true of false if the fields should be included based on the accessType */
    private static boolean shouldIncludeMethod(MethodDoc method, String accessType, ClassDoc classDoc) {
        if (method.isStatic() ||
                AnnotationUtils.hasAnnotation(method, KnownAnnotations.XMLTransient_Annotation) ||
                AnnotationUtils.hasAnnotation(method, KnownAnnotations.XMLAttribute_Annotation)) {
            return false;
        }

        if (accessType.equals("FIELD")) {
            // Every non static, non transient field in a JAXB-bound class will be automatically bound to XML, unless annotated by
            // XmlTransient.
            return false;
        }
        else if (accessType.equals("PUBLIC_MEMBER")) {
            // Every public getter/setter pair and every public field will be automatically bound to XML, unless annotated by XmlTransient.
            return method.isPublic() &&
                    method.name().startsWith("get") &&
                    hasMatchingSetter(method.name(), classDoc);
        }
        else if (accessType.equals("PROPERTY")) {
            // Every getter/setter pair in a JAXB-bound class will be automatically bound to XML, unless annotated by XmlTransient.
            return method.isPublic() &&
                    method.name().startsWith("get") &&
                    hasMatchingSetter(method.name(), classDoc);
        }
        else if (accessType.equals("NONE")) {
            // None of the fields or properties is bound to XML unless they are specifically annotated with some of the JAXB annotations.
            return false;
        }

        return false;
    }

    private static boolean hasMatchingSetter(String getterName, ClassDoc classDoc) {
        String setterName = "set" + getterName.substring(3);
        // Search for a matching setter
        for (MethodDoc m : classDoc.methods()) {
            if (m.name().equals(setterName)) {
                return true;
            }
        }
        return false;
    }

    public static void addFieldType(Type type, ApiField descriptor) {

        // Default Values?
        // Look for @Length and @Range
        if (TypeUtils.isCollectionType(type)) {
            descriptor.collection = true;
            Type parameterisedType = type.asParameterizedType().typeArguments()[0];
            if (TypeUtils.isPrimitiveType(parameterisedType)) {
                descriptor.primitiveType = parameterisedType.simpleTypeName();
            } else {
                descriptor.type = convertToApiClass(parameterisedType.asClassDoc());
            }
        }
        else if (TypeUtils.isPrimitiveType(type)) {
            descriptor.primitiveType = type.simpleTypeName();

            // Convert to XML types
            if (descriptor.primitiveType.equals("Calendar")) {
                descriptor.primitiveType = "DateTime";
            }

            if (descriptor.primitiveType.equals("int")) {
                descriptor.primitiveType = "Integer";
            }

            descriptor.primitiveType = Utils.upperCaseFirstChar(descriptor.primitiveType);

        }
        else {
            descriptor.type = convertToApiClass(type.asClassDoc());
        }
    }

    public static void addValidValues(ProgramElementDoc field, ApiField apiField) {
        for (Tag tag : field.tags()) {
            if (tag.name().equals("@valid")) {
                if (!tag.text().toLowerCase().equals("none")) {
                    String tagText = tag.text();
                    apiField.validValues.add(tagText.trim());
                }
            }
        }

        AnnotationDesc lengthAnnotation = AnnotationUtils.getAnnotation(field, KnownAnnotations.Length_Annotation);
        if (lengthAnnotation != null) {
            int min = 0;
            int max = Integer.MAX_VALUE;
            for (AnnotationDesc.ElementValuePair pair : lengthAnnotation.elementValues()) {

                if (pair.element().name().equals("min")) {
                    min = (Integer) pair.value().value();
                }

                if (pair.element().name().equals("max")) {
                    max = (Integer) pair.value().value();
                }
            }
            apiField.validValues.add("Length: " + min + ".." + max);
        }

        AnnotationDesc rangeAnnotation = AnnotationUtils.getAnnotation(field, KnownAnnotations.Range_Annotation);
        if (rangeAnnotation != null) {
            long min = 0;
            long max = Long.MAX_VALUE;
            for (AnnotationDesc.ElementValuePair pair : rangeAnnotation.elementValues()) {

                if (pair.element().name().equals("min")) {
                    min = (Long) pair.value().value();
                }

                if (pair.element().name().equals("max")) {
                    max = (Long) pair.value().value();
                }
            }
            apiField.validValues.add("Range: " + min + ".." + max);
        }
    }

    /** Searches the Super Classes for an inherited XmlAccessType annotation */
    private static String getXmlAccessType(ClassDoc classDoc) {
        String xmlAccessType = "";
        ClassDoc currentDoc = classDoc;

        while (currentDoc.qualifiedName().startsWith("com.emc") && xmlAccessType.equals("")) {
            FieldDoc xmlAccessTypeEnum = AnnotationUtils.getAnnotationValue(currentDoc, KnownAnnotations.XMLAccessorType_Annotation,
                    KnownAnnotations.Value_Element, null);
            if (xmlAccessTypeEnum != null) {
                return xmlAccessTypeEnum.name();
            }

            currentDoc = currentDoc.superclass();
        }

        return "PUBLIC_MEMBER"; // Default AccessType
    }

}
