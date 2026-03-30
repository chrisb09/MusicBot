# Fork Changelog

This file tracks fork-specific changes that are useful to operators and contributors. It is not meant to replace commit history, but to summarize what changed at a higher level.

## 0.6.3

- Added JDBC-backed playback analytics logging.
- Added SQLite as the default logging backend.
- Logged queue, playback, listener, search, skip, and command activity.
- Added `dblog.*` config settings to enable and configure logging.

Config:

```conf
dblog.enabled = true
dblog.jdbc_url = "jdbc:sqlite:musicbot.db"
dblog.user = ""
dblog.password = ""
```

By default, logging is disabled.

## 0.6.2

- Fixed several status-message race conditions and repost issues.
- Added debug logging around status-message creation, editing, deletion, and repost flow.
- Added a config toggle to disable the status-message feature entirely.

Config:

```conf
statusmessages = true
```

Set it to `false` to disable the feature.

## 0.6.1

- Bumped the fork version after the initial status-message work had landed.

## 0.6.0

- Updated the bot for modern JDA and Discord requirements.
- Added DAVE voice support required for current Discord voice playback.
- Continued dependency refreshes for lavaplayer and related components.
- Carried forward work related to YouTube login, PO token handling, and buildability on the newer stack.

Imported work included in this fork:

- PR-1670, support for logging into a YouTube account, is included from `0.4.5` onward.
- PR-1703, changes to allow building the project from source more easily, is included from `0.4.5` onward.
- `jagrosh#1772`, YouTube PO token and IP rotation support, is included from `0.4.7` onward.

## Notes

- This fork may move faster than upstream in areas related to Discord API compatibility and deployment practicality.
- When reporting issues, mention the exact fork version because behavior around playback, status messages, and logging has changed across recent releases.
