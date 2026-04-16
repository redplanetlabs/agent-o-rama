#!/bin/sh
# Frontend release build — keep in sync with .github/workflows/test.yml
# "Build frontend for UI tests" (NODE_ENV, npm ci, assets copy, shadow release :frontend).

set -e

lein deps
export NODE_ENV=production

npm ci

rm -rf resource/public
mkdir -p resource/public
cp -r resource/assets/* resource/public

lein with-profile +ui run -m shadow.cljs.devtools.cli release :frontend
