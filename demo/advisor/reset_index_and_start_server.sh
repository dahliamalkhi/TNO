#!/bin/bash
(set -o igncr) 2>/dev/null && set -o igncr; # this comment is required. this should be 2nd line of script.
# Above line makes this script ignore Windows line endings, to avoid this error without having to run dos2unix: 
#    $'\r': command not found
# As per http://stackoverflow.com/questions/14598753/running-bash-script-in-cygwin-on-windows-7

# Configure environment variables etc
source ../../scripts/env.sh

usage() {
  echo Usage: reset_index_and_start_server.sh \<TNO\|vanilla\>
}

if [ "$#" -ne 1 ]; then
  usage
  exit 1
fi

SOLR_VARIANT=$1
if [ "${SOLR_VARIANT}" != "TNO" ] && [ "${SOLR_VARIANT}" != "vanilla" ]; then
  echo Unknown Solr variant ${SOLR_VARIANT}
  usage
  exit 1
fi

./reset_index.sh

./start_server.sh ${SOLR_VARIANT}
