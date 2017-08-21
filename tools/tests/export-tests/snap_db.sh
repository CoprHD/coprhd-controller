snap_db() {
  session="$1"
  slot="$2"
  column_families="$3"
  escape_seq="$4"

  base_filter="| sed -r '/6[0]{29}[A-Z0-9]{2}=/s/\=-?[0-9][0-9]?[0-9]?/=XX/g' | sed -r 's/vdc1=-?[0-9][0-9]?[0-9]?/vdc1=XX/g' | grep -v \"status = OpStatusMap\" | grep -v \"lastDiscoveryRunTime = \" | grep -v \"allocatedCapacity = \" | grep -v \"capacity = \" | grep -v \"provisionedCapacity = \" | grep -v \"successDiscoveryTime = \" | grep -v \"storageDevice = URI: null\" | grep -v \"StringSet \[\]\" | grep -v \"varray = URI: null\" | grep -v \"Description:\" | grep -v \"Additional\" | grep -v -e '^$' | grep -v \"Rollback encountered problems\" | grep -v \"clustername = null\" | grep -v \"cluster = URI: null\" | grep -v \"vcenterDataCenter = \" | grep -v \"compositionType = \" | grep -v \"metaMemberCount = \" | grep -v \"metaMemberSize = \" $escape_seq"

  echo "snapping column families [session $session set $slot]: ${column_families}"
  mkdir -p results/${session}

  IFS=' ' read -ra cfs_array <<< "$column_families"
  for cf in "${cfs_array[@]}"; do
    execute="/opt/storageos/bin/dbutils list -sortByURI ${cf} $base_filter > results/${session}/${cf}-${slot}.txt"
    eval $execute
  done
}      

validate_db() {
    session=$1
    shift
    slot_1=${1}
    shift
    slot_2=${1}
    shift
    column_families=$*

    for cf in ${column_families}
    do
      diff results/${session}/${cf}-${slot_1}.txt results/${session}/${cf}-${slot_2}.txt
      if [ $? -ne 0 ]
      then
        exit 1
      fi
    done
}

if [ "$1" = "-validate" ]
then
  shift
  validate_db $1 $2 $3 $4
else
  snap_db "$1" "$2" "$3" "$4"
fi
