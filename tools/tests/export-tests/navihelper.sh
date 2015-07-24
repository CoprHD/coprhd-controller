#!/bin/sh
#
# Copyright (c) 2015 EMC Corporation
# All Rights Reserved
#
# 
# Quick verification script
#
# Usage: ./navihelper.sh <ServiceProcessor IP> <Initiator Pattern> <NAME_PATTERN> <NUMBER_OF_INITIATORS_EXPECTED> <NUMBER_OF_LUNS_EXPECTED>
#
# set -x

VNX_SP_IP=$1
INITIATOR_PATTERN=$2

shift
shift

SG_PATTERN=$1
NUM_INITIATORS=$2
NUM_LUNS=$3

/opt/Navisphere/bin/naviseccli -User bourne -Password bourne -Scope 0 -Address $VNX_SP_IP storagegroup -list > /tmp/verify.txt

grep -n ${SG_PATTERN} /tmp/verify.txt > /dev/null
if [ $? -ne 0 ]
then
   if [ "$2" = "gone" ]
   then
      echo "PASSED: Verified storage group with pattern ${SG_PATTERN} doesn't exist."
      exit 0;
   fi
   echo "ERROR: Expected storage group ${SG_PATTERN}, but could not find it";
   exit 1;
else
   if [ "$2" = "gone" ]
   then
      echo "ERROR: Expected storage group ${SG_PATTERN} to be gone, but it was found"
      exit;
   fi
fi

hits=`grep -n ${SG_PATTERN} /tmp/verify.txt | wc -l`
if [ ${hits} -gt 1 ]
then
  echo "ERROR: Expected storage group ${SG_PATTERN}, but found more than one that fit that pattern!"
  exit 1;
fi

sgline=`grep -n ${SG_PATTERN} /tmp/verify.txt | awk -F: '{print $1}'`

sgblocksize=`tail -n +${sgline} /tmp/verify.txt | grep -n Shareable | head -1 | awk -F: '{print $1}'`
sgblocksize=`expr ${sgblocksize} + 1`
sgblockstart=`expr ${sgline} + ${sgblocksize} - 1`
head -${sgblockstart} /tmp/verify.txt | tail -${sgblocksize} > /tmp/verify2.txt
num_inits=`grep "$INITIATOR_PATTERN" /tmp/verify2.txt | awk '{print $1}' | sort -u | wc -l | awk '{print $1}'`
num_luns=`egrep "[0-9].              .*[0-9].*" /tmp/verify2.txt | wc -l`
failed=false

if [ ${num_inits} -ne ${NUM_INITIATORS} ]
then
   echo "ERROR: Export group initiators: Expected: ${NUM_INITIATORS}, Retrieved: ${num_inits}";
   echo "Export group dump:"
   cat /tmp/verify2.txt
   failed=true
fi

if [ ${num_luns} -ne ${NUM_LUNS} ]
then
   echo "ERROR: Export group luns: Expected: ${NUM_LUNS}, Retrieved: ${num_luns}";
   echo "Export group dump:"
   cat /tmp/verify2.txt
   failed=true
fi

if [ "${failed}" = "true" ]
then
   exit 1;
fi

echo "PASSED: Verified storage group starting with pattern $1 contained $2 initiators and $3 luns"
exit 0;
