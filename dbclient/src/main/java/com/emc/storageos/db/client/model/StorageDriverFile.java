package com.emc.storageos.db.client.model;

@SuppressWarnings("serial")
@Cf("StorageDriverFile")
public class StorageDriverFile extends DataObject {
    private String driverName;
    private byte[] chunk;
    private int number;

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
    public int getNumber() {
        return number;
    }

    public void setNumber(int number) {
        this.number = number;
        setChanged("number");
    }

}
