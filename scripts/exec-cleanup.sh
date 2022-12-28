set -euo pipefail
IFS=$'\n\t'

old_files=$(ls | grep "jeopardy.*\.jar" | sort -V -r | tail -n +2)
if [[ -z "$old_files" ]]; then
  echo "Nothing to clean up"
else
  rm $old_files
  echo "Cleaned up $(echo "$old_files" | wc -l) files"
fi
