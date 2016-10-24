#!/bin/bash -x
DAVENPORT_NAME='davenport'
TIME=$(date "+%Y%m%d")
DAVENPORT_VERSION="0.1-SNAPSHOT-$TIME"
DAVENPORT_FILE_NAME="$DAVENPORT_NAME-$DAVENPORT_VERSION.zip"

# 1. Clean existing build directory.
rm -rf build/
mkdir -p build/dist/

# 2. Add the davenport files.
cp -r davenport/ build/dist/
cp *.py build/dist/
cp README.md build/dist/
cp requirements.txt build/dist/

# 3. Create the zip file.
cd build/dist/
mkdir data/
mkdir logs/
zip -r ../${DAVENPORT_FILE_NAME} . --exclude \*.pyc
cd ../../

# 4. Upload the package file to the file server.
scp build/${DAVENPORT_FILE_NAME} root@10.247.98.161:/Bourne/${DAVENPORT_NAME}/

echo 'Done.'
