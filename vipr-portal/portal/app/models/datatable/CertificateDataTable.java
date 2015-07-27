/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package models.datatable;

import com.emc.storageos.security.keystore.impl.KeyCertificatePairGenerator;
import com.emc.vipr.model.keystore.TrustedCertificate;
import util.datatable.DataTable;

import javax.naming.InvalidNameException;
import javax.naming.ldap.LdapName;
import javax.naming.ldap.Rdn;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.text.SimpleDateFormat;
import java.util.HashMap;
import java.util.Map;

public class CertificateDataTable extends DataTable {

    public CertificateDataTable() {
        addColumn("commonName");
        addColumn("organization");
        addColumn("issuedBy");
        addColumn("notBefore");
        addColumn("notAfter");
        addColumn("userSupplied").setRenderFunction("render.boolean");
        addColumn("certificateValid").setRenderFunction("render.operationalStatus");
		sortAll();
        setDefaultSortField("commonName");
    }

    public static class CertificateInfo {
        public String id;
        public String notAfter;
        public String notBefore;
        public String certificateValid;
        public String commonName;
        public String organization;
        public String issuedBy;
        public String certificateInfo;
        public boolean userSupplied;


        public CertificateInfo(TrustedCertificate certInfo) throws CertificateException {
            String pem = certInfo.getCertString();

            X509Certificate cert = (X509Certificate) KeyCertificatePairGenerator.
                    getCertificateFromString(pem);
            Map<String, String> subjectDN = parseDN(cert.getSubjectX500Principal().getName());
            Map<String, String> issuerDN = parseDN(cert.getIssuerX500Principal().getName());

            id = pem;
            certificateInfo = cert.toString();
            notAfter = new SimpleDateFormat("YYYY-MM-dd").format(cert.getNotAfter());
            notBefore = new SimpleDateFormat("YYYY-MM-dd").format(cert.getNotBefore());
            organization = subjectDN.get("O");
            commonName = subjectDN.get("CN");
            issuedBy = issuerDN.get("CN");
            userSupplied = certInfo.getUserSupplied();

            try {
                cert.checkValidity();
                certificateValid = "OK";
            } catch (Exception e) {
                certificateValid = "NOT_OK";
            }
        }

        private Map<String, String> parseDN(String dn) {
            HashMap<String, String> parsed = new HashMap<String, String>();
            try {
                for(Rdn rdn : new LdapName(dn).getRdns()) {
                    parsed.put(rdn.getType(), rdn.getValue().toString());
                }
            } catch (InvalidNameException e) {
                throw new RuntimeException(e);
            }
            return parsed;
        }
    }
}
