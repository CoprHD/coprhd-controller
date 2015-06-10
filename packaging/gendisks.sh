#!/bin/bash -ex
#
# Copyright (c) 2012 EMC Corporation
# All Rights Reserved
#
# This software contains the intellectual property of EMC Corporation
# or is licensed to EMC Corporation from third parties.  Use of this
# software and the intellectual property contained therein is expressly
# limited to the terms and conditions of the License Agreement under which
# it is provided by or on behalf of EMC.
#
# gendisks.sh
#
# A simple script to convert disks from the "mkdisks" OVF to the disk files in SVN.
#

_usage() { 
    echo "Usage: $0 [srcname]" >&2
    exit 2
}

_mkvmdk() {
    local disk=${2##*-}; disk=${disk%.*} 
    rm -f "${2}" "${2}.info"
    cp "${1}-${disk}.vmdk" "${2}"
    sed -n "
/ovf:diskId=\"vm${disk}\"/ s/.*capacity=\"\([1-9][0-9]*\)\".*populatedSize=\"\([1-9][0-9]*\)\".*/${disk}_capacity=\"\1\"\n${disk}_populated_size=\"\2\"/p
/ovf:href=\".*-${disk}.vmdk\"/ s/.*size=\"\([1-9][0-9]*\)\".*/${disk}_size=\"\1\"/p" <"${1}.ovf" >"${2}.info"
}

_mkflat() {
    rm -f "${2}" "${2%.*}-flat.vmdk" "${2%.*}-flat.vmdk.bz2" "${2%%-*}.ovf" "${2%%-*}.vmx"
    sed '
        /ovf:id="file2"/,/ovf:id="file4"/d
        /ovf:diskId="vmdisk2"/,/ovf:diskId="vmdisk4"/d
        /<Property /,/<\/Property>/d
        /<EulaSection>/,/<\/EulaSection>/d
        /<Item>/ { h; :B ; N ; /.*vmdisk[234].*<\/Item>/d; s/VirtualSCSI/lsilogic/; /<\/Item>/b; b B; }
' <"${1}.ovf" >"${2%%-*}.ovf"
    ovftool -dm=monolithicFlat "${2%%-*}.ovf" "${2%%-*}.vmx"
    bzip2 -9 "${2%.*}-flat.vmdk"
}


srcname=${1:-'BourneDevKit-1.0.0.0.9-mkdisk'}

_mkflat "${srcname}" storageos-disk1.vmdk
_mkvmdk "${srcname}" storageos-disk2.vmdk
_mkvmdk "${srcname}" storageos-disk3.vmdk 
_mkvmdk "${srcname}" storageos-disk4.vmdk 
_mkvmdk "${srcname}" storageos-disk5.vmdk 
