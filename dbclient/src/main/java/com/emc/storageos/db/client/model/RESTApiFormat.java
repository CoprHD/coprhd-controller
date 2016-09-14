package com.emc.storageos.db.client.model;

/**
 * Created by sonalisahu on 7/6/16.
 */
@Cf("OEworkflow")
public class RESTApiFormat extends DataObject
{
    public static final String  REST_OPERATION= "RESTOp";
    public static final String REST_FORMAT = "RESTFormat";

    private String RESTop;
    private String RESTFormat;

    @AlternateId("RESTOpToRESTApiFormat")
    @Name(REST_OPERATION)
    public String getRESTOp() {
        return RESTop;
    }

    public void setRESTOp(String RESTOp) {
        this.RESTop = RESTOp;
        setChanged(RESTop);
    }

    @Name(REST_FORMAT)
    public String getRESTFormat() {
        return RESTFormat;
    }

    public void setRESTFormat(String RESTFormat) {
        this.RESTFormat = RESTFormat;
        setChanged(RESTFormat);
    }
}


