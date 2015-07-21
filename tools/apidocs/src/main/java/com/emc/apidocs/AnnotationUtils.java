/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.apidocs;

import com.sun.javadoc.AnnotationDesc;
import com.sun.javadoc.Parameter;
import com.sun.javadoc.ProgramElementDoc;

import java.util.Collections;

/**
 * A collection of Utitilties for processing annotations
 */
public class AnnotationUtils {

    public static boolean hasAnnotation(ProgramElementDoc element, String annotationType) {
        for (AnnotationDesc annotation : element.annotations()) {
            if (annotation.annotationType().qualifiedTypeName().equals(annotationType)) {
                return true;
            }
        }

        return false;
    }

    public static boolean hasAnnotationElement(ProgramElementDoc programElement, String annotationType, String elementName) {
        AnnotationDesc annotationDesc = getAnnotation(programElement, annotationType);
        if (annotationDesc != null) {
            for (AnnotationDesc.ElementValuePair element : annotationDesc.elementValues()) {
                if (element.element().name().equals(elementName)) {
                    return true;
                }
            }
        }

        return false;
    }


    public static boolean hasAnnotation(Parameter element, String name) {
        for (AnnotationDesc annotation : element.annotations()) {
            if (annotation.annotationType().qualifiedTypeName().equals(name)) {
                return true;
            }
        }

        return false;
    }

    public static AnnotationDesc getAnnotation(ProgramElementDoc element, String name) {
        if (element == null) {
            return null;
        }

        for (AnnotationDesc annotation : element.annotations()) {
            if (annotation.annotationType().qualifiedTypeName().equals(name)) {
                return annotation;
            }
        }

        return null;
    }

    public static AnnotationDesc getAnnotation(Parameter parameter, String name) {
        for (AnnotationDesc annotation : parameter.annotations()) {
            if (annotation.annotationType().qualifiedTypeName().equals(name)) {
                return annotation;
            }
        }

        return null;
    }

    /**
     * Retrieves the element from the Annotation if present from a parameter, i.e @PathParam("id")
     * If the annotation isn't present, or the element isn't present, the defaultValue is returned.
     *
     * For Annotations with just a value, i.e @Path("/mypath") the element name is {@link KnownAnnotations.Value_Element}
     */
    public static <T> T getAnnotationValue(Parameter parameter, String annotation, String elementName, T defaultValue) {
        AnnotationDesc annotationDesc = getAnnotation(parameter, annotation);
        if (annotationDesc != null) {
            for (AnnotationDesc.ElementValuePair element : annotationDesc.elementValues()) {
                if (element.element().name().equals(elementName)) {
                    return (T)element.value().value();
                }
            }
        }

        return defaultValue;
    }

    /**
     * Retrieves the element from the Annotation if present, i.e @XmlElement(required=true) would be an annotation of XmlElement
     * and an element name of "required".  If the annotation isn't present, or the element isn't present, the defaultValue is returned.
     *
     * For Annotations with just a value, i.e @Path("/mypath") the element name is {@link KnownAnnotations.Value_Element}
     */
    public static <T> T getAnnotationValue(ProgramElementDoc method, String annotation, String elementName, T defaultValue) {
        AnnotationDesc annotationDesc = getAnnotation(method, annotation);
        if (annotationDesc != null) {
            for (AnnotationDesc.ElementValuePair element : annotationDesc.elementValues()) {
                if (element.element().name().equals(elementName)) {
                    return (T)element.value().value();
                }
            }
        }

        return defaultValue;
    }
}
