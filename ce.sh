#!/bin/bash
# show errors with their detail lines for files matching $1 (regex), limit $2
awk -v pat="$1" 'BEGIN{p=0} /^[A-Z]:.*error:/{p=($0 ~ pat)} p{print}' "$TEMP/fm.log" | perl -pe "s#.*findme.##" | grep -v "^\s*\^\s*$" | cut -c1-240 | head -${2:-80}
