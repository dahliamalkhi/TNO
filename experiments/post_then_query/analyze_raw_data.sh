#!/bin/bash
(set -o igncr) 2>/dev/null && set -o igncr; # this comment is required. this should be 2nd line of script.
# Above line makes this script ignore Windows line endings, to avoid this error without having to run dos2unix: 
#    $'\r': command not found
# As per http://stackoverflow.com/questions/14598753/running-bash-script-in-cygwin-on-windows-7

OUTPUT_FILE_NAME=results.csv

#INPUT_FILES=`ls stdout.client.*.txt`
#INPUT_FILES="stdout.client.vanilla_10_terms.txt stdout.client.vanilla_10K_terms.txt stdout.client.vanilla_10M_terms.txt stdout.client.TNO_10_terms.txt stdout.client.TNO_10K_terms.txt stdout.client.TNO_10M_terms.txt"
INPUT_FILE_NAMES="stdout.client.vanilla_1K_terms.txt stdout.client.vanilla_100K_terms.txt stdout.client.vanilla_1M_terms.txt stdout.client.TNO_1K_terms.txt stdout.client.TNO_100K_terms.txt stdout.client.TNO_1M_terms.txt"

INPUT_FILE_SET=2014-09-07__1K_100K_1M_terms__better_query_sampling

TEMP_INPUT_FILE=temp_input.txt
TEMP_TIMES_FILE=temp_times.txt

function output_row {
			#MEAN_TIME=$(cat ${TEMP_TIMES_FILE} | awk '{sum+=$1}END{print (sum/NR)}' )
			##STD_DEV=$(cat ${TEMP_TIMES_FILE} | awk '{sum+=$1; sumsq+=$1*$1}END{print sqrt(sumsq/NR - (sum/NR)**2)}' )
#			echo ${TESTCASE},${CURRENT_NUM_DOCS},${QUERY_TIMES_STRING}

#			echo ${TESTCASE},${CURRENT_NUM_DOCS},,,${QUERY_COUNT},${QUERY_TIMES_STRING} >&2
			echo ${TESTCASE},${CURRENT_NUM_DOCS},,,${QUERY_COUNT},${QUERY_TIMES_STRING} >> ${OUTPUT_FILE_NAME}
			CURRENT_NUM_DOCS=${NUM_DOCS}
			QUERY_COUNT=1
			QUERY_TIMES_STRING=${QUERY_TIME},
			echo ${QUERY_TIME} > ${TEMP_TIMES_FILE}
}

#echo testCase,indexSize,numQueries,scriptMean,excelMean,scriptStdDev,excelStdDev,queryTimes
#echo testCase,indexSize,numQueries,scriptMean,excelMean,scriptStdDev,excelStdDev
echo testCase,indexSize,meanTime,StdDev,numQueries,time1,time2,etc > ${OUTPUT_FILE_NAME}
for INPUT_FILE_NAME in ${INPUT_FILE_NAMES}; do
	TESTCASE=`echo ${INPUT_FILE_NAME} | cut -d '.' -f 3`
	echo Processing ${TESTCASE} >&2
	
	INPUT_FILE=${INPUT_FILE_SET}/${INPUT_FILE_NAME}
	
	# Keep just numDocs, totalTime and curlStatus
	tail -n +4 ${INPUT_FILE} | grep -v "post," | awk -F ',' '{ print $1,$3,$6,$7 }' > ${TEMP_INPUT_FILE}
	
	CURRENT_NUM_DOCS=1
	QUERY_COUNT=0
	QUERY_TIMES_STRING=""
	rm ${TEMP_TIMES_FILE}

	while read TERM NUM_DOCS QUERY_TIME CURL_STATUS; do
		# check curl status is a number
		# case ${CURL_STATUS} in
		# ''|*[!0-9]*)
		# 	echo Trouble with TERM=${TERM} NUM_DOCS=${NUM_DOCS} QUERY_TIME=${QUERY_TIME} CURL_STATUS=${CURL_STATUS}. Skipping... >&2 
		# 	continue;
		# 	;;
		# *) echo 
		# 	# Nothing to do if it is a number.
		# 	;;
		# esac

		# echo xx got ${TERM} ${NUM_DOCS} ${QUERY_TIME} ${CURL_STATUS} xx >&2
		if [ "${CURL_STATUS}" -ne "200" ]; then
		  echo Curl status $${CURL_STATUS} for ${TERM} at num docs ${NUM_DOCS}. Exiting ... >&2
		  exit 1
		fi
		if [ "${NUM_DOCS}" -eq "${CURRENT_NUM_DOCS}" ]; then
			let QUERY_COUNT=QUERY_COUNT+1
			QUERY_TIMES_STRING=${QUERY_TIMES_STRING}${QUERY_TIME},
			echo ${QUERY_TIME} >> ${TEMP_TIMES_FILE}
		else
			echo Writing output for ${CURRENT_NUM_DOCS} doc index >&2
			output_row
		fi
	done<${TEMP_INPUT_FILE}
	# Flush last row
	echo Writing output for ${CURRENT_NUM_DOCS} doc index >&2
	output_row
	
done
