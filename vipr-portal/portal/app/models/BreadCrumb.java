/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
package models;

public class BreadCrumb {
    public String id;
    public String name;
    public String title;
    public String path;

    public BreadCrumb() {
    }

    public BreadCrumb(String id, String name, String path) {
        this(id, name, name, path);
    }

    public BreadCrumb(String id, String name, String title, String path) {
        this.id = id;
        this.name = name;
        this.title = title;
        this.path = path;
    }
}
