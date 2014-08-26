#!/bin/bash
(set -o igncr) 2>/dev/null && set -o igncr; # this comment is required. this should be 2nd line of script.
# Above line makes this script ignore Windows line endings, to avoid this error without having to run dos2unix: 
#    $'\r': command not found
# As per http://stackoverflow.com/questions/14598753/running-bash-script-in-cygwin-on-windows-7

# Configure environment variables etc
source ./scripts/env.sh

ANT_BIN_PATH=`pwd`/dependencies/apache-ant-1.8.2/bin
if [[ "${PATH}" != "*apache-ant-1.8.2*" ]]; then
  #echo "  ** Adding ant bin dir to PATH **"
  export PATH=${ANT_BIN_PATH}:${PATH}
fi

pushd solr-4.6.1-tno/
ant ivy-bootstrap
cd solr
ant dist
popd
