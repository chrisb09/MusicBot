/*
 * Copyright 2026
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.jagrosh.jmusicbot.datalog;

import com.jagrosh.jmusicbot.Bot;
import com.jagrosh.jmusicbot.audio.RequestMetadata;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackInfo;
import java.sql.*;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.guild.voice.GuildVoiceUpdateEvent;
import net.dv8tion.jda.api.managers.AudioManager;
import net.dv8tion.jda.api.entities.channel.unions.AudioChannelUnion;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DataLogService
{
    private static final Logger LOG = LoggerFactory.getLogger("DataLog");

    private final Bot bot;
    private final Connection connection;
    private final ExecutorService executor;
    private final Map<Long, Long> currentPlaySessionId;
    private final Map<Long, Long> currentPlayStartAt;
    private final Map<Long, Map<Long, Long>> activeListeners;

    public DataLogService(Bot bot, String jdbcUrl, String user, String password) throws SQLException
    {
        this.bot = bot;
        this.executor = Executors.newSingleThreadExecutor();
        this.currentPlaySessionId = new ConcurrentHashMap<>();
        this.currentPlayStartAt = new ConcurrentHashMap<>();
        this.activeListeners = new ConcurrentHashMap<>();

        if(user != null && !user.isEmpty())
            this.connection = DriverManager.getConnection(jdbcUrl, user, password == null ? "" : password);
        else
            this.connection = DriverManager.getConnection(jdbcUrl);

        this.connection.setAutoCommit(true);
        initSchema();
    }

    public void shutdown()
    {
        executor.shutdownNow();
        try
        {
            connection.close();
        }
        catch(SQLException ex)
        {
            LOG.warn("Failed to close DataLogService connection: {}", ex.getMessage());
        }
    }

    private void initSchema()
    {
        submit(() ->
        {
            try(Statement st = connection.createStatement())
            {
                st.execute("CREATE TABLE IF NOT EXISTS guilds (guild_id INTEGER PRIMARY KEY, name TEXT, first_seen_at INTEGER)");
                st.execute("CREATE TABLE IF NOT EXISTS users (user_id INTEGER PRIMARY KEY)");
                st.execute("CREATE TABLE IF NOT EXISTS user_profiles (id INTEGER PRIMARY KEY AUTOINCREMENT, user_id INTEGER, guild_id INTEGER, username TEXT, discrim TEXT, avatar_url TEXT, nickname TEXT, seen_at INTEGER)");
                st.execute("CREATE TABLE IF NOT EXISTS tracks (id INTEGER PRIMARY KEY AUTOINCREMENT, source TEXT, identifier TEXT, uri TEXT, title TEXT, author TEXT, length_ms INTEGER, is_stream INTEGER)");
                st.execute("CREATE UNIQUE INDEX IF NOT EXISTS idx_tracks_source_identifier ON tracks(source, identifier)");
                st.execute("CREATE TABLE IF NOT EXISTS play_sessions (id INTEGER PRIMARY KEY AUTOINCREMENT, guild_id INTEGER, voice_channel_id INTEGER, track_id INTEGER, requested_by INTEGER, queue_source TEXT, search_query TEXT, playlist_name TEXT, started_at INTEGER, ended_at INTEGER, end_reason TEXT, duration_ms INTEGER, listeners_at_start INTEGER)");
                st.execute("CREATE TABLE IF NOT EXISTS listener_sessions (id INTEGER PRIMARY KEY AUTOINCREMENT, play_session_id INTEGER, user_id INTEGER, joined_at INTEGER, left_at INTEGER)");
                st.execute("CREATE TABLE IF NOT EXISTS queue_events (id INTEGER PRIMARY KEY AUTOINCREMENT, guild_id INTEGER, user_id INTEGER, track_id INTEGER, event_type TEXT, source TEXT, position INTEGER, query TEXT, playlist_name TEXT, event_meta TEXT, created_at INTEGER)");
                st.execute("CREATE TABLE IF NOT EXISTS search_events (id INTEGER PRIMARY KEY AUTOINCREMENT, guild_id INTEGER, user_id INTEGER, query TEXT, selected_index INTEGER, track_id INTEGER, event_meta TEXT, created_at INTEGER)");
                st.execute("CREATE TABLE IF NOT EXISTS command_events (id INTEGER PRIMARY KEY AUTOINCREMENT, guild_id INTEGER, user_id INTEGER, command TEXT, args TEXT, result TEXT, event_meta TEXT, created_at INTEGER)");
                st.execute("CREATE TABLE IF NOT EXISTS tags (id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT UNIQUE)");
                st.execute("CREATE TABLE IF NOT EXISTS track_tags (track_id INTEGER, tag_id INTEGER, added_at INTEGER, added_by_user_id INTEGER)");

                st.execute("CREATE INDEX IF NOT EXISTS idx_play_sessions_guild_started ON play_sessions(guild_id, started_at)");
                st.execute("CREATE INDEX IF NOT EXISTS idx_queue_events_guild_created ON queue_events(guild_id, created_at)");
                st.execute("CREATE INDEX IF NOT EXISTS idx_search_events_guild_created ON search_events(guild_id, created_at)");
                st.execute("CREATE INDEX IF NOT EXISTS idx_listener_sessions_play ON listener_sessions(play_session_id)");
                st.execute("CREATE INDEX IF NOT EXISTS idx_command_events_guild_created ON command_events(guild_id, created_at)");
                st.execute("CREATE INDEX IF NOT EXISTS idx_command_events_command_created ON command_events(command, created_at)");

                ensureColumnExists("queue_events", "event_meta", "TEXT");
                ensureColumnExists("search_events", "event_meta", "TEXT");
            }
            catch(SQLException ex)
            {
                LOG.warn("Failed to init datalog schema: {}", ex.getMessage());
            }
        });
    }

    public void logQueueAdd(Guild guild, User user, AudioTrack track, String source, Integer position, String query, String playlistName)
    {
        logQueueEventWithMeta(guild, user, track, "ADD", source, position, query, playlistName, null);
    }

    public void logSearchSelection(Guild guild, User user, String query, int selectedIndex, AudioTrack track)
    {
        logSearchEventWithMeta(guild, user, query, selectedIndex, track, null);
    }

    public void logPlayStart(Guild guild, AudioTrack track, RequestMetadata rm, String queueSource, String searchQuery, String playlistName)
    {
        if(guild == null || track == null)
            return;
        submit(() ->
        {
            try
            {
                ensureGuild(guild);
                long trackId = ensureTrack(track);
                long requestedBy = rm == null ? 0L : rm.getOwner();
                if(rm != null && rm.user != null)
                    ensureUserAndProfile(guild, null, rm);

                AudioManager am = guild.getAudioManager();
                AudioChannelUnion channel = am.getConnectedChannel();
                long channelId = channel == null ? 0L : channel.getIdLong();
                int listeners = channel == null ? 0 : (int)channel.getMembers().stream()
                        .filter(m -> !m.getUser().isBot() && !m.getVoiceState().isDeafened())
                        .count();

                try(PreparedStatement ps = connection.prepareStatement(
                        "INSERT INTO play_sessions (guild_id, voice_channel_id, track_id, requested_by, queue_source, search_query, playlist_name, started_at, listeners_at_start) VALUES (?,?,?,?,?,?,?,?,?)",
                        Statement.RETURN_GENERATED_KEYS))
                {
                    ps.setLong(1, guild.getIdLong());
                    if(channelId == 0L)
                        ps.setNull(2, Types.BIGINT);
                    else
                        ps.setLong(2, channelId);
                    ps.setLong(3, trackId);
                    ps.setLong(4, requestedBy);
                    ps.setString(5, queueSource);
                    ps.setString(6, searchQuery);
                    ps.setString(7, playlistName);
                    long startedAt = now();
                    ps.setLong(8, startedAt);
                    ps.setInt(9, listeners);
                    ps.executeUpdate();
                    try(ResultSet rs = ps.getGeneratedKeys())
                    {
                        if(rs.next())
                        {
                            long sessionId = rs.getLong(1);
                            currentPlaySessionId.put(guild.getIdLong(), sessionId);
                            currentPlayStartAt.put(guild.getIdLong(), startedAt);
                            activeListeners.put(guild.getIdLong(), new ConcurrentHashMap<>());
                            if(channel != null)
                            {
                                channel.getMembers().forEach(m ->
                                {
                                    if(m.getUser().isBot() || m.getVoiceState().isDeafened())
                                        return;
                                    startListenerSession(guild, sessionId, m.getUser().getIdLong());
                                });
                            }
                        }
                    }
                }
            }
            catch(SQLException ex)
            {
                LOG.debug("logPlayStart failed: {}", ex.getMessage());
            }
        });
    }

    public void logPlayEnd(Guild guild, String endReason)
    {
        if(guild == null)
            return;
        submit(() ->
        {
            Long sessionId = currentPlaySessionId.remove(guild.getIdLong());
            Long startedAt = currentPlayStartAt.remove(guild.getIdLong());
            if(sessionId == null || startedAt == null)
                return;
            long endedAt = now();
            long duration = Math.max(0L, endedAt - startedAt);
            try(PreparedStatement ps = connection.prepareStatement(
                    "UPDATE play_sessions SET ended_at=?, end_reason=?, duration_ms=? WHERE id=?"))
            {
                ps.setLong(1, endedAt);
                ps.setString(2, endReason);
                ps.setLong(3, duration);
                ps.setLong(4, sessionId);
                ps.executeUpdate();
            }
            catch(SQLException ex)
            {
                LOG.debug("logPlayEnd failed: {}", ex.getMessage());
            }
            closeAllListenerSessions(guild.getIdLong(), sessionId);
        });
    }

    public void logSkipVote(Guild guild, User user, AudioTrack track)
    {
        logQueueEventWithMeta(guild, user, track, "SKIP_VOTE", null, null, null, null, null);
    }

    public void logSkipAction(Guild guild, User user, AudioTrack track, String reason)
    {
        logQueueEventWithMeta(guild, user, track, "SKIP_"+reason, null, null, null, null, null);
    }

    public void logCommandEvent(Guild guild, User user, String command, String args, String result, String metaJson)
    {
        if(guild == null || command == null)
            return;
        submit(() ->
        {
            try
            {
                ensureGuild(guild);
                if(user != null)
                    ensureUserAndProfile(guild, user, null);
                try(PreparedStatement ps = connection.prepareStatement(
                        "INSERT INTO command_events (guild_id, user_id, command, args, result, event_meta, created_at) VALUES (?,?,?,?,?,?,?)"))
                {
                    ps.setLong(1, guild.getIdLong());
                    ps.setLong(2, user == null ? 0L : user.getIdLong());
                    ps.setString(3, command);
                    ps.setString(4, args);
                    ps.setString(5, result);
                    ps.setString(6, metaJson);
                    ps.setLong(7, now());
                    ps.executeUpdate();
                }
            }
            catch(SQLException ex)
            {
                LOG.debug("logCommandEvent failed: {}", ex.getMessage());
            }
        });
    }

    public void logQueueEventWithMeta(Guild guild, User user, AudioTrack track, String eventType, String source, Integer position, String query, String playlistName, String metaJson)
    {
        if(guild == null || eventType == null)
            return;
        submit(() ->
        {
            try
            {
                ensureGuild(guild);
                if(user != null)
                    ensureUserAndProfile(guild, user, null);
                Long trackId = null;
                if(track != null)
                    trackId = ensureTrack(track);
                try(PreparedStatement ps = connection.prepareStatement(
                        "INSERT INTO queue_events (guild_id, user_id, track_id, event_type, source, position, query, playlist_name, event_meta, created_at) VALUES (?,?,?,?,?,?,?,?,?,?)"))
                {
                    ps.setLong(1, guild.getIdLong());
                    ps.setLong(2, user == null ? 0L : user.getIdLong());
                    if(trackId == null) ps.setNull(3, Types.INTEGER); else ps.setLong(3, trackId);
                    ps.setString(4, eventType);
                    ps.setString(5, source);
                    if(position == null) ps.setNull(6, Types.INTEGER); else ps.setInt(6, position);
                    ps.setString(7, query);
                    ps.setString(8, playlistName);
                    ps.setString(9, metaJson);
                    ps.setLong(10, now());
                    ps.executeUpdate();
                }
            }
            catch(SQLException ex)
            {
                LOG.debug("logQueueEvent failed: {}", ex.getMessage());
            }
        });
    }

    public void logSearchEventWithMeta(Guild guild, User user, String query, Integer selectedIndex, AudioTrack track, String metaJson)
    {
        if(guild == null)
            return;
        submit(() ->
        {
            try
            {
                ensureGuild(guild);
                if(user != null)
                    ensureUserAndProfile(guild, user, null);
                Long trackId = null;
                if(track != null)
                    trackId = ensureTrack(track);
                try(PreparedStatement ps = connection.prepareStatement(
                        "INSERT INTO search_events (guild_id, user_id, query, selected_index, track_id, event_meta, created_at) VALUES (?,?,?,?,?,?,?)"))
                {
                    ps.setLong(1, guild.getIdLong());
                    ps.setLong(2, user == null ? 0L : user.getIdLong());
                    ps.setString(3, query);
                    if(selectedIndex == null) ps.setNull(4, Types.INTEGER); else ps.setInt(4, selectedIndex);
                    if(trackId == null) ps.setNull(5, Types.INTEGER); else ps.setLong(5, trackId);
                    ps.setString(6, metaJson);
                    ps.setLong(7, now());
                    ps.executeUpdate();
                }
            }
            catch(SQLException ex)
            {
                LOG.debug("logSearchEvent failed: {}", ex.getMessage());
            }
        });
    }

    public void logPlaylistError(Guild guild, User user, String playlistName, String item, String reason)
    {
        JSONObject meta = new JSONObject();
        if(playlistName != null)
            meta.put("playlist_name", playlistName);
        if(item != null)
            meta.put("playlist_item", item);
        if(reason != null)
            meta.put("error_reason", reason);
        logQueueEventWithMeta(guild, user, null, "PLAYLIST_ERROR", "PLAYLIST", null, null, playlistName,
                meta.length() == 0 ? null : meta.toString());
    }

    public void onVoiceUpdate(GuildVoiceUpdateEvent event)
    {
        if(event == null)
            return;
        Guild guild = event.getGuild();
        Long sessionId = currentPlaySessionId.get(guild.getIdLong());
        if(sessionId == null)
            return;
        Member member = event.getMember();
        if(member.getUser().isBot())
            return;
        AudioManager am = guild.getAudioManager();
        AudioChannelUnion channel = am.getConnectedChannel();
        if(channel == null)
            return;

        boolean inChannel = member.getVoiceState().inAudioChannel() && channel.equals(member.getVoiceState().getChannel());
        boolean listening = inChannel && !member.getVoiceState().isDeafened();
        long userId = member.getUser().getIdLong();
        Map<Long, Long> listenerMap = activeListeners.computeIfAbsent(guild.getIdLong(), id -> new ConcurrentHashMap<>());

        if(listening && !listenerMap.containsKey(userId))
        {
            submit(() -> startListenerSession(guild, sessionId, userId));
        }
        else if(!listening && listenerMap.containsKey(userId))
        {
            submit(() -> endListenerSession(guild.getIdLong(), sessionId, userId));
        }
    }

    private void startListenerSession(Guild guild, long sessionId, long userId)
    {
        Map<Long, Long> listenerMap = activeListeners.computeIfAbsent(guild.getIdLong(), id -> new ConcurrentHashMap<>());
        if(listenerMap.containsKey(userId))
            return;
        try(PreparedStatement ps = connection.prepareStatement(
                "INSERT INTO listener_sessions (play_session_id, user_id, joined_at) VALUES (?,?,?)",
                Statement.RETURN_GENERATED_KEYS))
        {
            ps.setLong(1, sessionId);
            ps.setLong(2, userId);
            ps.setLong(3, now());
            ps.executeUpdate();
            try(ResultSet rs = ps.getGeneratedKeys())
            {
                if(rs.next())
                {
                    listenerMap.put(userId, rs.getLong(1));
                }
            }
        }
        catch(SQLException ex)
        {
            LOG.debug("startListenerSession failed: {}", ex.getMessage());
        }
    }

    private void endListenerSession(long guildId, long sessionId, long userId)
    {
        Map<Long, Long> listenerMap = activeListeners.computeIfAbsent(guildId, id -> new ConcurrentHashMap<>());
        Long listenerSessionId = listenerMap.remove(userId);
        if(listenerSessionId == null)
            return;
        try(PreparedStatement ps = connection.prepareStatement(
                "UPDATE listener_sessions SET left_at=? WHERE id=?"))
        {
            ps.setLong(1, now());
            ps.setLong(2, listenerSessionId);
            ps.executeUpdate();
        }
        catch(SQLException ex)
        {
            LOG.debug("endListenerSession failed: {}", ex.getMessage());
        }
    }

    private void closeAllListenerSessions(long guildId, long sessionId)
    {
        Map<Long, Long> listenerMap = activeListeners.remove(guildId);
        if(listenerMap == null)
            return;
        listenerMap.forEach((userId, listenerSessionId) ->
        {
            try(PreparedStatement ps = connection.prepareStatement(
                    "UPDATE listener_sessions SET left_at=? WHERE id=?"))
            {
                ps.setLong(1, now());
                ps.setLong(2, listenerSessionId);
                ps.executeUpdate();
            }
            catch(SQLException ex)
            {
                LOG.debug("closeListenerSession failed: {}", ex.getMessage());
            }
        });
    }

    private void ensureGuild(Guild guild) throws SQLException
    {
        try(PreparedStatement ps = connection.prepareStatement("UPDATE guilds SET name=? WHERE guild_id=?"))
        {
            ps.setString(1, guild.getName());
            ps.setLong(2, guild.getIdLong());
            int updated = ps.executeUpdate();
            if(updated == 0)
            {
                try(PreparedStatement ins = connection.prepareStatement(
                        "INSERT INTO guilds (guild_id, name, first_seen_at) VALUES (?,?,?)"))
                {
                    ins.setLong(1, guild.getIdLong());
                    ins.setString(2, guild.getName());
                    ins.setLong(3, now());
                    ins.executeUpdate();
                }
            }
        }
    }

    private void ensureColumnExists(String table, String column, String type) throws SQLException
    {
        if(table == null || column == null || type == null)
            return;
        try(Statement st = connection.createStatement();
            ResultSet rs = st.executeQuery("PRAGMA table_info(" + table + ")"))
        {
            while(rs.next())
            {
                String name = rs.getString("name");
                if(column.equalsIgnoreCase(name))
                    return;
            }
        }
        try(Statement st = connection.createStatement())
        {
            st.execute("ALTER TABLE " + table + " ADD COLUMN " + column + " " + type);
        }
    }

    private void ensureUserAndProfile(Guild guild, User user, RequestMetadata rm) throws SQLException
    {
        long userId = user != null ? user.getIdLong() : rm.user.id;
        try(PreparedStatement ps = connection.prepareStatement("INSERT INTO users (user_id) VALUES (?)"))
        {
            ps.setLong(1, userId);
            ps.executeUpdate();
        }
        catch(SQLException ignore)
        {
            // ignore duplicates
        }
        String username = user != null ? user.getName() : rm.user.username;
        String discrim = user != null ? user.getDiscriminator() : rm.user.discrim;
        String avatar = user != null ? user.getEffectiveAvatarUrl() : rm.user.avatar;
        String nickname = null;
        if(user != null)
        {
            Member member = guild.getMember(user);
            nickname = member == null ? null : member.getNickname();
        }
        long now = now();
        try(PreparedStatement ps = connection.prepareStatement(
                "SELECT username, discrim, avatar_url, nickname FROM user_profiles WHERE user_id=? AND guild_id=? ORDER BY seen_at DESC LIMIT 1"))
        {
            ps.setLong(1, userId);
            ps.setLong(2, guild.getIdLong());
            try(ResultSet rs = ps.executeQuery())
            {
                boolean changed = true;
                if(rs.next())
                {
                    changed = !safeEquals(rs.getString(1), username)
                            || !safeEquals(rs.getString(2), discrim)
                            || !safeEquals(rs.getString(3), avatar)
                            || !safeEquals(rs.getString(4), nickname);
                }
                if(changed)
                {
                    try(PreparedStatement ins = connection.prepareStatement(
                            "INSERT INTO user_profiles (user_id, guild_id, username, discrim, avatar_url, nickname, seen_at) VALUES (?,?,?,?,?,?,?)"))
                    {
                        ins.setLong(1, userId);
                        ins.setLong(2, guild.getIdLong());
                        ins.setString(3, username);
                        ins.setString(4, discrim);
                        ins.setString(5, avatar);
                        ins.setString(6, nickname);
                        ins.setLong(7, now);
                        ins.executeUpdate();
                    }
                }
            }
        }
    }

    private long ensureTrack(AudioTrack track) throws SQLException
    {
        AudioTrackInfo info = track.getInfo();
        String source = inferSource(info);
        try(PreparedStatement ps = connection.prepareStatement(
                "SELECT id FROM tracks WHERE source=? AND identifier=?"))
        {
            ps.setString(1, source);
            ps.setString(2, track.getIdentifier());
            try(ResultSet rs = ps.executeQuery())
            {
                if(rs.next())
                    return rs.getLong(1);
            }
        }
        try(PreparedStatement ins = connection.prepareStatement(
                "INSERT INTO tracks (source, identifier, uri, title, author, length_ms, is_stream) VALUES (?,?,?,?,?,?,?)",
                Statement.RETURN_GENERATED_KEYS))
        {
            ins.setString(1, source);
            ins.setString(2, track.getIdentifier());
            ins.setString(3, info.uri);
            ins.setString(4, info.title);
            ins.setString(5, info.author);
            ins.setLong(6, info.length);
            ins.setInt(7, info.isStream ? 1 : 0);
            ins.executeUpdate();
            try(ResultSet rs = ins.getGeneratedKeys())
            {
                if(rs.next())
                    return rs.getLong(1);
            }
        }
        catch(SQLException ignore)
        {
            // duplicate or insert failure; fall through to select
        }
        try(PreparedStatement ps = connection.prepareStatement(
                "SELECT id FROM tracks WHERE source=? AND identifier=?"))
        {
            ps.setString(1, source);
            ps.setString(2, track.getIdentifier());
            try(ResultSet rs = ps.executeQuery())
            {
                if(rs.next())
                    return rs.getLong(1);
            }
        }
        return -1L;
    }

    private String inferSource(AudioTrackInfo info)
    {
        if(info == null || info.uri == null)
            return "unknown";
        String uri = info.uri.toLowerCase();
        if(uri.contains("youtube.com") || uri.contains("youtu.be"))
            return "youtube";
        if(uri.contains("soundcloud.com"))
            return "soundcloud";
        if(uri.contains("bandcamp.com"))
            return "bandcamp";
        if(uri.contains("vimeo.com"))
            return "vimeo";
        if(uri.contains("twitch.tv"))
            return "twitch";
        return "other";
    }

    private void submit(Runnable r)
    {
        executor.submit(r);
    }

    private static long now()
    {
        return Instant.now().toEpochMilli();
    }

    private static boolean safeEquals(String a, String b)
    {
        if(a == null && b == null)
            return true;
        if(a == null || b == null)
            return false;
        return a.equals(b);
    }

}
