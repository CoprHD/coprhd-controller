#!/bin/bash
#
# Copyright (c) 2015 EMC Corporation
# All Rights Reserved
#
# This software contains the intellectual property of EMC Corporation
# or is licensed to EMC Corporation from third parties.  Use of this
# software and the intellectual property contained therein is expressly
# limited to the terms and conditions of the License Agreement under which
# it is provided by or on behalf of EMC.
#
# To generate disk4 (streamOptimzed) of 1M size
# A disk4 (monothicFlat) with 1.02M size needs to be generated
# so we need to append some random bytes to the ISO file to expend it to that size

_usage() {
    echo "Usage: $0 iso-header-file iso-trailer-file flat-disk4-size"
    exit 2 
}

if [ $# -ne 3 ] ; then
    _usage
fi

header=$1
trailer=$2
flat_disk4_size=$3
header_size=$(stat -c %s ${header})
trailer_size=$(stat -c %s ${trailer})
(( padding_size = flat_disk4_size - header_size - trailer_size ))
dd if=/dev/urandom of=${trailer} ibs=1 count=${padding_size} conv=notrunc oflag=append
