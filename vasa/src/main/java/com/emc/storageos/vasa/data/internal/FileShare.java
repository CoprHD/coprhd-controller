/*
 * Copyright (c) 2012 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.vasa.data.internal;

import java.util.List;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

/**
 * File share data object
 */
@XmlRootElement(name = "filesystem")
public class FileShare {

    public FileShare() {

    }

    public FileShare(String id) {
        this.id = id;
    }

    @XmlElement
    private String id;

    @XmlElement
    private boolean inactive;

    @XmlElement
    private String name;

    private FileSystemExports exports;

    @XmlElement(name = "mount_path")
    private String mountPath;

    @XmlElement(name = "capacity_gb")
    private double capacity;

    @XmlElement(name = "vpool")
    private AssociatedCoS cos;

    @XmlElement(name = "neighborhood")
    private AssociatedResource neighborhood;

    @XmlElement(name = "storage_pool")
    private AssociatedResource pool;

    @XmlElement(name = "project")
    private AssociatedResource project;

    @XmlElement(name = "protocols")
    private Protocol protocols;

    @XmlElement(name = "storage_system")
    private AssociatedResource storageController;

    @XmlElement(name = "storage_port")
    private AssociatedResource storagePort;

    public FileSystemExports getExports() {
        return exports;
    }

    public void setExports(FileSystemExports exports) {
        this.exports = exports;
    }

    public AssociatedResource getProject() {
        return project;
    }

    public AssociatedResource getStoragePort() {
        return storagePort;
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        builder.append("FileShare [id=");
        builder.append(id);
        builder.append(", inactive=");
        builder.append(inactive);
        builder.append(", name=");
        builder.append(name);
        builder.append(", exports=");
        builder.append(exports);
        builder.append(", mountPath=");
        builder.append(mountPath);
        builder.append(", capacity=");
        builder.append(capacity);
        builder.append(", cos=");
        builder.append(cos);
        builder.append(", neighborhood=");
        builder.append(neighborhood);
        builder.append(", pool=");
        builder.append(pool);
        builder.append(", project=");
        builder.append(project);
        builder.append(", protocols=");
        builder.append(protocols);
        builder.append(", storageController=");
        builder.append(storageController);
        builder.append(", storagePort=");
        builder.append(storagePort);
        builder.append("]");
        return builder.toString();
    }

    /**
     * @return the id
     */
    public String getId() {
        return id;
    }

    /**
     * @return the name
     */
    public String getName() {
        return name;
    }

    /**
     * @return the inactive
     */
    public boolean isInactive() {
        return inactive;
    }

    /**
     * @return the mountPath
     */
    public String getMountPath() {
        return mountPath;
    }

    /**
     * @return the protocols
     */
    public Protocol getProtocols() {
        return protocols;
    }

    public AssociatedResource getNeighborhood() {
        return neighborhood;
    }

    public AssociatedResource getStorageController() {
        return storageController;
    }

    /**
     * @return the capacity
     */
    public double getCapacity() {
        return capacity;
    }

    public AssociatedCoS getCos() {
        return cos;
    }

    public AssociatedResource getPool() {
        return pool;
    }

    public static class AssociatedResource {

        @XmlElement(name = "id")
        private String id;

        public String getId() {
            return id;
        }

        @Override
        public String toString() {
            StringBuilder builder = new StringBuilder();
            builder.append("AssociatedResource [id=");
            builder.append(id);
            builder.append("]");
            return builder.toString();
        }

    }

    @XmlRootElement(name = "filesystem_exports")
    public static class FileSystemExports {

        @XmlElement(name = "filesystem_export")
        private List<FileSystemExport> fsExportList;

        @Override
        public String toString() {
            StringBuilder builder = new StringBuilder();
            builder.append("FileSystemExports [fsExportList=");
            builder.append(fsExportList);
            builder.append("]");
            return builder.toString();
        }

        public List<FileSystemExport> getFsExportList() {
            return fsExportList;
        }

        @XmlRootElement(name = "filesystem_export")
        public static class FileSystemExport {

            @XmlElement(name = "mount_point")
            private String mountPoint;

            @XmlElement(name = "protocol")
            private String protocol;

            public String getMountPoint() {
                return mountPoint;
            }

            public String getProtocol() {
                return protocol;
            }

            @Override
            public String toString() {
                StringBuilder builder = new StringBuilder();
                builder.append("FileSystemExport [mountPoint=");
                builder.append(mountPoint);
                builder.append(", protocol=");
                builder.append(protocol);
                builder.append("]");
                return builder.toString();
            }

        }

    }

    @XmlRootElement(name = "results")
    public static class SearchResults {

        @XmlElement(name = "resource")
        private AssociatedResource resource;

        public AssociatedResource getResource() {
            return resource;
        }

        @Override
        public String toString() {
            StringBuilder builder = new StringBuilder();
            builder.append("SearchResults [resource=");
            builder.append(resource);
            builder.append("]");
            return builder.toString();
        }

    }

}
