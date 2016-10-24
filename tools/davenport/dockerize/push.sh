#!/bin/bash -x

# This script is used to push the Docker image to the private registry server.
# Be default it use a version number to tag the current image version. The
# version number is "$MAJOR_VERSION.$MINOR_VERSION.$DATE".
IMAGE_NAME='niqi/davenport'
REGISTRY_SERVER='10.32.72.130:5000'
MAJOR_VERSION='0'
MINOR_VERSION='1'
DATE=$(date "+%Y-%m-%d")
VERSION_NUMBER=$MAJOR_VERSION.$MINOR_VERSION.$DATE

# Tag the image with version number(version = $major.$minor.$date).
docker rmi $REGISTRY_SERVER/$IMAGE_NAME:$VERSION_NUMBER
docker rmi $REGISTRY_SERVER/$IMAGE_NAME:latest
docker tag $IMAGE_NAME $REGISTRY_SERVER/$IMAGE_NAME:$VERSION_NUMBER
docker push $REGISTRY_SERVER/$IMAGE_NAME:$VERSION_NUMBER
# Also update the "latest" image.
docker tag $IMAGE_NAME $REGISTRY_SERVER/$IMAGE_NAME
docker push $REGISTRY_SERVER/$IMAGE_NAME
