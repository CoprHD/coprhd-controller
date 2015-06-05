#!/bin/bash

# Copyright 2015 EMC Corporation
# All Rights Reserved

KEYTOOL=$JAVA_HOME/bin/keytool
echo Generating the Server KeyStore in file server.keystore
$KEYTOOL -genkey -alias tomcat-sv -dname "CN=localhost, OU=X, O=Y, L=Z, S=XY, C=YZ" -keyalg RSA -keypass changeit -storepass changeit -keystore server.keystore

echo Exporting the certificate from keystore to an external file server.cer
$KEYTOOL -export -alias tomcat-sv -storepass changeit -file server.cer -keystore server.keystore

echo Generating the Client KeyStore in file client.keystore
$KEYTOOL -genkey -alias tomcat-cl -dname "CN=Client, OU=X, O=Y, L=Z, S=XY, C=YZ" -keyalg RSA -keypass changeit -storepass changeit -keystore client.keystore

echo Exporting the certificate from keystore to external file client.cer
$KEYTOOL -export -alias tomcat-cl -storepass changeit -file client.cer -keystore client.keystore

echo Importing Client's certificate into Server's keystore
$KEYTOOL -import -v -trustcacerts -alias tomcat -file server.cer -keystore client.keystore -keypass changeit -storepass changeit

echo Importing Server's certificate into Client's keystore
$KEYTOOL -import -v -trustcacerts -alias tomcat -file client.cer -keystore server.keystore -keypass changeit -storepass changeit
