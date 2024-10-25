<img align="right" src="https://i.imgur.com/zrE80HY.png" height="200" width="200">

# This fork

Essentially, this is just a merge of two pull requests:

### Support for logging into your (burner) youtube account:

[PR-1670](https://github.com/jagrosh/MusicBot/pull/1670)

### To allow building the project from source
[PR-1703](https://github.com/jagrosh/MusicBot/pull/1703)

## Easy deployment via docker

Additionally, i thought a corresponding docker image would make things better:

The image can be found on [dockerhub](https://hub.docker.com/repository/docker/chrisb09/jmusicbot/general)

Docker-compose:

```yaml
version: '2.2'

services:
  jmusicbot:
    image: chrisb09/jmusicbot:latest
    container_name: jmusicbot
    restart: unless-stopped
    volumes:
     - ./config:/jmb/config
```

although, obviously you should change `./config` to whatever path is right for your setup.
Source code for the docker image is also on [Github](https://github.com/chrisb09/jmb-container).


## YouTube-Login

Obviously you still need to log in if you want to use youtube properly, hence you need to enable this in the config.
If you are using a pre-existing config, simply add:
```
/ If you set this to true, the bot will use a Google account to play YouTube tracks.
// This is only needed if you are experiencing issues with YouTube playback.
// Once enabled, instructions for login will be written to the console and DMs.
// DO NOT use your main Google account! Use an alternative account.

youtubeoauth2=true
```

If you start with a clean slate this line should already be present, albeit with `false` being the default.
As the author of the PR reiterates multiple times, do not use your main google account as using your account for a bot is most certainly against the ToS and thus you are endangering said account.

To add the account you simply have to follow the very basic instructions printed either in the console or sent to you from the bot via discord after you have started it, open the linked google page and paste the code.

# JMusicBot

[![Downloads](https://img.shields.io/github/downloads/jagrosh/MusicBot/total.svg)](https://github.com/jagrosh/MusicBot/releases/latest)
[![Stars](https://img.shields.io/github/stars/jagrosh/MusicBot.svg)](https://github.com/jagrosh/MusicBot/stargazers)
[![Release](https://img.shields.io/github/release/jagrosh/MusicBot.svg)](https://github.com/jagrosh/MusicBot/releases/latest)
[![License](https://img.shields.io/github/license/jagrosh/MusicBot.svg)](https://github.com/jagrosh/MusicBot/blob/master/LICENSE)
[![Discord](https://discordapp.com/api/guilds/147698382092238848/widget.png)](https://discord.gg/0p9LSGoRLu6Pet0k)<br>
[![CircleCI](https://dl.circleci.com/status-badge/img/gh/jagrosh/MusicBot/tree/master.svg?style=svg)](https://dl.circleci.com/status-badge/redirect/gh/jagrosh/MusicBot/tree/master)
[![Build and Test](https://github.com/jagrosh/MusicBot/actions/workflows/build-and-test.yml/badge.svg)](https://github.com/jagrosh/MusicBot/actions/workflows/build-and-test.yml)
[![CodeFactor](https://www.codefactor.io/repository/github/jagrosh/musicbot/badge)](https://www.codefactor.io/repository/github/jagrosh/musicbot)

A cross-platform Discord music bot with a clean interface, and that is easy to set up and run yourself!

[![Setup](http://i.imgur.com/VvXYp5j.png)](https://jmusicbot.com/setup)

## Features
  * Easy to run (just make sure Java is installed, and run!)
  * Fast loading of songs
  * No external keys needed (besides a Discord Bot token)
  * Smooth playback
  * Server-specific setup for the "DJ" role that can moderate the music
  * Clean and beautiful menus
  * Supports many sites, including Youtube, Soundcloud, and more
  * Supports many online radio/streams
  * Supports local files
  * Playlist support (both web/youtube, and local)

## Supported sources and formats
JMusicBot supports all sources and formats supported by [lavaplayer](https://github.com/sedmelluq/lavaplayer#supported-formats):
### Sources
  * YouTube
  * SoundCloud
  * Bandcamp
  * Vimeo
  * Twitch streams
  * Local files
  * HTTP URLs
### Formats
  * MP3
  * FLAC
  * WAV
  * Matroska/WebM (AAC, Opus or Vorbis codecs)
  * MP4/M4A (AAC codec)
  * OGG streams (Opus, Vorbis and FLAC codecs)
  * AAC streams
  * Stream playlists (M3U and PLS)

## Example
![Loading Example...](https://i.imgur.com/kVtTKvS.gif)

## Setup
Please see the [Setup Page](https://jmusicbot.com/setup) to run this bot yourself!

## Questions/Suggestions/Bug Reports
**Please read the [Issues List](https://github.com/jagrosh/MusicBot/issues) before suggesting a feature**. If you have a question, need troubleshooting help, or want to brainstorm a new feature, please start a [Discussion](https://github.com/jagrosh/MusicBot/discussions). If you'd like to suggest a feature or report a reproducible bug, please open an [Issue](https://github.com/jagrosh/MusicBot/issues) on this repository. If you like this bot, be sure to add a star to the libraries that make this possible: [**JDA**](https://github.com/DV8FromTheWorld/JDA) and [**lavaplayer**](https://github.com/sedmelluq/lavaplayer)!

## Editing
This bot (and the source code here) might not be easy to edit for inexperienced programmers. The main purpose of having the source public is to show the capabilities of the libraries, to allow others to understand how the bot works, and to allow those knowledgeable about java, JDA, and Discord bot development to contribute. There are many requirements and dependencies required to edit and compile it, and there will not be support provided for people looking to make changes on their own. Instead, consider making a feature request (see the above section). If you choose to make edits, please do so in accordance with the Apache 2.0 License.
