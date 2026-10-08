#!/bin/sh
set -eu
cd "$(dirname "$0")"
mkdir -p out
javac -d out WeatherServer.java
exec java -cp out WeatherServer
