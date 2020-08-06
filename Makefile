#!/usr/bin/env make

.PHONY: deploy clean

clean:
	rm -rf .cpcache/ .shadow-cljs/ target/


target/main.js: node_modules
	clojure -A:shadow-cljs release conduit

node_modules:
	npm install

deploy: clean target/main.js
	cp target/main.js gh-pages/main.js
	cp target/main.js.map gh-pages/main.js.map
	cd gh-pages && git add main.js && git commit -am'+main.js' && git push origin gh-pages
