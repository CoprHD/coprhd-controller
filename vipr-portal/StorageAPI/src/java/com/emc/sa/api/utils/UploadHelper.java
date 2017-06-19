/*
 * Copyright 2017 Dell Inc. or its subsidiaries.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */
package com.emc.sa.api.utils;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;

import javax.servlet.http.HttpServletRequest;

import com.emc.storageos.svcs.errorhandling.resources.InternalServerErrorException;

public final class UploadHelper {

    private UploadHelper() {}
    
    public static byte[] read(final HttpServletRequest request) {
        try(final ByteArrayOutputStream file = new ByteArrayOutputStream()) {
            final byte buffer[] = new byte[2048];
            final InputStream in = request.getInputStream();
            int nRead = 0;
            while ((nRead = in.read(buffer)) > 0) {
                file.write(buffer, 0, nRead);
            }
            return file.toByteArray();
        } catch (final IOException e) {
            throw InternalServerErrorException.internalServerErrors.genericApisvcError("failed to read octet stream", e);
        }
    }
    
    public static final byte[] read(final int length, final DataInputStream dis) throws IOException {
        byte[] bytes = new byte[length];
        int offset = 0;
        while( offset < length) {
            int nRead = dis.read(bytes, offset, length - offset);
            if( nRead <= 0 ) {
                throw new IOException("unexpected end of stream");
            }
            offset += nRead;
        }
        return bytes;
    }
}
