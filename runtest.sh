#!/bin/bash
# Repeat an instrumented test multiple times and print timing + failure logs.

# ---- Configuration ----
CLASS_ARG="org.xmtp.android.library.GroupTest#testCanRemoveGroupMembersWhenNotCreator"
MODULE=":library"
RUNNER_TASK="connectedDebugAndroidTest"
RUNS=10
# ------------------------

success=0
fail=0
total_time=0

echo "Running $CLASS_ARG $RUNS times..."
echo

for i in $(seq 1 $RUNS); do
  echo "=========================="
  echo " Run #$i"
  echo "=========================="

  start_time=$(date +%s)
  
  # capture stdout+stderr to a temp log file
  LOG_FILE="run_${i}.log"
  ./gradlew $MODULE:$RUNNER_TASK \
    "-Pandroid.testInstrumentationRunnerArguments.class=$CLASS_ARG" \
    >"$LOG_FILE" 2>&1
  status=$?

  end_time=$(date +%s)
  duration=$((end_time - start_time))
  total_time=$((total_time + duration))

  if [ $status -eq 0 ]; then
    echo "✅ Run #$i PASSED in ${duration}s"
    ((success++))
  else
    echo "❌ Run #$i FAILED in ${duration}s (exit code $status)"
    ((fail++))
    echo "---- Failure Output ----"
    # print the last 30 lines of the log so you see the reason
    tail -n 30 "$LOG_FILE"
    echo "---- Full log: $LOG_FILE ----"
  fi
done

echo
echo "=========================="
echo " Test Summary"
echo "=========================="
echo "  Total runs:    $RUNS"
echo "  Passed:        $success"
echo "  Failed:        $fail"

success_rate=$(awk "BEGIN { printf \"%.1f\", ($success/$RUNS)*100 }")
avg_time=$(awk "BEGIN { printf \"%.2f\", ($total_time/$RUNS) }")

echo "  Success rate:  $success_rate%"
echo "  Avg run time:  ${avg_time}s"
echo "  Total time:    ${total_time}s"
echo

# exit nonzero if any failures occurred
[ $fail -eq 0 ] || exit 1

