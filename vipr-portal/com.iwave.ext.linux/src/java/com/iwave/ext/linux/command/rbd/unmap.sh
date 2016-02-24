pool="%s";
vol="%s";
snap="%s";
log="/tmp/coprhd.$pool.$vol.$snap.log";

echo "Unmap device: $pool/$vol@$snap" > "$log";

# remove key and record in rdbmap (remove mappnig on reboot)
RBDMAP_FILE="/etc/ceph/rbdmap";
if [ -f "$RBDMAP_FILE" ]; then
  echo "Remove device from $RBDMAP_FILE to disable automap on reboot" >> "$log";
  grep -v "$pool/$vol@$snap" "$RBDMAP_FILE" > "$RBDMAP_FILE.tmp";
  mv -f "$RBDMAP_FILE.tmp" "$RBDMAP_FILE";
  keyfile="/etc/ceph/$pool.$vol.$snap.keyfile";
  key=$(grep -o "$keyfile" "$RBDMAP_FILE");
  if [ -z "$key" ]; then
    echo "Remove keyfile $keyfile" >> "$log";
    rm -f "$keyfile";
  fi;
fi;

# exit if already unmounted
if [[ "$snap" == "-" ]]; then
  v=$(readlink "/dev/rbd/$pool/$vol"); # pool/vol
else
  v=$(readlink "/dev/rbd/$pool/$vol@$snap"); # pool/vol@snap
fi;
id=$(echo "$v" | grep -o '[0-9]\\+');
if [ -z "$id" ]; then
  # find by name
  echo "Fallback for old drivers: find device number by name" >> "$log";
  volumes=( $(ls /sys/bus/rbd/devices/ | sort) );
  size=${#volumes[@]};
  index=0;
  while [  $index -lt $size ]; do
    v=${volumes[$index]};
    p=$(cat /sys/bus/rbd/devices/$v/pool);
    n=$(cat /sys/bus/rbd/devices/$v/name);
    s=$(cat /sys/bus/rbd/devices/$v/current_snap);
    echo "Check device: v=$v, p=$p, n=$n s=$s" >> "$log";
    if [[ "$p" == "$pool" && "$n" == "$vol" && "$s" == "$snap" ]]; then
      echo "Device found" >> "$log";
      id=$v;
      break;
    fi;
    let index=$index+1;
  done;
fi;

if [ -z "$id" ]; then
  echo Warning: Device not found >> "$log";
  cat $log >&2
  exit 0;
fi;

# do unmap
echo "Do unmap device $id" >> "$log";
echo "$id" 1>/sys/bus/rbd/remove 2>>"$log" || (cat $log >&2 && exit -1);
echo "Device unmapped" >> "$log";
