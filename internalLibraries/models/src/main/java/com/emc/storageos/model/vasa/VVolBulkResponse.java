package com.emc.storageos.model.vasa;

import java.util.ArrayList;
import java.util.List;

import javax.xml.bind.annotation.XmlElement;

public class VVolBulkResponse {

    List<VVolResponseParam> vVols;

    @XmlElement(name="vvol_response_param")
    public List<VVolResponseParam> getvVols() {
        if(vVols == null){
            vVols = new ArrayList<VVolResponseParam>();
        }
        return vVols;
    }

    public void setvVols(List<VVolResponseParam> vVols) {
        this.vVols = vVols;
    }
    
}
