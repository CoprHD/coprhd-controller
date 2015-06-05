#!/bin/bash

# Copyright 2015 EMC Corporation
# All Rights Reserved

# Create an RSA key:
openssl genrsa -aes128 -out vp.key 1024
# Remove the passphrase from the key:
cp vp.key vp.key.withpassphrase
openssl rsa -in vp.key.withpassphrase -out vp.key
# Create a certificate signing request:
openssl req -new -key vp.key -out vp.csr
# Generate a self signed certificate that does not have CA signing ability:
echo "basicConstraints=CA:FALSE" > vp.ext
openssl x509 -req -in vp.csr -days 90 -sha256 -signkey vp.key -extfile vp.ext -out vp.crt
# Display the new certificate with the extensions information for verification:
openssl x509 -text -noout -purpose -in vp.crt
