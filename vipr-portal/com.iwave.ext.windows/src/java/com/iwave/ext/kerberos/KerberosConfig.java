/**
* Copyright 2012-2015 iWave Software LLC
* All Rights Reserved
 */
package com.iwave.ext.kerberos;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.zip.CRC32;
import java.util.zip.CheckedInputStream;
import java.util.zip.Checksum;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.io.output.NullOutputStream;
import org.apache.log4j.Logger;

/**
 * The kerberos config krb5File (krb5.conf) contains all of the configuration for
 * kerberos realms. It would be great if you could programmatically set the
 * realms before access but you cannot. Everything is set through system properties
 * and in the krb5.conf krb5File.
 * 
 * http://docs.oracle.com/javase/1.4.2/docs/guide/security/jgss/tutorials/KerberosReq.html
 * 
 * This class provides a wrapper around this mechanism to allow the application
 * to provide a dynamic list of realms. These are then written to the krb5.conf
 * krb5File which is set up for Kerberos.
 * 
 * @author Chris Dail
 */
public class KerberosConfig {
    private static final Logger LOG = Logger.getLogger(KerberosConfig.class);

    public static final String FILE_PREFIX = "krb5";
    public static final String FILE_SUFFIX = ".conf.tmp";
    private static KerberosConfig instance = new KerberosConfig();
    public static KerberosConfig getInstance() {
        return instance;
    }
    
    private File krb5File;
    private long checksum = 0;
    
    private KerberosConfig() {}
    
    public synchronized void initialize(String krb5Conf) throws IOException {
        if (krb5Conf == null) {
            return;
        }
        
        if (krb5File == null) {
            krb5File = File.createTempFile(FILE_PREFIX, FILE_SUFFIX);
            krb5File.deleteOnExit();

            FileUtils.writeStringToFile(krb5File, krb5Conf);
            System.setProperty("java.security.krb5.conf", krb5File.getAbsolutePath());

            LOG.debug("Kerberos KRB5 configuration written to " + krb5File.getAbsolutePath());
            checksum = FileUtils.checksumCRC32(krb5File);
            return;
        }
        
        long newChecksum = checksumCRC32(krb5Conf);
        // There is an existing krb5File, check the checksum is different
        if (newChecksum != checksum) {
            FileUtils.writeStringToFile(krb5File, krb5Conf);
            LOG.debug("Updated Kerberos KRB5 configuration");

            checksum = newChecksum;
        }
    }
    
    public File getKrb5File() {
        return krb5File;
    }
    
    private long checksumCRC32(String string) throws IOException {
        Checksum sum = new CRC32();
        InputStream in = null;
        try {
            in = new CheckedInputStream(new ByteArrayInputStream(string.getBytes()), sum);
            IOUtils.copy(in, new NullOutputStream());
        }
        finally {
            IOUtils.closeQuietly(in);
        }
        return sum.getValue();
    }
}
