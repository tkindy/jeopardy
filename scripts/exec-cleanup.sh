old_files=$(ls | grep "jeopardy.*\.jar" | sort -V -r | tail -n +2)
if [[ -z "$old_files" ]]; then
  echo "Nothing to clean up"
else
  rm $old_files
fi
