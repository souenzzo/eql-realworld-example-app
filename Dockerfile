FROM node:alpine AS node
COPY package.json .
COPY package-lock.json .
RUN npm install

FROM clojure:openjdk-15-tools-deps-alpine
RUN adduser -D conduit
USER conduit
WORKDIR /home/conduit
COPY --chown=conduit . .
COPY --from=node --chown=conduit node_modules node_modules
RUN clojure -A:shadow-cljs release conduit \
 && mkdir classes \
 && clojure -e "(compile 'conduit.server)"
CMD ["clojure", "-m", "conduit.server"]
