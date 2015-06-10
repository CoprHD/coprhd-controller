#!//bin/bash
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
#  getvmdkinfo.sh
#
# Get VMDK parameters for disk1, disk2 and disk3
# - Ultimately, we are going to extract the parameters from the streamOptimized VMDKs
#   directly. This, however, needs more work. For now we get disk1 from the OVF geenrated
#   by the ovftool, and disk2/disk3 parameters are hardcoded.
#

_usage() {
    echo "Usage: $0 diskN ovf-file" >&2
    exit 2
}

[[ ${#} = 2            ]] || _usage
[[ "${1}" =~ disk[1-9] ]] || _usage
    
cat ${2} | sed -n "
/ovf:diskId=\"vm${1}\"/ s/.*capacity=\"\([1-9][0-9]*\)\".*populatedSize=\"\([1-9][0-9]*\)\".*/${1}_capacity=\"\1\"\n${1}_populated_size=\"\2\"/p
/ovf:href=\".*-${1}.vmdk\"/ s/.*size=\"\([1-9][0-9]*\)\".*/${1}_size=\"\1\"/p"

