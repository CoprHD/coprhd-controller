#!/bin/sh

IQN=$1
ARRAY_HOST=$2
FILE=/tmp/tmp.txt
EXPORTED_MOUNT_POINT=$3
CONTAINER_MOUNT_POINT=$4
CONTAINER_IMAGE_NAME=$5
CONTAINER_NAME=$6

echo "Trying to discover volumes in $ARRAY_HOST under $IQN"
echo $FILE
echo "Will mount device on $EXPORTED_MOUNT_POINT"
echo "Will Start docker $CONTAINER_NAME - [image: $CONTAINER_IMAGE_NAME, mount pt: $CONTAINER_MOUNT_POINT]"
#discover the volumes
iscsiadm --mode node --targetname $IQN --portal $ARRAY_HOST:3260 --login

#restart scsi daemon
/etc/init.d/open-iscsi restart
sleep 15

# find the new device added
rm -f $FILE
fdisk -l 2>&1 | grep "doesn't contain a valid partition table" > $FILE

if [ -s $FILE ]; #if file is not empty
then
  my_device=`head -1 $FILE |awk '{print $2}'`
  echo "Device $my_device has been added"
else
  echo "No devices without partition found"
  exit
fi;

#create partition, file-system and mount it
(echo o; echo n; echo p; echo 1; echo ; echo; echo w) |  fdisk $my_device
mkfs.ext3 $my_device"1"
mkdir $EXPORTED_MOUNT_POINT
mount $my_device"1" $EXPORTED_MOUNT_POINT

#start docker with this volume
docker run -i -t -d --volume-driver flocker -v  $EXPORTED_MOUNT_POINT:$CONTAINER_MOUNT_POINT --name=$CONTAINER_NAME $CONTAINER_IMAGE_NAME bash

#test if docker started
docker exec $CONTAINER_NAME df -h
