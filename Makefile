#!/usr/bin/env make

.PHONY: deploy clean dev

export TIMBRE_LEVEL=:report
export VERSION=$(shell git rev-parse HEAD)

clean:
	rm -rf .cpcache/ .shadow-cljs/ target/

dev:
	clojure -A:dev:shadow-cljs watch conduit workspace

target/conduit/main.js: node_modules
	clojure -A:shadow-cljs release conduit --config-merge "{:closure-defines {conduit.client/VERSION \"$(VERSION)\"}}"
	clojure -A:dev:shadow-cljs release workspace

node_modules:
	npm install

deploy: clean target/conduit/main.js
	cp target/conduit/main.js       gh-pages/conduit/main.js
	cp target/conduit/main.js.map   gh-pages/conduit/main.js.map
	cp target/workspace/main.js     gh-pages/workspace/main.js
	cp target/workspace/main.js.map gh-pages/workspace/main.js.map
	cd gh-pages && git commit -am'v: $(VERSION)' && git push origin gh-pages

