package com.emc.storageos.model;

public class testResourceTypeEnum {
   

    public static void main(String[] args) {
        
        ResourceTypeEnum x = ResourceTypeEnum.fromString("file");
        
        System.out.println(x);

    }

}
