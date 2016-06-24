#!/bin/bash
ostype=`uname`

ddpid="%s";
in="%s";
out="%s";
    
if [ -f "$in" ]; then
  totalbytes=`du -D -k "$in" | tr ' ' '\t' | cut -f 1`
  totalbytes=`expr $totalbytes \* 1024` # busybox's "du" mode doesn't support -b
else
  totalbytes=`blockdev --getsize64 "$in" | tr ' ' '\t'`
fi

if [ -f "$out" ]; then
  bytescopied=`du -D -k "$out" | tr ' ' '\t' | cut -f 1`
  bytescopied=`expr $bytescopied \* 1024` # busybox's "du" mode doesn't support -b
else
  bytescopied=`blockdev --getsize64 "$out" | tr ' ' '\t'`
fi
    
dd_is_running() {
  if [ "$ostype" = "Linux" ] && mount | grep /proc >/dev/null; then
    if [ "$ddpid" != "" ] && \
      [ -d /proc/${ddpid} ] && \
      cat /proc/${ddpid}/cmdline 2>/dev/null | grep "dd" >/dev/null; then
      return 0
    else
      return 1
    fi
  else # fallback to using ps
    if [ "$ddpid" != "" ] && \
      ps xau | grep " $ddpid " | grep "dd" >/dev/null; then
      return 0
    else
      return 1
    fi
  fi
}

print_percent() {
  percent=`expr $bytescopied \* 100 / $totalbytes`
  percent=`echo -n " $percent" | tail -c 3`
  echo -e -n "$percent"
}
    
dd_is_running && {
    print_percent
    exit
}

if [ $bytescopied == $totalbytes ]; then
    # The migration is complete, the process has exited
    # and all bytes have been copied.
    echo -e -n "100"
else
    # Something went wrong, the process exited but the
    # migration is unfinished. Return -1 to indicate
    # an error
    echo -e -n "-1"
fi
