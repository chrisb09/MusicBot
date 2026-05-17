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
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
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

    public long getGuildTrackPlayCount(Guild guild, AudioTrack track)
    {
        return getGuildTrackPlayCount(guild, track, StatsTimeRange.all());
    }

    public long getGuildTrackPlayCount(Guild guild, AudioTrack track, StatsTimeRange range)
    {
        if(guild == null || track == null)
            return 0L;
        AudioTrackInfo info = track.getInfo();
        return getGuildTrackPlayCount(guild.getIdLong(), inferSource(info), track.getIdentifier(), range);
    }

    public long getGuildTrackPlayCount(long guildId, String source, String identifier)
    {
        return getGuildTrackPlayCount(guildId, source, identifier, StatsTimeRange.all());
    }

    public long getGuildTrackPlayCount(long guildId, String source, String identifier, StatsTimeRange range)
    {
        if(identifier == null)
            return 0L;
        StatsTimeRange effectiveRange = range == null ? StatsTimeRange.all() : range;
        return query(() ->
        {
            StringBuilder sql = new StringBuilder("SELECT COUNT(*) FROM play_sessions ps JOIN tracks t ON ps.track_id=t.id WHERE ps.guild_id=? AND t.source=? AND t.identifier=?");
            if(effectiveRange.getStartMillis() != null)
                sql.append(" AND ps.started_at>=?");
            if(effectiveRange.getEndMillis() != null)
                sql.append(" AND ps.started_at<?");
            try(PreparedStatement ps = connection.prepareStatement(sql.toString()))
            {
                ps.setLong(1, guildId);
                ps.setString(2, StatsFilters.normalizeSource(source));
                ps.setString(3, identifier);
                int index = 4;
                if(effectiveRange.getStartMillis() != null)
                    ps.setLong(index++, effectiveRange.getStartMillis());
                if(effectiveRange.getEndMillis() != null)
                    ps.setLong(index, effectiveRange.getEndMillis());
                try(ResultSet rs = ps.executeQuery())
                {
                    return rs.next() ? rs.getLong(1) : 0L;
                }
            }
        }, 0L);
    }

    public StatsSummary getStatsSummary(long guildId, StatsTimeRange range, String source, Long userId)
    {
        StatsTimeRange effectiveRange = range == null ? StatsTimeRange.all() : range;
        String effectiveSource = StatsFilters.normalizeSource(source);
        return query(() -> buildStatsSummary(guildId, effectiveRange, effectiveSource, userId),
                new StatsSummary(effectiveRange, effectiveSource, 0L, 0L,
                        Collections.emptyList(), Collections.emptyList(), Collections.emptyList(),
                        Collections.emptyList(), Collections.emptyList(), Collections.emptyList()));
    }

    public List<StatsRow> getTopRequestedTracks(long guildId, StatsTimeRange range, String source, List<Long> userIds, int limit)
    {
        StatsTimeRange effectiveRange = range == null ? StatsTimeRange.all() : range;
        String effectiveSource = StatsFilters.normalizeSource(source);
        return query(() -> topRequestedTracks(guildId, effectiveRange, effectiveSource, userIds, Math.max(1, limit)),
                Collections.emptyList());
    }

    public List<StatsRow> getTopListenedTracks(long guildId, StatsTimeRange range, String source, List<Long> userIds, int limit)
    {
        StatsTimeRange effectiveRange = range == null ? StatsTimeRange.all() : range;
        String effectiveSource = StatsFilters.normalizeSource(source);
        return query(() -> topListenedTracks(guildId, effectiveRange, effectiveSource, userIds, Math.max(1, limit)),
                Collections.emptyList());
    }

    public List<StatsRow> findUsersByProfile(long guildId, String queryText, int limit)
    {
        if(queryText == null || queryText.trim().isEmpty())
            return Collections.emptyList();
        String like = "%" + queryText.trim().toLowerCase() + "%";
        return query(() ->
        {
            try(PreparedStatement ps = connection.prepareStatement(
                    "SELECT up.user_id, COALESCE(up.nickname, up.username) label FROM user_profiles up "
                            + "WHERE up.guild_id=? AND up.seen_at=(SELECT MAX(seen_at) FROM user_profiles latest WHERE latest.guild_id=up.guild_id AND latest.user_id=up.user_id) "
                            + "AND (LOWER(COALESCE(up.username, '')) LIKE ? OR LOWER(COALESCE(up.nickname, '')) LIKE ?) "
                            + "ORDER BY label ASC LIMIT ?"))
            {
                ps.setLong(1, guildId);
                ps.setString(2, like);
                ps.setString(3, like);
                ps.setInt(4, Math.max(1, limit));
                List<StatsRow> rows = new ArrayList<>();
                try(ResultSet rs = ps.executeQuery())
                {
                    while(rs.next())
                    {
                        long userId = rs.getLong("user_id");
                        rows.add(new StatsRow(userId, userLabel(rs.getString("label"), userId), null, 0L, 0L));
                    }
                }
                return rows;
            }
        }, Collections.emptyList());
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

    private <T> T query(SqlSupplier<T> supplier, T fallback)
    {
        Future<T> future = executor.submit(() ->
        {
            try
            {
                return supplier.get();
            }
            catch(SQLException ex)
            {
                LOG.debug("stats query failed: {}", ex.getMessage());
                return fallback;
            }
        });
        try
        {
            return future.get(5, TimeUnit.SECONDS);
        }
        catch(Exception ex)
        {
            future.cancel(true);
            LOG.debug("stats query timed out or failed: {}", ex.getMessage());
            return fallback;
        }
    }

    private StatsSummary buildStatsSummary(long guildId, StatsTimeRange range, String source, Long userId) throws SQLException
    {
        long totalPlays = countPlays(guildId, range, source, userId);
        long totalListeningMillis = sumListeningMillis(guildId, range, source, userId);
        return new StatsSummary(range, source, totalPlays, totalListeningMillis,
                topTracks(guildId, range, source, userId, 10),
                topUsersFromPlaySessions(guildId, range, source, userId, 10),
                topListeners(guildId, range, source, userId, 10),
                topSources(guildId, range, userId, 10),
                topSkippers(guildId, range, source, userId, true, 10),
                topSkippers(guildId, range, source, userId, false, 10));
    }

    private long countPlays(long guildId, StatsTimeRange range, String source, Long userId) throws SQLException
    {
        StringBuilder sql = new StringBuilder("SELECT COUNT(*) FROM play_sessions ps JOIN tracks t ON ps.track_id=t.id WHERE ps.guild_id=?");
        appendPlayFilters(sql, range, source, userId);
        try(PreparedStatement ps = connection.prepareStatement(sql.toString()))
        {
            bindPlayFilters(ps, 1, guildId, range, source, userId);
            try(ResultSet rs = ps.executeQuery())
            {
                return rs.next() ? rs.getLong(1) : 0L;
            }
        }
    }

    private long sumListeningMillis(long guildId, StatsTimeRange range, String source, Long userId) throws SQLException
    {
        String upperBound = boundedListenerUpperExpression();
        StringBuilder sql = new StringBuilder("SELECT SUM(CASE "
                + "WHEN " + upperBound + " > MAX(ls.joined_at, COALESCE(ps.started_at, ls.joined_at)) "
                + "THEN " + upperBound + " - MAX(ls.joined_at, COALESCE(ps.started_at, ls.joined_at)) "
                + "ELSE 0 END) "
                + "FROM listener_sessions ls JOIN play_sessions ps ON ls.play_session_id=ps.id JOIN tracks t ON ps.track_id=t.id WHERE ps.guild_id=?");
        appendPlayFilters(sql, range, source, null);
        if(userId != null)
            sql.append(" AND ls.user_id=?");
        try(PreparedStatement ps = connection.prepareStatement(sql.toString()))
        {
            int index = 1;
            long now = now();
            ps.setLong(index++, now);
            ps.setLong(index++, now);
            ps.setLong(index++, now);
            ps.setLong(index++, now);
            index = bindPlayFilters(ps, index, guildId, range, source, null);
            if(userId != null)
                ps.setLong(index, userId);
            try(ResultSet rs = ps.executeQuery())
            {
                return rs.next() ? rs.getLong(1) : 0L;
            }
        }
    }

    private List<StatsRow> topTracks(long guildId, StatsTimeRange range, String source, Long userId, int limit) throws SQLException
    {
        StringBuilder sql = new StringBuilder("SELECT t.id, t.title, t.author, COUNT(*) plays FROM play_sessions ps JOIN tracks t ON ps.track_id=t.id WHERE ps.guild_id=?");
        appendPlayFilters(sql, range, source, userId);
        sql.append(" GROUP BY t.id, t.title, t.author ORDER BY plays DESC, t.title ASC LIMIT ?");
        try(PreparedStatement ps = connection.prepareStatement(sql.toString()))
        {
            int index = bindPlayFilters(ps, 1, guildId, range, source, userId);
            ps.setInt(index, limit);
            List<StatsRow> rows = new ArrayList<>();
            try(ResultSet rs = ps.executeQuery())
            {
                while(rs.next())
                    rows.add(new StatsRow(rs.getLong("id"), safeLabel(rs.getString("title"), "Unknown track"), rs.getString("author"), rs.getLong("plays"), 0L));
            }
            return rows;
        }
    }

    private List<StatsRow> topRequestedTracks(long guildId, StatsTimeRange range, String source, List<Long> userIds, int limit) throws SQLException
    {
        StringBuilder sql = new StringBuilder("SELECT t.id, t.title, t.author, COUNT(*) plays FROM play_sessions ps JOIN tracks t ON ps.track_id=t.id WHERE ps.guild_id=?");
        appendPlayFilters(sql, range, source, null);
        appendUserListFilter(sql, "ps.requested_by", userIds);
        sql.append(" GROUP BY t.id, t.title, t.author ORDER BY plays DESC, t.title ASC LIMIT ?");
        try(PreparedStatement ps = connection.prepareStatement(sql.toString()))
        {
            int index = bindPlayFilters(ps, 1, guildId, range, source, null);
            index = bindUserList(ps, index, userIds);
            ps.setInt(index, limit);
            List<StatsRow> rows = new ArrayList<>();
            try(ResultSet rs = ps.executeQuery())
            {
                while(rs.next())
                    rows.add(new StatsRow(rs.getLong("id"), safeLabel(rs.getString("title"), "Unknown track"), rs.getString("author"), rs.getLong("plays"), 0L));
            }
            return rows;
        }
    }

    private List<StatsRow> topListenedTracks(long guildId, StatsTimeRange range, String source, List<Long> userIds, int limit) throws SQLException
    {
        String upperBound = boundedListenerUpperExpression();
        StringBuilder sql = new StringBuilder("SELECT t.id, t.title, t.author, COUNT(*) listens, "
                + "SUM(CASE "
                + "WHEN " + upperBound + " > MAX(ls.joined_at, COALESCE(ps.started_at, ls.joined_at)) "
                + "THEN " + upperBound + " - MAX(ls.joined_at, COALESCE(ps.started_at, ls.joined_at)) "
                + "ELSE 0 END) listen_ms "
                + "FROM listener_sessions ls JOIN play_sessions ps ON ls.play_session_id=ps.id JOIN tracks t ON ps.track_id=t.id WHERE ps.guild_id=?");
        appendPlayFilters(sql, range, source, null);
        appendUserListFilter(sql, "ls.user_id", userIds);
        sql.append(" GROUP BY t.id, t.title, t.author ORDER BY listen_ms DESC, listens DESC, t.title ASC LIMIT ?");
        try(PreparedStatement ps = connection.prepareStatement(sql.toString()))
        {
            int index = 1;
            long current = now();
            ps.setLong(index++, current);
            ps.setLong(index++, current);
            ps.setLong(index++, current);
            ps.setLong(index++, current);
            index = bindPlayFilters(ps, index, guildId, range, source, null);
            index = bindUserList(ps, index, userIds);
            ps.setInt(index, limit);
            List<StatsRow> rows = new ArrayList<>();
            try(ResultSet rs = ps.executeQuery())
            {
                while(rs.next())
                    rows.add(new StatsRow(rs.getLong("id"), safeLabel(rs.getString("title"), "Unknown track"), rs.getString("author"), rs.getLong("listens"), rs.getLong("listen_ms")));
            }
            return rows;
        }
    }

    private List<StatsRow> topUsersFromPlaySessions(long guildId, StatsTimeRange range, String source, Long userId, int limit) throws SQLException
    {
        StringBuilder sql = new StringBuilder("SELECT ps.requested_by user_id, " + userLabelSql("ps.requested_by", "ps.guild_id") + " label, COUNT(*) plays "
                + "FROM play_sessions ps JOIN tracks t ON ps.track_id=t.id WHERE ps.guild_id=? AND ps.requested_by<>0");
        appendPlayFilters(sql, range, source, userId);
        sql.append(" GROUP BY ps.requested_by ORDER BY plays DESC LIMIT ?");
        try(PreparedStatement ps = connection.prepareStatement(sql.toString()))
        {
            int index = bindPlayFilters(ps, 1, guildId, range, source, userId);
            ps.setInt(index, limit);
            return readCountRows(ps, "user_id", "label", "plays");
        }
    }

    private List<StatsRow> topListeners(long guildId, StatsTimeRange range, String source, Long userId, int limit) throws SQLException
    {
        String upperBound = boundedListenerUpperExpression();
        StringBuilder sql = new StringBuilder("SELECT ls.user_id, " + userLabelSql("ls.user_id", "ps.guild_id") + " label, "
                + "SUM(CASE "
                + "WHEN " + upperBound + " > MAX(ls.joined_at, COALESCE(ps.started_at, ls.joined_at)) "
                + "THEN " + upperBound + " - MAX(ls.joined_at, COALESCE(ps.started_at, ls.joined_at)) "
                + "ELSE 0 END) listen_ms "
                + "FROM listener_sessions ls JOIN play_sessions ps ON ls.play_session_id=ps.id JOIN tracks t ON ps.track_id=t.id WHERE ps.guild_id=?");
        appendPlayFilters(sql, range, source, null);
        if(userId != null)
            sql.append(" AND ls.user_id=?");
        sql.append(" GROUP BY ls.user_id ORDER BY listen_ms DESC LIMIT ?");
        try(PreparedStatement ps = connection.prepareStatement(sql.toString()))
        {
            int index = 1;
            long current = now();
            ps.setLong(index++, current);
            ps.setLong(index++, current);
            ps.setLong(index++, current);
            ps.setLong(index++, current);
            index = bindPlayFilters(ps, index, guildId, range, source, null);
            if(userId != null)
                ps.setLong(index++, userId);
            ps.setInt(index, limit);
            List<StatsRow> rows = new ArrayList<>();
            try(ResultSet rs = ps.executeQuery())
            {
                while(rs.next())
                    rows.add(new StatsRow(rs.getLong("user_id"), userLabel(rs.getString("label"), rs.getLong("user_id")), null, 0L, rs.getLong("listen_ms")));
            }
            return rows;
        }
    }

    private List<StatsRow> topSources(long guildId, StatsTimeRange range, Long userId, int limit) throws SQLException
    {
        StringBuilder sql = new StringBuilder("SELECT t.source, COUNT(*) plays FROM play_sessions ps JOIN tracks t ON ps.track_id=t.id WHERE ps.guild_id=?");
        appendPlayFilters(sql, range, null, userId);
        sql.append(" GROUP BY t.source ORDER BY plays DESC, t.source ASC LIMIT ?");
        try(PreparedStatement ps = connection.prepareStatement(sql.toString()))
        {
            int index = bindPlayFilters(ps, 1, guildId, range, null, userId);
            ps.setInt(index, limit);
            List<StatsRow> rows = new ArrayList<>();
            try(ResultSet rs = ps.executeQuery())
            {
                while(rs.next())
                    rows.add(new StatsRow(0L, safeLabel(rs.getString("source"), "unknown"), null, rs.getLong("plays"), 0L));
            }
            return rows;
        }
    }

    private List<StatsRow> topSkippers(long guildId, StatsTimeRange range, String source, Long userId, boolean votes, int limit) throws SQLException
    {
        StringBuilder sql = new StringBuilder("SELECT qe.user_id, " + userLabelSql("qe.user_id", "qe.guild_id") + " label, COUNT(*) skips "
                + "FROM queue_events qe LEFT JOIN tracks t ON qe.track_id=t.id WHERE qe.guild_id=? AND qe.user_id<>0");
        if(votes)
            sql.append(" AND qe.event_type='SKIP_VOTE'");
        else
            sql.append(" AND qe.event_type LIKE 'SKIP_%' AND qe.event_type<>'SKIP_VOTE'");
        appendQueueFilters(sql, range, source, userId);
        sql.append(" GROUP BY qe.user_id ORDER BY skips DESC LIMIT ?");
        try(PreparedStatement ps = connection.prepareStatement(sql.toString()))
        {
            int index = bindQueueFilters(ps, 1, guildId, range, source, userId);
            ps.setInt(index, limit);
            return readCountRows(ps, "user_id", "label", "skips");
        }
    }

    private List<StatsRow> readCountRows(PreparedStatement ps, String idColumn, String labelColumn, String countColumn) throws SQLException
    {
        List<StatsRow> rows = new ArrayList<>();
        try(ResultSet rs = ps.executeQuery())
        {
            while(rs.next())
            {
                long id = rs.getLong(idColumn);
                rows.add(new StatsRow(id, userLabel(rs.getString(labelColumn), id), null, rs.getLong(countColumn), 0L));
            }
        }
        return rows;
    }

    private void appendPlayFilters(StringBuilder sql, StatsTimeRange range, String source, Long userId)
    {
        if(range != null && range.getStartMillis() != null)
            sql.append(" AND ps.started_at>=?");
        if(range != null && range.getEndMillis() != null)
            sql.append(" AND ps.started_at<?");
        if(source != null)
            sql.append(" AND t.source=?");
        if(userId != null)
            sql.append(" AND ps.requested_by=?");
    }

    private int bindPlayFilters(PreparedStatement ps, int index, long guildId, StatsTimeRange range, String source, Long userId) throws SQLException
    {
        ps.setLong(index++, guildId);
        if(range != null && range.getStartMillis() != null)
            ps.setLong(index++, range.getStartMillis());
        if(range != null && range.getEndMillis() != null)
            ps.setLong(index++, range.getEndMillis());
        if(source != null)
            ps.setString(index++, source);
        if(userId != null)
            ps.setLong(index++, userId);
        return index;
    }

    private void appendQueueFilters(StringBuilder sql, StatsTimeRange range, String source, Long userId)
    {
        if(range != null && range.getStartMillis() != null)
            sql.append(" AND qe.created_at>=?");
        if(range != null && range.getEndMillis() != null)
            sql.append(" AND qe.created_at<?");
        if(source != null)
            sql.append(" AND t.source=?");
        if(userId != null)
            sql.append(" AND qe.user_id=?");
    }

    private int bindQueueFilters(PreparedStatement ps, int index, long guildId, StatsTimeRange range, String source, Long userId) throws SQLException
    {
        ps.setLong(index++, guildId);
        if(range != null && range.getStartMillis() != null)
            ps.setLong(index++, range.getStartMillis());
        if(range != null && range.getEndMillis() != null)
            ps.setLong(index++, range.getEndMillis());
        if(source != null)
            ps.setString(index++, source);
        if(userId != null)
            ps.setLong(index++, userId);
        return index;
    }

    private void appendUserListFilter(StringBuilder sql, String column, List<Long> userIds)
    {
        if(userIds == null || userIds.isEmpty())
            return;
        sql.append(" AND ").append(column).append(" IN (");
        for(int i = 0; i < userIds.size(); i++)
        {
            if(i > 0)
                sql.append(",");
            sql.append("?");
        }
        sql.append(")");
    }

    private int bindUserList(PreparedStatement ps, int index, List<Long> userIds) throws SQLException
    {
        if(userIds == null)
            return index;
        for(Long userId : userIds)
            ps.setLong(index++, userId);
        return index;
    }

    private String userLabelSql(String userIdExpression, String guildIdExpression)
    {
        return "(SELECT COALESCE(nickname, username) FROM user_profiles up WHERE up.user_id=" + userIdExpression + " AND up.guild_id=" + guildIdExpression + " ORDER BY seen_at DESC LIMIT 1)";
    }

    private String boundedListenerUpperExpression()
    {
        String fallbackEnd = "COALESCE(ps.ended_at, CASE "
                + "WHEN ps.duration_ms IS NOT NULL THEN COALESCE(ps.started_at, ls.joined_at) + ps.duration_ms "
                + "WHEN t.is_stream=0 AND t.length_ms IS NOT NULL THEN COALESCE(ps.started_at, ls.joined_at) + t.length_ms "
                + "ELSE ? END)";
        return "MIN(COALESCE(ls.left_at, " + fallbackEnd + "), " + fallbackEnd + ")";
    }

    private String userLabel(String label, long userId)
    {
        return safeLabel(label, "<@" + userId + ">");
    }

    private String safeLabel(String label, String fallback)
    {
        return label == null || label.trim().isEmpty() ? fallback : label;
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

    private interface SqlSupplier<T>
    {
        T get() throws SQLException;
    }

}
