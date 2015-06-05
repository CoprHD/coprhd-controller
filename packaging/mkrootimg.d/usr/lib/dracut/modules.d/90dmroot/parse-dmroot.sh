#!/bin/sh

# Copyright 2015 EMC Corporation
# All Rights Reserved

## Dracut cmdline hook to parse kernel command line. It set rootok=1
## if kernel cmdline is legal

. /lib/dmroot-lib.sh

# This script is sourced, so root should be set. But let's be paranoid
[ -z "$root" ] && root=$(getarg root=)

devinfo=${root#dmroot:}

if [ -n ${devinfo} ]; then
  # Check required arguments. See dmroot-lib for argument format
  dmroot_to_var ${root}
  [ -z "${bootfs_dev}" ] && echo "Argument bootfs_dev for dmroot is missing"

  # Tell dracut we are ok
  rootok=1
fi

