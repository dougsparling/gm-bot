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
$ java -jar **/*-assembly-**.jar
```

If `browse` doesn't launch your browser, manually open [http://localhost:8080/](http://localhost:8080/) in your browser.

