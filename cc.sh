#!/bin/bash
# compile and print unique errors: ./cc.sh [maxlines]
cd "$(dirname "$0")"
./gradlew.bat compileJava > "$TEMP/fm.log" 2>&1
grep -E "^[A-Z]:.*error:" "$TEMP/fm.log" | perl -pe "s#.*findme.##" | sort -u > "$TEMP/fm.err"
echo "errors: $(wc -l < "$TEMP/fm.err")  $(grep -E 'BUILD' "$TEMP/fm.log")"
head -${1:-60} "$TEMP/fm.err" | cut -c1-230
