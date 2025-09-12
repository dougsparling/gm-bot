# GM Bot #

## Build & Run ##

Developers, build and assemble with SBT. Though Java 17+ won't work with this version of SBT due to security manager limitations, so downgrade if needed:

```sh
$ export JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home
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

A Docker image is also available, but rather than doing a multi-stage build, it just copies the jar built as above because I'm lazy:

```sh
docker build . -t <registry>/gm-bot
docker push <registry>/gm-bot:latest
```

And finally, the docker image can be deployed to Kubernetes using the simple manifest available in `manifest/deployment.yaml`