# ![RealWorld Example App](logo.png)

> ### eql-realworld-example-app codebase containing real world examples (CRUD, auth, advanced patterns, etc) that adheres to the [RealWorld](https://github.com/gothinkster/realworld) spec and API.


### [Demo](https://eql-realworld-example-app.herokuapp.com/spa#/home)&nbsp;&nbsp;&nbsp;&nbsp;[RealWorld](https://github.com/gothinkster/realworld)


This codebase was created to demonstrate a fully fledged fullstack application built with **[YOUR_FRAMEWORK]** including CRUD operations, authentication, routing, pagination, and more.

We've gone to great lengths to adhere to the **EQL** community styleguides & best practices.

For more information on how to this works with other frontends/backends, head over to the [RealWorld](https://github.com/gothinkster/realworld) repo.


# How it works

It will work in many ways:

## spa

[spa](https://eql-realworld-example-app.herokuapp.com/spa#/home)

- `fulcro` will use `pathom` as `remote`. `connect` resolvers will call `conduit` api from browser.

## spa + proxy

[spa-proxy](https://eql-realworld-example-app.herokuapp.com/spa-proxy#/home)

- `fulcro` will use the standard `http` as `remote` and `pathom` will run on server, calling the same `connect` resolvers, requesting `conduit` api from server.  

## ssr (TBD)

- `fulcro` will render on JVM, without any JS. Will use pathom as proxy

## ssr_hybid (TBD)

- `fulcro` will render on JVM and the client will `hydrate`. Will operate as spa + proxy

## spa + SQL (TBD)

## spa + CRUX (memory) (TBD)

## spa + Datascript (memory) (TBD)


# [TBD]  Clients NEXT

Both frontends and backends can operate in many ways

## REST Client

Send's requests to the remote folloing conduit specification.

It can point to:

- https://conduit.productionready.io/api
- https://eql-realworld-example-app.herokuapp.com/proxy/api
- https://eql-realworld-example-app.herokuapp.com/datascript/api
- https://eql-realworld-example-app.herokuapp.com/crux/api
- https://eql-realworld-example-app.herokuapp.com/jdbc/api

## EQL Client

Send's EQL queries to it's remote.

It can point to 

- https://eql-realworld-example-app.herokuapp.com/proxy/eql
- https://eql-realworld-example-app.herokuapp.com/datascript/eql
- https://eql-realworld-example-app.herokuapp.com/crux/eql
- https://eql-realworld-example-app.herokuapp.com/jdbc/eql

## Datascript client

It uses datascript "server" inside the SPA client.

## Proxy server

It receive EQL operations in https://eql-realworld-example-app.herokuapp.com/proxy/eql and 
uses https://conduit.productionready.io/api to resolve all data.

## Crux server

EQL to Crux resolvers

## JDBC server

EQL to jdbc resolvers

## Datascript server 

EQL to datascript resolvers

# Getting started

```bash
$ npm install
$ clj -A:dev:shadow-cljs watch conduit
```
