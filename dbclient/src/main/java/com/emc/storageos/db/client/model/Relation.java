/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.db.client.model;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Defines a relationship between two data model objects using object references as opposed to URI's
 * 
 * URI or StringSet field is required as the mapped by field. The the
 * 
 * The object or list of objects referenced by this relationship are "lazy loaded" when the getter method
 * for this field is called. This lazy loading takes place behind the scenes using the mapped by field
 * to query the object or list of objects out of the database
 * 
 * The mapped by field can be either within the same class as the related field or in a different class. If
 * the mapped by field is in the same class, the relationship is maintained when the object is persisted.
 * 
 * If the mapped by field is in a different class, the relationship is not maintained when the object is persisted.
 * 
 * For more information: https://asdwiki.isus.emc.com:8443/display/OS/ViPR+Data+Layer+Interface
 * 
 * @author cgarber
 * 
 */
@Documented
@Target({ ElementType.METHOD })
@Retention(RetentionPolicy.RUNTIME)
public @interface Relation {
    /**
     * type of the referenced object
     * 
     * Required for lists; optional for DataObjects
     * 
     * @return
     */
    Class<? extends DataObject> type() default DataObject.class;

    /**
     * the contents of the @Name attribute for the URI or StringSet field that maps this relationship
     * 
     * Required
     * 
     * @return
     */
    String mappedBy();

}
