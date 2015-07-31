/*
 * Copyright (c) 2008-2011 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.vnx.xmlapi;


public class VNXQuotaTree extends VNXBaseClass {
    private String name;
    private int id;
    private int fsId;
    private String path;
    private Long size;

    public void setName(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public String getPath() {
        return path;
    }

    public void setSize(Long size) {
        this.size = size;
    }

    public Long getSize() {
        return size;
    }

    public void setId(int id) {
        this.id = id;
    }

    public int getId() {
        return id;
    }

    public void setFileSystemId(int id) {
        this.fsId = id;
    }

    public int getFsId() {
        return fsId;
    }

    public VNXQuotaTree() {
    }

    public VNXQuotaTree(String name, int id) {
        this.name = name;
        this.id = id;
    }

    public VNXQuotaTree(String name, int id, int fsId) {
        this.name = name;
        this.id = id;
        this.fsId = fsId;
    }

    public String createQuotaTreeXML() {
        String xml = requestHeader +
                "\t<StartTask timeout=\"" + timeout + "\">\n" +
                "\t<NewTree fileSystem=\"" + fsId + "\" path=\"" + path + "\"/>\n" +
                "\t</StartTask>\n" +
                requestFooter;

        return xml;
    }

    public String modifyQuotaTreeXML() {
        String xml = requestHeader +
                "\t<StartTask timeout=\"" + timeout + "\">\n" +
                "\t<ModifyTreeQuota fileSystem=\"" + fsId + "\" path=\"" + path + "\">\n" +
                "\t<Limits spaceHardLimit=\"" + size + "\"/>\n" +
                "\t</ModifyTreeQuota>\n" +
                "\t</StartTask>\n" +
                requestFooter;

        return xml;
    }

    public String deleteQuotaTreeXML() {
        String xml = requestHeader +
                "\t<StartTask timeout=\"" + timeout + "\">\n" +
                "\t<DeleteTree fileSystem=\"" + fsId + "\" path=\"" + path + "\"/>\n" +
                "\t</StartTask>\n" +
                requestFooter;

        return xml;
    }
}
