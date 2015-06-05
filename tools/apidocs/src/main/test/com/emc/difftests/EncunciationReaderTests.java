package com.emc.difftests;

import com.emc.apidocs.differencing.EnunciateFileReader;
import com.emc.apidocs.model.ApiMethod;
import com.emc.apidocs.model.ApiService;

import java.io.InputStream;
import java.util.Map;

public class EncunciationReaderTests {

    public static void main(String[] args) throws Exception {

        InputStream enunciateStream = EncunciationReaderTests.class.getResourceAsStream("apisvc-1.1.xml");

        EnunciateFileReader reader = new EnunciateFileReader();
        Map<String, ApiService> services = reader.loadServices(enunciateStream);

        for (ApiService service : services.values()) {
            System.out.println(service.getFqJavaClassName());

            for (ApiMethod method : service.methods) {
                System.out.println("== "+method.httpMethod+" "+method.path);
                if (method.input != null) {
                    System.out.println("==---  IN : "+method.input.name);
                }
                if (method.output != null) {
                    System.out.println("==---  OUT : "+method.output.name);
                }

            }
        }
    }
}
