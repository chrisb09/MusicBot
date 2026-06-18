<img align="right" src="https://i.imgur.com/zrE80HY.png" height="200" width="200">

# JMusicBot Fork

A self-hosted Discord music bot fork based on JMusicBot, updated to keep working on modern Discord and JDA versions.

This repository keeps the bot practical to run today, while also adding fork-specific features such as status messages and playback analytics logging. Possibly more.

## Demo

Temporary invite: [discord.gg/4cp9CvjwaW](https://discord.gg/4cp9CvjwaW)  
Permanent invite: [discord.gg/hK95396uB3](https://discord.gg/hK95396uB3)

## What This Fork Adds
- Make it work again, through:
    - JDA 6 support and current dependency updates
    - DAVE voice encryption support for current Discord voice requirements
    - Optional YouTube account login support
    - Optional YouTube PO token and visitor-data support
- New niche features, such as:
    - Optional per-channel playback status message
    - Optional JDBC/SQLite playback analytics logging
- Basic QoL improvements, such as:
    - Docker image and container-friendly deployment path

## Quick Links

- Fork changes and version notes: [docs/fork-changelog.md](/docs/fork-changelog.md)
- Setup guide: [docs/setup.md](/docs/setup.md)
- Docker usage: [docs/docker.md](/docs/docker.md)

## Features

- Easy self-hosting
- Fast song loading
- Smooth playback
- DJ role and server-specific settings
- Queue management and playlist support
- Support for many sources, streams, and local files

## Supported Sources And Formats

JMusicBot supports all sources and formats supported by [lavaplayer](https://github.com/sedmelluq/lavaplayer#supported-formats).

Sources:
- YouTube
- SoundCloud
- Bandcamp
- Vimeo
- Twitch streams
- Local files
- HTTP URLs

Formats:
- MP3
- FLAC
- WAV
- Matroska/WebM
- MP4/M4A
- OGG streams
- AAC streams
- Stream playlists such as M3U and PLS

## Setup Notes

If you only need the essentials:

1. Create a Discord bot and get its token, see [here](https://jmusicbot.com/getting-a-bot-token/).
2. Enable the `MESSAGE_CONTENT` intent in the Discord Developer Portal [here](https://discord.com/developers/applications) ([Wiki link](https://jda.wiki/using-jda/gateway-intents-and-member-cache-policy/)).
3. Configure `config.txt`.
4. Run the jar directly or use Docker.

For the detailed version, see [docs/setup.md](/docs/setup.md).

## Docker

A Docker image is published here: [docker.io/chrisb09/jmusicbot](https://hub.docker.com/r/chrisb09/jmusicbot)

For compose examples and deployment notes, see [docs/docker.md](/docs/docker.md).

## YouTube Login

This fork supports Google account login for YouTube playback workarounds.

Enable:

```conf
youtubeoauth2=true
```

Do not use your main Google account. Use a burner or otherwise disposable account instead.

More details are in [docs/setup.md](/docs/setup.md).

If you use PO tokens or visitor data for YouTube, those details are also covered in [docs/setup.md](/docs/setup.md).

## DAVE / Voice Encryption

Discord currently requires DAVE for voice on modern API versions. This fork configures `libdave-jvm` during startup and bundles native binaries for common Linux, Windows, and macOS targets.

If you deploy on an unsupported architecture, add the matching native dependency in [pom.xml](/pom.xml). You probably need to build the library from source for your architecture, so have fun with that.

## Upstream And Related Work

This fork incorporates ideas and patches related to:

- [PR-1670](https://github.com/jagrosh/MusicBot/pull/1670)
- [PR-1703](https://github.com/jagrosh/MusicBot/pull/1703)
- [PR-1772](https://github.com/jagrosh/MusicBot/pull/1772)

If this fork does not fit your needs, another fork you may want to inspect is [SeVile/MusicBot](https://github.com/SeVile/MusicBot).

## Questions And Contributions

If you run into issues, please include:

- the version you are running
- whether you are using Docker or the jar directly
- relevant config changes
- relevant logs

Fork-specific version notes live in [docs/fork-changelog.md](/docs/fork-changelog.md), which should help with troubleshooting and migration as the fork evolves.
