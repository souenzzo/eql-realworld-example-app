#!/usr/bin/env make

.PHONY: deploy clean

clean:
	rm -rf .cpcache/ .shadow-cljs/ target/


target/main.js: node_modules
	clojure -A:shadow-cljs release conduit

node_modules:
	npm install

deploy: clean target/main.js
	git checkout gh-pages
	cp target/main.js .
	git add main.js
	git commit -am'+main.js'
	git push origin gh-pages
