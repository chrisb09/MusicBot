# Docker Guide

This fork supports running the bot in Docker, which is usually the easiest deployment path for a server.

## Image

Published image:

- [docker.io/chrisb09/jmusicbot](https://hub.docker.com/r/chrisb09/jmusicbot)

Container build sources:

- [github.com/chrisb09/jmb-container](https://github.com/chrisb09/jmb-container)

## Minimal Compose Example

```yaml
version: "2.2"

services:
  jmusicbot:
    image: chrisb09/jmusicbot:latest
    container_name: jmusicbot
    restart: unless-stopped
    volumes:
      - ./config:/jmb/config
```

Replace `./config` with the host path you actually want to use.

## Config Files

Your persistent bot config should live in the mounted config directory. That lets you redeploy containers without losing your bot settings.

Typical things to configure there:

- bot token
- owner ID
- prefix
- YouTube login toggle
- YouTube PO token and visitor-data values if needed
- status message toggle
- analytics logging settings

## Deploy Flow

A typical update flow is:

1. Pull or rebuild the image.
2. Restart the container.
3. Check logs for startup errors.

Useful commands:

```bash
docker-compose up -d
docker-compose logs -f jmusicbot
```

## Alpine / musl Note

This fork bundles native support for Alpine-compatible musl environments as part of its DAVE setup, so Alpine-based container deployments are supported.

## SQLite Logging In Docker

If you enable analytics logging with SQLite:

```conf
dblog.enabled = true
dblog.jdbc_url = "jdbc:sqlite:musicbot.db"
```

make sure the database file lives somewhere inside your mounted persistent config or data path if you want it to survive container recreation.

The `stats` command and scheduled server rewind embeds read from this database, so reports only include activity logged after analytics were enabled.

## YouTube Token Notes In Docker

If you use YouTube login or PO token based configuration, keep those values in your persistent mounted config rather than baking them into the image.

Relevant config values include:

```conf
youtubeoauth2 = true
ytpotoken = "PO_TOKEN_HERE"
ytvisitordata = "VISITOR_DATA_HERE"
```

## Recommendation

For production-style use, Docker is the cleanest path if you want:

- reproducible runtime behavior
- simple upgrades
- easier separation between bot code and persistent config
