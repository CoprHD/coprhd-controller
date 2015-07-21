/**
 *  Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */

package com.emc.storageos.security.validator;


import com.emc.storageos.db.client.model.AbstractChangeTrackingSet;
import com.emc.storageos.db.client.model.TenantOrg;
import com.emc.storageos.model.tenant.TenantOrgRestRep;
import com.emc.storageos.security.authorization.BasePermissionsHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;
import javax.xml.bind.Unmarshaller;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.List;

public class MarshallUtil {

    private static Logger log = LoggerFactory.getLogger(MarshallUtil.class);

    /**
     * leveraging TenantOrgRestRep's JAXB representation, converts given tenant's user-mapping to a string.
     *
     * @param tenant
     * @return
     * @throws Exception
     */
    public static String ConvertTenantUserMappingToString(TenantOrg tenant) throws Exception {
        TenantOrgRestRep response = new TenantOrgRestRep();

        if(tenant.getUserMappings() != null) {
            for(AbstractChangeTrackingSet<String> userMappingSet: tenant.getUserMappings().values()) {
                for(String existingMapping : userMappingSet ) {
                    response.getUserMappings().add(BasePermissionsHelper.UserMapping.toParam(
                            BasePermissionsHelper.UserMapping.fromString(existingMapping)));
                }
            }
        }

        StringWriter writer = new StringWriter();
        JAXBContext jaxbContext = JAXBContext.newInstance(TenantOrgRestRep.class);
        Marshaller jaxbMarshaller = jaxbContext.createMarshaller();
        jaxbMarshaller.marshal(response, writer);
        return writer.toString();
    }


    /**
     * leveraging TenantOrgRestRep's JAXB representation, converting its string to List of UserMapping object.
     *
     * @param strUserMappings
     * @return
     */
    public static List<BasePermissionsHelper.UserMapping> convertStringToUserMappingList(String strUserMappings) {

        List<BasePermissionsHelper.UserMapping> userMappingList = null;
        try {
            JAXBContext jaxbContext = JAXBContext.newInstance(TenantOrgRestRep.class);
            Unmarshaller unmarshaller = jaxbContext.createUnmarshaller();
            TenantOrgRestRep response = (TenantOrgRestRep) unmarshaller.unmarshal(new StringReader(strUserMappings));
            userMappingList = BasePermissionsHelper.UserMapping.fromParamList(response.getUserMappings());
        } catch (JAXBException e) {
            log.error("An error occurred when converting string {} to list. Cause: {}", strUserMappings, e);
        }

        return  userMappingList;
    }
}
