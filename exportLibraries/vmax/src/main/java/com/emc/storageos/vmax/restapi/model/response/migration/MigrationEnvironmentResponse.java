package com.emc.storageos.vmax.restapi.model.response.migration;

import org.codehaus.jackson.annotate.JsonProperty;
import org.codehaus.jackson.map.annotate.JsonRootName;

import com.google.gson.Gson;
import com.google.gson.annotations.SerializedName;

/*
 https://{unipshere-ip}:{portnumber}/univmax/restapi/84/migration/symmetrix/{array-serailnumber}/environment/{other-array-serialnumber}
 
  {
  "symmetrixId": "{array-serailnumber}",
  "otherSymmetrixId": "{other-array-serialnumber}",
  "invalid": false,
  "state": "OK"
  }
  
 */

@JsonRootName(value = "symmetrixMigrationEnvironment")
public class MigrationEnvironmentResponse {

    @SerializedName("symmetrixId")
    @JsonProperty(value = "symmetrixId")
    private String symmetrixId;

    @SerializedName("otherSymmetrixId")
    @JsonProperty(value = "otherSymmetrixId")
    private String otherSymmetrixId;

    @SerializedName("invalid")
    @JsonProperty(value = "invalid")
    private boolean invalid;

    @SerializedName("state")
    @JsonProperty(value = "state")
    private String state;

    public String getSymmetrixId() {
        return symmetrixId;
    }

    public void setSymmetrixId(String symmetrixId) {
        this.symmetrixId = symmetrixId;
    }

    public String getOtherSymmetrixId() {
        return otherSymmetrixId;
    }

    public void setOtherSymmetrixId(String otherSymmetrixId) {
        this.otherSymmetrixId = otherSymmetrixId;
    }

    public boolean isInvalid() {
        return invalid;
    }

    public void setInvalid(boolean invalid) {
        this.invalid = invalid;
    }

    public String getState() {
        return state;
    }

    public void setState(String state) {
        this.state = state;
    }

    @Override
    public String toString() {
        return new Gson().toJson(this).toString();
    }

}
