#!/bin/bash

# Copyright 2015 EMC Corporation
# All Rights Reserved

Usage() {
    echo "Usage: $0 vmdk-disk1 vmdk-disk2 ..." &>2
}

_generate_mf() {
    local mf_contents=""
    local disk_files=$*

    local i=0
    for file in $disk_files; do
        let i++
        dir_name=`dirname ${file}`
        base_name=`basename ${file}`
        sha1sum=`sha1sum ${file} | cut -d ' ' -f 1`
        mf_contents+="SHA1(\${vmdk_dir}/${base_name})=${sha1sum}\n"
    done
    echo ${mf_contents}
}

_generate_mf $*

exit 0
