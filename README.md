# Soduku – Electric Clojure

First try using "Electric #Clojure":
Multiplayer Sudoku in [<150 LOC](https://github.com/s-ol/electric-sudoku/blob/main/src/app/sudoku.cljc), ~6h. Pretty neat!

https://user-images.githubusercontent.com/124158/220984721-bd0960da-8d05-49e1-b9b3-a948e942bfc8.mp4

# Setup (development)

```
$ clj -A:dev -X user/main

Starting Electric compiler and server...
shadow-cljs - server version: 2.20.1 running at http://localhost:9630
shadow-cljs - nREPL server started on port 9001
[:app] Configuring build.
[:app] Compiling ...
[:app] Build completed. (224 files, 0 compiled, 0 warnings, 1.93s)

👉 App server available at http://0.0.0.0:8080
```

# Building

For a manual JAR deployment:

```
$ clj -X:build build-client # release-mode JS client build
$ clj -X:build uberjar # server jar build (cleans & builds client)
```

or to create a Docker image:

```
$ docker build --build-arg VERSION=`git describe ...` -t electric-sudoku .
```
