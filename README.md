# GM Bot #

## Build & Run ##

Developers, run inside SBT:

```sh
$ cd gmbot
$ sbt
$ jetty:start
```

Operators, deploy and run a phat jar:

```sh
$ cd gmbot
$ sbt clean assembly
$ PORT=8080 java -jar **/*-assembly-**.jar
```

Open [http://localhost:8080/health](http://localhost:8080/health) in your browser to verify that the app is running.

