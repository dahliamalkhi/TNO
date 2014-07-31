#!/bin/bash
(set -o igncr) 2>/dev/null && set -o igncr; # this comment is required. this should be 2nd line of script.
# Above line makes this script ignore Windows line endings, to avoid this error without having to run dos2unix: 
#    $'\r': command not found
# As per http://stackoverflow.com/questions/14598753/running-bash-script-in-cygwin-on-windows-7

# Configure environment variables etc
source ../../scripts/env.sh

NUM_ITERATIONS=10000

#QUERY="*%3A*+Computer"
QUERY="MG%3A%228347a770-14b0-400e-8881-3a5771d49f89%22%0A"
#QUERY="*"

export KEYS_STRING=""
# Currently only one (optional) argument:
# name of field to include key string for.
# TODO: Upgrade this to handle a list of field names, and get the values
# from SecureCipherUtil.Keys.txt
if [ "$#" -eq 1 ]; then
  FIELD_NAME="$1"
  if [ "${FIELD_NAME}" == "MG" ]; then
    KEYS_STRING="&Keys=MG:A4E45BF7AF38387D1418D44F96DF582B" 
  else
    echo No TNO key entry found for field named ${FIELD_NAME}. Exiting.
	exit 1
  fi
fi
echo KEYS_STRING = ${KEYS_STRING}

# # Curl output goes into ./query_output
# # Force the user to manage the contents.
# if [ -e "./query_output" ]; then
# 	echo "Directory query_output already exists. Exiting..." 1>&2
# 	exit 1
# fi
rm -rf ./query_output
mkdir ./query_output

ITERATION=1
while [[ ${ITERATION} -le ${NUM_ITERATIONS} ]]; do
  TIMESTAMP=`date`
  
    curl --write-out "Stats: ${ITERATION} at ${TIMESTAMP} : Curl Statuscode %{http_code} Lookup %{time_namelookup} Connect %{time_connect} Pretransfer %{time_pretransfer} Starttransfer %{time_starttransfer} Total %{time_total} sec\n" -o query_output/stats.${ITERATION}.txt "http://${SOLR_SERVER_HOSTNAME}${SOLR_SERVER_PORT}/solr/collection1/admin/mbeans?stats=true"
        
    curl --write-out "Query: ${ITERATION} at ${TIMESTAMP} : Curl Statuscode %{http_code} Lookup %{time_namelookup} Connect %{time_connect} Pretransfer %{time_pretransfer} Starttransfer %{time_starttransfer} Total %{time_total} sec\n" -o query_output/query.${ITERATION}.txt "http://${SOLR_SERVER_HOSTNAME}${SOLR_SERVER_PORT}/solr/collection1/select?q=${QUERY}&wt=json&indent=true&facet=true&facet.field=ObjectFullName&facet.field=MG&facet.field=WorkflowName&rows=0&facet.mincount=1&facet.date=TimeGenerated&facet.date.start=2013-03-10T00:00:00Z&facet.date.end=2014-12-17T00:00:00Z&facet.date.gap=%2B1HOUR${KEYS_STRING}"
    
  sleep 1

  let ITERATION=ITERATION+1 
done
