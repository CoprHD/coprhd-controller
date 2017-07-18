/*
 *
 *  * Copyright (c) 2016 EMC Corporation
 *  * All Rights Reserved
 *
 */

package com.emc.storageos.db.client.model;

import java.lang.annotation.*;

@Repeatable(DbViews.class)
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface DbView {
    String cf() default "";
    String pkey();
    String[] clkeys();
    String[] cols();
}
