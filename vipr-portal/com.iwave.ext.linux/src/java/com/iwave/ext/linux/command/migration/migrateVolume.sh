#!/bin/bash

in="%s";
out="%s";

dd if=$in of=$out &
ddpid="$!"
echo "ddpid = " $ddpid

exit $ddpid
