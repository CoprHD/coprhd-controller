#
# Copyright (c) 2015 EMC Corporation
# All Rights Reserved
#

export BOURNE_IPADDR=10.247.78.237
export BOURNE_DATA_IPADDR=10.247.78.239
SECURITY="--uid wuser1@SANITY.LOCAL --secret=U5/1LIqgWRhG+LXArN0/xLFWleMk3nFNW1d1Vdsv"


./security login root ChangeMe

#Testcase 1
./bucket create s3 mybuck1 $SECURITY 
for i in {1..10}
do
	./bucketkey create s3 mybuck1 key$i value$i $SECURITY
done

#Testcase 2
#for num in {3..4} do
#	./bucket create s3 mybuck$num --proj proj$num $SECURITY 
#	for i in {1..10}
#	do
#		./bucketkey create s3 mybuck$num key$num$i value$num$i $SECURITY
#	done
#done

#Testcase 3
#for num in {5..6} do
#	./bucket create s3 mybuck$num --rg rg$num $SECURITY 
#	for i in {1..10}
#	do
#		./bucketkey create s3 mybuck$num key$num$i value$num$i $SECURITY
#	done
#done
