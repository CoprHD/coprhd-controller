package com.emc.storageos.model.vasa;

import javax.xml.bind.annotation.XmlRootElement;

@XmlRootElement(name="Sample_Soap_Request")
public class VVolCreateRequestParam {

    String create_vvol;

    public String getCreate_vvol() {
        return create_vvol;
    }

    public void setCreate_vvol(String create_vvol) {
        this.create_vvol = create_vvol;
    }
}
