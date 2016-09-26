package com.emc.storageos.db.client.model;

@SuppressWarnings("serial")
@Cf("StorageDriverFile")
public class StorageDriverFile extends DataObject {
    private String driverName;
    private byte[] chunk;
    private Integer number;

    @Name("driverName")
    public String getDriverName() {
        return driverName;
    }

    public void setDriverName(String driverName) {
        this.driverName = driverName;
        setChanged("driverName");
    }

    @Name("chunk")
    public byte[] getChunk() {
        return chunk;
    }

    public void setChunk(byte[] chunk) {
        this.chunk = chunk;
        setChanged("chunk");
    }

    @Name("number")
    public Integer getNumber() {
        return number;
    }

    public void setNumber(Integer number) {
        this.number = number;
        setChanged("number");
    }

}
