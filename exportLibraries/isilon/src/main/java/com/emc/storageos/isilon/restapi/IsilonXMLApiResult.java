
/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.isilon.restapi;

import org.w3c.dom.Document;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.StringReader;

public class IsilonXMLApiResult {
    private boolean commandSuccess = false;
    private String message = "";
    private String resultFeed = "";
    private Object obj = null;

    //Error Handling
    private static final String ERROR = "error";
    private static final String READY = "ready";
    private static final String TASKRESPONSE = "TaskResponse";
    private static final String FAULT = "Fault";
    private static final String PROBLEM = "Problem";
    private static final String MESSAGE = "message";
    private static final String DESCRIPTION = "Description";

    public IsilonXMLApiResult(){}

    public IsilonXMLApiResult(String resultFeed) {
        this.resultFeed = resultFeed;
    }

    public boolean isCommandSuccess() {
        return commandSuccess;
    }

    public void setCommandSuccess(){
        commandSuccess = true;
    }

    public void setCommandFailed(){
        commandSuccess = false;
    }
    public String getMessage(){
        return message;
    }

    public void setMessage(String message){
        this.message = message;
    }

    public String getResultFeed(){
        return resultFeed;
    }

    public void setResultFeed(String resultFeed){
        this.resultFeed = resultFeed;
    }

    public void setObject(Object obj){
        this.obj = obj;
    }

    public Object getObject(){
        return obj;
    }
    
    public static IsilonXMLApiResult parseResult(String resultXML){
        IsilonXMLApiResult result = new IsilonXMLApiResult(resultXML);
        String cmdMessage = "";
        String errorMsg = "";
        try {
            DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
            DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
            InputSource source = new InputSource();
            source.setCharacterStream(new StringReader(resultXML));
            Document doc = dBuilder.parse(source);
            NodeList list = doc.getElementsByTagName(PROBLEM);
            if(list.getLength() > 0) {
                //Handle Error response
                Node probNode = list.item(0);
                if (PROBLEM.equals(probNode.getNodeName())) {
                    NamedNodeMap attributes = probNode.getAttributes();
                    Node errorMsgNode = attributes.getNamedItem(MESSAGE);
                    errorMsg = errorMsgNode.getNodeValue();
                    NodeList probChildren = probNode.getChildNodes();
                    for(int j=0; j < probChildren.getLength(); j++){
                        Node probChild = probChildren.item(j);

                        if( !DESCRIPTION.equals(probChild.getNodeName()))continue;
                        if (probChild.getFirstChild() != null) {
                            errorMsg = errorMsg + ":" + probChild.getFirstChild().getNodeValue();
                        }
                    }
                }
                result.setCommandFailed();
                result.setMessage(errorMsg);
            } else {
                //Handle success response
                result.setCommandSuccess();
            }
        } catch (Exception e) {
            result.setCommandFailed();
            result.setMessage(e.getMessage() + " : " + errorMsg);
        }

        return result;
    }

    public static IsilonXMLApiResult parseErrorResult(String errorMsg){
        IsilonXMLApiResult result = new IsilonXMLApiResult();
        result.setCommandFailed();
        result.setMessage(errorMsg);
        return result;
    }
}
