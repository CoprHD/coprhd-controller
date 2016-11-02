package com.emc.storageos.dbcli;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

import com.emc.storageos.db.client.DbClient;


public class CFCommandObject {
    
    public enum Operations {
       INVENTORY_DELETE,
       VALIDATE
    };
    
    StringBuffer strBuffer = new StringBuffer();
    
    private CFCommandObject leftCmdObject;
    
    private CFCommandObject rightCmdObject;
    
    private CFCommandObject parent;
    
    private URI objectURI;
    
    
    private CFCommandObject firstChildCmdObject;
    
    private List<CFCommandObject> siblingCmdObjectList;

    public CFCommandObject() {
       
    }
    
    private DbClient dbClient;
    
    public void execute(Operations operation) {
        switch (operation) {
        case INVENTORY_DELETE:
            try {
                //Delete the child object, and clear all references from it's  parent
                //e.g. Delete the snap object, and remove the snap reference from it's parent volume.
                

            } catch (Exception e) {

            }
            break;
        case VALIDATE:
            try {

            } catch (Exception e) {

            }
            break;
        default:
            throw new IllegalArgumentException("Invalid command");
        }
    }
    
   

    public DbClient getDbClient() {
        return dbClient;
    }

    public void setDbClient(DbClient dbClient) {
        this.dbClient = dbClient;
    }

    public CFCommandObject getLeftCmdObject() {
        return leftCmdObject;
    }

    public void setLeftCmdObject(CFCommandObject leftCmdObject) {
        this.leftCmdObject = leftCmdObject;
        if (this.leftCmdObject != null) {
            strBuffer.append(":: Left Node ::");
            strBuffer.append(this.leftCmdObject.toString());
        }
    }

    public CFCommandObject getRightCmdObject() {
        return rightCmdObject;
    }

    public void setRightCmdObject(CFCommandObject rightCmdObject) {
        this.rightCmdObject = rightCmdObject;
        if (this.rightCmdObject != null) {
            strBuffer.append(":: right Node ::");
            strBuffer.append(this.rightCmdObject.toString());
        }
    }

    public URI getObjectURI() {
        return objectURI;
    }

    public void setObjectURI(URI objectURI) {
        this.objectURI = objectURI;
        if (this.objectURI != null) {
            strBuffer.append("Value ::");
            strBuffer.append(objectURI.toString());
        }
    }

    public CFCommandObject getParent() {
        return parent;
    }

    public void setParent(CFCommandObject parent) {
        this.parent = parent;
        if (this.parent != null) {
            strBuffer.append(":: Parent ::");
            strBuffer.append(this.parent.toString());
        }
    }
    @Override
    public String toString() {
        return strBuffer.toString();
        
    }



    public CFCommandObject getFirstChildCmdObject() {
        return firstChildCmdObject;
    }



    public void setFirstChildCmdObject(CFCommandObject firstChildCmdObject) {
        this.firstChildCmdObject = firstChildCmdObject;
    }





    public List<CFCommandObject> getSiblingCmdObjectList() {
        if (siblingCmdObjectList == null) {
            siblingCmdObjectList = new ArrayList<CFCommandObject>();
        }
        return siblingCmdObjectList;
    }



    public void setSiblingCmdObjectList(List<CFCommandObject> siblingCmdObjectList) {
        this.siblingCmdObjectList = siblingCmdObjectList;
    }
    
}
