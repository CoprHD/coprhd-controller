/*
 * Copyright (c) 2008-2011 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.vnx.xmlapi;


public class VNXSnapshot extends VNXBaseClass {
    private String name;
    private int id;
    private int fsId;

    public void setName(String name) {
        this.name = name;
    }
    public String getName(){
        return name;
    }
    public void setId(int id){
        this.id = id;
    }
    public int getId() {
        return id;
    }

    public void setFileSystemId(int id){
        this.fsId = id;
    }

    public int getFsId(){
        return fsId;
    }

    public VNXSnapshot() {}

    public VNXSnapshot(String name, int id){
        this.name = name;
        this.id = id;
    }

    public VNXSnapshot(String name, int id, int fsId){
        this.name = name;
        this.id = id;
        this.fsId = fsId;
    }
    
    public String getReadOnlySnapshotCreateXML() {
        String xml = requestHeader +
                "\t<StartTask timeout=\""+ timeout +"\">\n" +
                "\t<NewCheckpoint checkpointOf=\""+ fsId + "\" name=\""+ name +"\">\n" +
                "\t</NewCheckpoint>\n" +
                "\t</StartTask>\n" +
                requestFooter;

        return xml;
    }

    
    public String getDeleteXML(){
        String xml = requestHeader +
                "\t<StartTask timeout=\""+ timeout +"\">\n" +
                "\t<DeleteCheckpoint checkpoint=\"" + id +"\"/>\n" +
                "\t</StartTask>\n" +
                requestFooter;
        return xml;
    }

    public static String getAllSnapshots(){
        String xml = requestHeader +
                "\t<Query>\n" +
                "\t<CheckpointQueryParams/>\n" +
                "\t</Query>\n" +
                requestFooter;
        return xml;
    }


    public String getRestoreXML(){
        String xml = requestHeader +
                "\t<StartTask timeout=\""+ timeout +"\">\n" +
                "\t<RestoreCheckpoint checkpoint=\"" + id +"\"/>\n" +
                "\t</StartTask>\n" +
                requestFooter;
        return xml;
    }
}