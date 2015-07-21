/*
 * Copyright (c) 2008-2013 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.db.common.schema;


import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.List;

import javax.xml.bind.annotation.XmlElement;

import com.google.common.base.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.model.Cf;
import com.emc.storageos.db.client.model.Name;
import com.emc.storageos.db.client.model.Ttl;
import com.emc.storageos.db.client.upgrade.CustomMigrationCallback;
import com.emc.storageos.db.common.DbSchemaScannerInterceptor;

public class Annotations {
    private static final Logger log = LoggerFactory.getLogger(Annotations.class);

    private List<AnnotationType> annotations = new ArrayList<AnnotationType>();

    public Annotations() {
    }

    public Annotations(RuntimeType runtimeType, Annotation[] annotations, SchemaObject parent) {
        this(runtimeType, annotations, parent, (DbSchemaScannerInterceptor)null);
    }

    public Annotations(RuntimeType runtimeType, Annotation[] annotations, SchemaObject parent, DbSchemaScannerInterceptor scannerInterceptor) {
        for (int i = 0; i < annotations.length; i++) {
            Annotation annotation = annotations[i];

            if (!annotation.annotationType().getPackage().getName().startsWith(
                    "com.emc.storageos"))
                continue;
            // Ttl doesn't affect the serialization behavior
            if (annotation.annotationType().equals(Ttl.class))
                continue;
            // This will override the field name if present, so no need to record it here
            // Cf annotation will overwrite the name of the DbSchema, so, skip it here
            if (annotation.annotationType().equals(Name.class) || annotation.annotationType().equals(Cf.class))
                continue;
            // CustomMigrationCallback will be replaced by versioned migration callbacks
            if (annotation.annotationType().equals(CustomMigrationCallback.class))
                continue;
            if (scannerInterceptor != null) {
                boolean isClassAnnotation = runtimeType.getPropertyDescriptor() == null;
                if (isClassAnnotation && scannerInterceptor.isClassAnnotationIgnored(runtimeType.getCfClass().getSimpleName(), annotations[i].annotationType().getSimpleName())) {
                    String msg = String.format("Class annotation %s:%s is ignored in schema due to interceptor", runtimeType.getCfClass().getSimpleName(), annotations[i].annotationType().getSimpleName());
                    log.warn(msg);                
                    continue;
                } else if (!isClassAnnotation && scannerInterceptor.isAnnotationIgnored(runtimeType.getCfClass().getSimpleName(), runtimeType.getPropertyDescriptor().getName(), annotations[i].annotationType())) {
                    String msg = String.format("Property annotation %s:%s:%s is ignored in schema due to interceptor", runtimeType.getCfClass().getSimpleName(), runtimeType.getPropertyDescriptor().getName(), annotations[i].annotationType().getSimpleName());
                    log.warn(msg);                    
                    continue;
                }                
            }
            this.annotations.add(new AnnotationType(runtimeType, annotations[i], parent));
        }
    }

    @XmlElement(name="annotation")
    public List<AnnotationType> getAnnotations() {
        return annotations;
    }

    public void setAnnotations(List<AnnotationType> annotations) {
        this.annotations = annotations;
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof Annotations)) 
            return false;

        List<AnnotationType> annotations = ((Annotations)o).getAnnotations();
        return Objects.equal(this.annotations, annotations);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(annotations);
    }
}
