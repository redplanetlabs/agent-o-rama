npm i
mkdir -p resource/public
cp -r resource/assets/* resource/public
lein with-profile +ui run -m shadow.cljs.devtools.cli --npm compile :frontend
lein install
