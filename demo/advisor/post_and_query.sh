#!/bin/bash
(set -o igncr) 2>/dev/null && set -o igncr; # this comment is required. this should be 2nd line of script.
# Above line makes this script ignore Windows line endings, to avoid this error without having to run dos2unix: 
#    $'\r': command not found
# As per http://stackoverflow.com/questions/14598753/running-bash-script-in-cygwin-on-windows-7

# Configure environment variables etc
source ../../scripts/env.sh

#MAX_DOCS=5000
#DOCS_PER_ITERATION=1000

# 10 terms
#NUM_TERMS=10
# 10K terms
#NUM_TERMS=10000
# 1M terms
#NUM_TERMS=1000000
# 10M terms
NUM_TERMS=10000000

DOCS_TO_QUERY="1 10 100 1000 10000 100000 1000000 2000000 3000000 4000000 5000000 6000000 7000000 8000000 9000000 10000000"
#DOCS_TO_QUERY="10000 100000 1000000 2000000 3000000 4000000 5000000 6000000 7000000 8000000 9000000 10000000"
MAX_DOCS_PER_POST=100000

#QUERY="MG%3A%228347a770-14b0-400e-8881-3a5771d49f89%22%0A"
QUERY_PREFIX="ManagementGroupName%3A%22"
QUERY_SUFFIX="%22%0A"

export KEYS_STRING=""
KEYS_STRING="&Keys=ManagementGroupName:C621D985C092E8911E5441024988724B" 

#export FACET_STRING="&facet=true&facet.field=ObjectFullName&facet.field=MG&facet.field=WorkflowName&rows=0&facet.mincount=1&facet.date=TimeGenerated&facet.date.start=2013-03-10T00:00:00Z&facet.date.end=2014-12-17T00:00:00Z&facet.date.gap=%2B1HOUR"
export FACET_STRING=""
#echo FACET_STRING = ${FACET_STRING}

START_TIME_CLOCK=`date "+%H:%M:%S"`
START_TIME_EPOCH_NANO=`date "+%s.%N"`
echo Starting at ${START_TIME_CLOCK} - time since epoch start ${START_TIME_EPOCH_NANO}
echo term,numTerms,numDocs,timeEpoch,timeStart,totalTime,curlStatus,lookupTime,connectTime,pretransfterTime,starttransferTime

CURRENT_NUM_DOCS=0
for DOCS_NEXT_QUERY in ${DOCS_TO_QUERY}; do

  while [ ${CURRENT_NUM_DOCS} -lt ${DOCS_NEXT_QUERY} ]; do
  
    let DOCS_TO_POST=DOCS_NEXT_QUERY-CURRENT_NUM_DOCS
    if [ ${DOCS_TO_POST} -gt ${MAX_DOCS_PER_POST} ]; then
	  DOCS_TO_POST=${MAX_DOCS_PER_POST}
	fi
    
    echo Generating data for ${DOCS_TO_POST} docs starting at doc num ${CURRENT_NUM_DOCS} ... >&2
    ./datagen.sh ${CURRENT_NUM_DOCS} ${DOCS_TO_POST} ${NUM_TERMS}
    
    echo Posting data ... >&2
    CURRENT_TIME_EPOCH_NANO=`date "+%s.%N"`  
    POST_COMMENT="post,${NUM_TERMS},${CURRENT_NUM_DOCS},${CURRENT_TIME_EPOCH_NANO},${TIME_SINCE_START}"
    ./post_csv_file.sh temp_data.csv "${POST_COMMENT}" COMMIT
    
    # Check that new docs were committed.
    # -s -S : Don't show progress info, but do show errors.
    # echo Checking committed doc count ...
    curl -s -S -o temp_stats.txt "http://${SOLR_SERVER_HOSTNAME}${SOLR_SERVER_PORT}/solr/collection1/admin/mbeans?stats=true"
    
    # Using XMLStarlet for XML processing
    # http://xmlstar.sourceforge.net/doc/UG/xmlstarlet-ug.html
    #
    # Get the value of numDocs
    # <int name="numDocs">0</int>
    # Path determined using: $ xmlstarlet.exe el -v temp_stats.txt | grep numDocs
    COMMITTED_DOCS=`xmlstarlet.exe sel --text --template --value-of "(response/lst/lst/lst/lst/int[@name='numDocs'])[1]" temp_stats.txt`
    
	let CURRENT_NUM_DOCS=COMMITTED_DOCS
	
  done
  
  # Issue a query against each of the randomly selected terms.
  while read TERM; do
    QUERY=${QUERY_PREFIX}${TERM}${QUERY_SUFFIX}
    #echo Querying ${TERM} ... >&2
	OUTPUT_FILE_NAME=${CURRENT_NUM_DOCS}_${TERM}
	
	CURRENT_TIME_EPOCH_NANO=`date "+%s.%N"`  
	# -s -S : Don't show progress info, but do show errors.
	CURL_OUTPUT=`curl -s -S --write-out "%{time_total},%{http_code},%{time_namelookup},%{time_connect},%{time_pretransfer},%{time_starttransfer}\n" -o query_output/query.${OUTPUT_FILE_NAME}.txt "http://${SOLR_SERVER_HOSTNAME}${SOLR_SERVER_PORT}/solr/collection1/select?q=${QUERY}&wt=json&indent=true${FACET_STRING}${KEYS_STRING}"`
	# Do this calculation after run curl, even though it is reporting the time that start curl.
	# Don't want to include the time to spawn a subshell and run awk.
	TIME_SINCE_START=`echo - | awk "{ print ${CURRENT_TIME_EPOCH_NANO} - ${START_TIME_EPOCH_NANO} }"`
	
	echo "${TERM},${NUM_TERMS},${COMMITTED_DOCS},${CURRENT_TIME_EPOCH_NANO},${TIME_SINCE_START},${CURL_OUTPUT}"
	# echo "${TERM},${NUM_TERMS},${COMMITTED_DOCS},${CURRENT_TIME_EPOCH_NANO},${TIME_SINCE_START},${CURL_OUTPUT}" >&2
  done <temp_terms.txt  

  #  sleep 1

done
