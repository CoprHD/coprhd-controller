package com.emc.storageos.systemservices.impl.util;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.StorageDriverFile;

/**
 * Class for test to store driver jar file in Cassandra
 * 
 * @author caos1
 *
 */
public class DriverUtil {
    private DbClient dbClient;

    public DriverUtil(DbClient dbClient) {
        this.dbClient = dbClient;
    }

    public void storeDriver(String filePath) throws IOException {
        File driver = new File(filePath);
        InputStream fis = new FileInputStream(driver);
        byte[] buffer = new byte[0x10000];
        int readBytes;
        int number = 0;
        while ((readBytes = fis.read(buffer)) != -1) {
            StorageDriverFile chunk = new StorageDriverFile();
            byte[] dest = new byte[readBytes];
            System.arraycopy(buffer, 0, dest, 0, readBytes);
            chunk.setChunk(buffer);
            chunk.setDriverName(filePath);
            chunk.setNumber(number ++);
            dbClient.createObject(chunk);
        }
        fis.close();
    }
    public InputStream getFile(String driverName) {
        List<URI> ids = dbClient.queryByType(StorageDriverFile.class, true);

        Set<StorageDriverFile> file = new TreeSet<>((c1, c2) -> {
            return c1.getNumber() - c2.getNumber();
        });

        int size = 0;
        Iterator<StorageDriverFile> iter = dbClient.queryIterativeObjects(StorageDriverFile.class, ids);
        while (iter.hasNext()) {
            StorageDriverFile chunk = iter.next();
            if (!driverName.equals(chunk.getDriverName())) {
                continue;
            }
            file.add(chunk);
            size += chunk.getChunk().length;
        }
        byte[] buffer = new byte[size];
        int offset = 0;
        for (StorageDriverFile chunk : file) {
            byte[] src = chunk.getChunk();
            System.arraycopy(src, 0, buffer, offset, src.length);
            offset += src.length;
        }
        return new ByteArrayInputStream(buffer);
    }
}
