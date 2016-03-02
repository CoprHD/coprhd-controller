/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.db.common;

import java.beans.PropertyDescriptor;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.Collection;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.impl.ColumnField;
import com.emc.storageos.db.client.model.Cf;
import com.emc.storageos.db.client.model.DataObject;
import com.emc.storageos.db.client.model.NamedRelationIndex;
import com.emc.storageos.db.client.model.RelationIndex;

public class DependencyInterceptor {
	private static final Logger log = LoggerFactory.getLogger(DependencyInterceptor.class);
	private Collection<Class<?>> modelClasses;
	
	public DependencyInterceptor(Collection<Class<?>> modelClasses) {
		super();
		this.modelClasses = modelClasses;
	}

    /**
     * Check the clazz if it has Cf annotation
     *
     * @param clazz the class which to check against
     * @return true if has Cf annotation 
     */
	@SuppressWarnings({ "unchecked", "rawtypes" })
	public boolean isConcretModelClass(Class clazz) {
		return clazz.getAnnotation(Cf.class) != null;
	}
	
    /**
     * handle the dependency if type attribute set to abstract class in RelationIndex/NamedRelationIndex
     *
     * @param tracker dependency tracker
     * @param sourceClazz the DataObject Type 
     * @param field the property has Relation/NamedRelationIndex annotation in sourceClazz  
     * @return
     */
	@SuppressWarnings({ "rawtypes" })
	public void handleDependency(DependencyTracker tracker,  Class sourceClazz, ColumnField field) {
		log.info("process dependency of class {} field {}", sourceClazz.getSimpleName(), field.getName());
		if (field.getIndexRefType().equals(DataObject.class)) {
			if (hasMutipleDependencies(field)) {
				addMutilpleDependencies(tracker, sourceClazz, field);
			}
		} else {
			addConcretDependencies(tracker, sourceClazz, field);
		}
	}
	
	@SuppressWarnings({ "rawtypes", "unchecked" })
	private void addConcretDependencies(DependencyTracker tracker, Class sourceClazz, ColumnField field) {
		Class targetClazz = field.getIndexRefType();
		for (Class clazz : this.modelClasses) {
			if (targetClazz.isAssignableFrom(clazz) && isConcretModelClass(clazz)) {
				log.info("{} depends on {}:"+field.getName(), clazz.getSimpleName(), sourceClazz.getSimpleName());
				tracker.addDependency(clazz, sourceClazz, field);
			}
		}
	}

	@SuppressWarnings({ "rawtypes", "unchecked" })
	private void addMutilpleDependencies(DependencyTracker tracker, Class sourceClazz, ColumnField field) {
		Annotation a = this.getRelationAnnotation(field);
		Class<? extends DataObject>[] refTypes; 
		
		if (a instanceof RelationIndex) {
			refTypes = ((RelationIndex) a).types();
		} else {
			refTypes = ((NamedRelationIndex) a).types();
		}
		
		for (Class<? extends DataObject> type : refTypes) {
			log.info("{} depends on {}:"+field.getName(), type.getSimpleName(), sourceClazz.getSimpleName());
			tracker.addDependency(type, sourceClazz, field);
		}
	}
	
	private boolean hasMutipleDependencies(ColumnField field) {
		Annotation a = this.getRelationAnnotation(field);
		if (a instanceof RelationIndex) {
			return ((RelationIndex) a).types().length > 0;
		} else if (a instanceof NamedRelationIndex) {
			return ((NamedRelationIndex) a).types().length > 0;
		}
		
		return false;
	}

	private Annotation getRelationAnnotation(ColumnField field) {
		PropertyDescriptor property = field.getPropertyDescriptor();
		Method method = property.getReadMethod();
		for (Annotation annotation :  method.getAnnotations()) {
			if (annotation instanceof RelationIndex) {
				return annotation;
			} else if (annotation instanceof NamedRelationIndex) {
				return annotation;
			}
		}
		
		throw new IllegalStateException("field:" + field.getName() + " should have RelationIndex annotation");
	}
}
