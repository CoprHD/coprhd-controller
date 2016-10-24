#!/bin/bash -x

# This script is used to build the "Davenport" Docker images.
# Before run this script, you need to build davenport first.

IMAGE_NAME='niqi/davenport'
BUILD_DIR='../build'
DOCKERFILE_DIR='.'

# Update the Dockerfile.
rm $BUILD_DIR/Dockerfile
cp $DOCKERFILE_DIR/Dockerfile $BUILD_DIR/Dockerfile

# Remove the existing docker image.
docker rmi $IMAGE_NAME

# Build the
cd $BUILD_DIR
docker build -t $IMAGE_NAME .
cd ../dockerize/
echo 'Build done.'