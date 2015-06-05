#!/bin/sh

# Copyright 2015 EMC Corporation
# All Rights Reserved

MODELS_DIR=../../../internalLibraries/models
CLIENT_DIR=..
TARGET=../../../../controller-client-java


# Remove old content
rm -rf ${TARGET}/client/src/main/java
rm -rf ${TARGET}/models/src/main/java

# Add new content
mkdir -p ${TARGET}/client/src/main/java
mkdir -p ${TARGET}/models/src/main/java

cp -R ${CLIENT_DIR}/src/java/* ${TARGET}/client/src/main/java/
cp -R ${MODELS_DIR}/src/main/java/* ${TARGET}/models/src/main/java/

