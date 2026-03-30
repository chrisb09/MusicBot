# Setup Guide

This guide covers the normal non-Docker setup path and the main configuration points that matter on this fork.

## Prerequisites

- Java 11 or newer
- A Discord bot token
- A Discord application with the bot invited to your server

## Discord Setup

1. Create a bot in the [Discord Developer Portal](https://discord.com/developers/applications).
2. Copy the bot token.
3. Enable the `MESSAGE_CONTENT` intent.

This intent is required for command-based bots like this one.

## Privileged Intent Notes

This fork moved from JDA 4 to JDA 6, so `MESSAGE_CONTENT` is now an important operational requirement for the classic text-command flow.

Keep in mind:

- If the bot joins more than 100 servers, this intent must be enabled in the [Discord Developer Portal](https://discord.com/developers/applications).
- `MESSAGE_CONTENT` is a privileged intent.
- Discord verification requirements become relevant as the bot grows.
- The related portal controls historically only become visible once the application is already in a fairly large number of servers.

For the broader Discord/JDA behavior, see the [JDA gateway intents documentation](https://jda.wiki/using-jda/gateway-intents-and-member-cache-policy/).

## First Configuration

The bot reads from `config.txt` using the values defined in [reference.conf](/git/MusicBot/src/main/resources/reference.conf).

The minimum settings you should care about first are:

```conf
token = "YOUR_BOT_TOKEN"
owner = 123456789012345678
prefix = "@mention"
```

Useful fork-specific options:

```conf
youtubeoauth2 = true
statusmessages = true
dblog.enabled = false
```

You may also need these for YouTube PO token support:

```conf
ytpotoken = "PO_TOKEN_HERE"
ytvisitordata = "VISITOR_DATA_HERE"
```

## Running The Bot

Build:

```bash
mvn clean package
```

Run:

```bash
java -jar target/JMusicBot-*-All.jar
```

This fork includes changes to keep source builds practical on the newer stack as well.

## YouTube Login

If YouTube playback is unreliable, enable:

```conf
youtubeoauth2 = true
```

After startup, the bot prints instructions and sends the owner a DM with the authorization flow.

Important:

- Do not use your main Google account.
- Use a burner or otherwise disposable account.

If you are starting from a clean config, this option should already exist with a default of `false`.

## YouTube PO Tokens And Visitor Data

This fork also supports explicit YouTube PO token and visitor-data configuration.

Config:

```conf
ytpotoken = "PO_TOKEN_HERE"
ytvisitordata = "VISITOR_DATA_HERE"
```

You can generate these values with:

- [iv-org/youtube-trusted-session-generator](https://github.com/iv-org/youtube-trusted-session-generator)

This is useful when normal YouTube playback is not enough and you need a more explicit trusted-session style setup.

## DAVE / Voice Support

This fork includes DAVE support for current Discord voice requirements. In normal supported environments, nothing extra is required beyond using the provided shaded jar.

If you run on an unsupported architecture, add the correct native `libdave-jvm` dependency in [pom.xml](/git/MusicBot/pom.xml).

Bundled native targets include:

- Linux glibc x86_64
- Linux musl x86_64
- Windows x86_64
- macOS

## Status Messages

The bot can maintain a single playback status message in the active text channel.

Config:

```conf
statusmessages = true
```

Disable it with:

```conf
statusmessages = false
```

## Analytics Logging

Playback analytics logging is off by default.

Enable SQLite logging:

```conf
dblog.enabled = true
dblog.jdbc_url = "jdbc:sqlite:musicbot.db"
```

This creates a local SQLite database file and records queue, playback, listener, and command activity.

## Windows Note

If you run the bot directly on Windows rather than in Docker, install Java first and consider a service wrapper such as [NSSM](http://nssm.cc/) if you want it to run in the background.

## Related Fork Context

This fork incorporates or builds on work related to:

- [PR-1670](https://github.com/jagrosh/MusicBot/pull/1670) for YouTube account login support
- [PR-1703](https://github.com/jagrosh/MusicBot/pull/1703) for source builds
- [PR-1772](https://github.com/jagrosh/MusicBot/pull/1772) for YouTube PO token and IP rotation work

If this fork does not fit your setup, another fork you may want to inspect is [SeVile/MusicBot](https://github.com/SeVile/MusicBot).
