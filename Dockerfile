FROM node:alpine AS node
COPY package.json package-lock.json .
RUN npm install

FROM clojure:openjdk-16-tools-deps-alpine
RUN useradd -m conduit
USER conduit
WORKDIR /home/conduit
COPY --chown=conduit . .
COPY --from=node --chown=conduit node_modules node_modules
RUN clojure -A:shadow-cljs release conduit \
 && echo    -A:shadow-cljs run shadow.cljs.build-report conduit target/report.html \
 && mkdir -p classes \
 && clojure -A:aot \
 && clojure -A:conduit -Spath
CMD ["clojure", "-A:conduit"]
