#!/usr/bin/env make

.PHONY: deploy clean dev

clean:
	rm -rf .cpcache/ .shadow-cljs/ target/

dev:
	clojure -A:dev:shadow-cljs watch conduit workspace

target/conduit/main.js: node_modules
	clojure -A:shadow-cljs release conduit
	clojure -A:dev:shadow-cljs release workspace

node_modules:
	npm install

deploy: clean target/conduit/main.js
	cp target/conduit/main.js gh-pages/conduit.js
	cp target/workspace/main.js gh-pages/workspace.js
	cp target/conduit/main.js.map gh-pages/conduit.js.map
	cp target/workspace/main.js.map gh-pages/workspace.js.map
	cd gh-pages && git commit -am'+main.js' && git push origin gh-pages
