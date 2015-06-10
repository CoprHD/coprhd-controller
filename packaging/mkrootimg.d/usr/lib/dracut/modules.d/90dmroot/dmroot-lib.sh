#!/bin/sh

##
## Common routines
##

# Load dracut functions
type getarg >/dev/null 2>&1 || . /lib/dracut-lib.sh

# Parse bootfs_dev and cow_dev arguments from root
#
# Preferred format:
#       root=dmroot:[:bootfs_dev][:cow_dev]
#
# Example: root=dmroot:/dev/sda1
#          root=dmroot:/dev/sda1:initramfs
#          root=dmroot:/dev/sda1:/dev/sdb1
#

dmroot_to_var() {
    local v=${1}:
    set --
    while [ -n "$v" ]; do
        set -- "$@" "${v%%:*}"
        v=${v#*:}
    done

    unset bootfs_dev cow_dev
    bootfs_dev=$2; cow_dev=$3;
}

