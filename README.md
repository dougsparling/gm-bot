# GM Bot #

## Build & Run ##

Developers, build and run locally with SBT (requires Java 21+):

```sh
$ cd gmbot
$ sbt run
```

Operators, build and push a Docker image (no local sbt/Java required):

```sh
docker build . -t <registry>/gm-bot
docker push <registry>/gm-bot:latest
```

Or run locally:

```sh
docker run -p 8080:8080 <registry>/gm-bot
```

And finally, the docker image can be deployed to Kubernetes using the simple manifest available in `manifest/deployment.yaml`
