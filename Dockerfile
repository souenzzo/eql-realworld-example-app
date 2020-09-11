FROM node:alpine AS node
COPY package.json package-lock.json ./
RUN npm install

FROM adoptopenjdk:11-jre-openj9
ENV CLOJURE_VERSION=1.10.1.561
ENV CLOJURE_INSTALL_SHA=7f9e4e7c5a8171db4e4edf5ce78e5b8f453bae641a4c6b7f3dda36c3128d2ff7
RUN curl -sO https://download.clojure.org/install/linux-install-$CLOJURE_VERSION.sh && \
    sha256sum linux-install-$CLOJURE_VERSION.sh && \
    echo "$CLOJURE_INSTALL_SHA *linux-install-$CLOJURE_VERSION.sh" | sha256sum -c - && \
    chmod +x linux-install-$CLOJURE_VERSION.sh && \
    ./linux-install-$CLOJURE_VERSION.sh && \
    clojure -e "(clojure-version)"
RUN useradd -m conduit
USER conduit
WORKDIR /home/conduit
COPY --chown=conduit . .
COPY --from=node --chown=conduit node_modules node_modules
RUN clojure -A:shadow-cljs release conduit \
 && clojure -A:shadow-cljs run shadow.cljs.build-report conduit target/report.html \
 && mkdir -p classes \
 && clojure -A:aot
CMD ["clojure", "-A:conduit"]
