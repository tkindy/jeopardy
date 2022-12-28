latest () {
  ls | grep "$1.*\.jar" | sort -V -r | head -n 1
}

server_admin_jar="$(latest server-admin)"
jeopardy_jar="$(latest jeopardy)"

echo "Deploying $jeopardy_jar"
java -jar $server_admin_jar $jeopardy_jar
