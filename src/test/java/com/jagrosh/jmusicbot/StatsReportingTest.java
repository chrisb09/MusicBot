/*
 * Copyright 2026
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package com.jagrosh.jmusicbot;

import com.jagrosh.jmusicbot.datalog.DataLogService;
import com.jagrosh.jmusicbot.datalog.StatsAccess;
import com.jagrosh.jmusicbot.datalog.StatsFilters;
import com.jagrosh.jmusicbot.datalog.StatsMessageFormatter;
import com.jagrosh.jmusicbot.datalog.StatsSummary;
import com.jagrosh.jmusicbot.datalog.StatsTimeRange;
import com.jagrosh.jmusicbot.datalog.StatsRow;
import java.lang.reflect.Field;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.List;
import java.util.Arrays;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.MessageEmbed;
import org.junit.Test;

import static org.junit.Assert.*;

public class StatsReportingTest
{
    @Test
    public void parsesRangesAndSources()
    {
        assertEquals("all", StatsTimeRange.parse("all").getKey());
        assertEquals("today", StatsTimeRange.parse("today").getKey());
        assertEquals("week", StatsTimeRange.parse("week").getKey());
        assertEquals("month", StatsTimeRange.parse("month").getKey());
        assertEquals("year", StatsTimeRange.parse("year").getKey());
        assertEquals("60d", StatsTimeRange.parse("60d").getKey());
        assertEquals("1y", StatsTimeRange.parse("1y").getKey());
        assertEquals("2026-04", StatsTimeRange.month(2026, 4).getKey());
        assertEquals("2025", StatsTimeRange.year(2025).getKey());
        assertNull(StatsTimeRange.month(2026, 13));
        assertNull(StatsTimeRange.parse("forever-ish"));

        assertEquals("youtube", StatsFilters.normalizeSource("yt"));
        assertEquals("soundcloud", StatsFilters.normalizeSource("SC"));
        assertNull(StatsFilters.normalizeSource("all"));
    }

    @Test
    public void userStatsRespectSelfOrAdminAccess()
    {
        assertTrue(StatsAccess.canViewUserStats(1L, 1L, false));
        assertTrue(StatsAccess.canViewUserStats(1L, 2L, true));
        assertFalse(StatsAccess.canViewUserStats(1L, 2L, false));
    }

    @Test
    public void chunkedFieldsStayWithinDiscordLimit()
    {
        List<StatsRow> rows = Arrays.asList(
                new StatsRow(1L, repeat("Very Long Track Name ", 20), "Artist", 10L, 0L),
                new StatsRow(2L, repeat("Another Long Track Name ", 20), "Artist", 9L, 0L),
                new StatsRow(3L, repeat("Third Long Track Name ", 20), "Artist", 8L, 0L));
        EmbedBuilder builder = new EmbedBuilder();
        StatsMessageFormatter.addChunkedField(builder, "Tracks", StatsMessageFormatter.countList(rows, "plays", 3), false);
        MessageEmbed embed = builder.build();
        assertTrue(embed.getFields().size() > 1);
        for(MessageEmbed.Field field : embed.getFields())
            assertTrue(field.getValue().length() <= 1024);
    }

    @Test
    public void aggregatesPlaybackStatsFromSqlite() throws Exception
    {
        String dbName = "stats-test-" + System.nanoTime();
        DataLogService service = new DataLogService(null, "jdbc:sqlite:file:" + dbName + "?mode=memory&cache=shared", "", "");
        try
        {
            service.getStatsSummary(1L, StatsTimeRange.all(), null, null);
            Connection connection = getConnection(service);
            seedStatsData(connection);

            StatsSummary server = service.getStatsSummary(1L, StatsTimeRange.all(), null, null);
            assertEquals(5L, server.getTotalPlays());
            assertEquals(305000L, server.getTotalListeningMillis());
            assertEquals("Song A", server.getTopTracks().get(0).getLabel());
            assertEquals(3L, server.getTopTracks().get(0).getCount());
            assertEquals("youtube", server.getTopSources().get(0).getLabel());
            assertEquals(2L, server.getSkipVotes().get(0).getCount());
            assertEquals(1L, server.getSkipActions().get(0).getCount());

            StatsSummary user = service.getStatsSummary(1L, StatsTimeRange.all(), null, 100L);
            assertEquals(2L, user.getTotalPlays());
            assertEquals(120000L, user.getTotalListeningMillis());

            StatsSummary soundcloud = service.getStatsSummary(1L, StatsTimeRange.all(), "soundcloud", null);
            assertEquals(2L, soundcloud.getTotalPlays());
            assertEquals("Song B", soundcloud.getTopTracks().get(0).getLabel());

            List<StatsRow> requestedByAlice = service.getTopRequestedTracks(1L, StatsTimeRange.all(), null, java.util.Collections.singletonList(100L), 5);
            assertEquals(1, requestedByAlice.size());
            assertEquals("Song A", requestedByAlice.get(0).getLabel());
            assertEquals(2L, requestedByAlice.get(0).getCount());

            List<StatsRow> listenedByBob = service.getTopListenedTracks(1L, StatsTimeRange.all(), null, java.util.Collections.singletonList(200L), 5);
            assertEquals("Song A", listenedByBob.get(0).getLabel());
            assertEquals(150000L, listenedByBob.get(0).getMillis());

            List<StatsRow> profileMatches = service.findUsersByProfile(1L, "ali", 5);
            assertEquals(1, profileMatches.size());
            assertEquals(100L, profileMatches.get(0).getId());
        }
        finally
        {
            service.shutdown();
        }
    }

    private Connection getConnection(DataLogService service) throws Exception
    {
        Field field = DataLogService.class.getDeclaredField("connection");
        field.setAccessible(true);
        return (Connection)field.get(service);
    }

    private void seedStatsData(Connection connection) throws Exception
    {
        long now = System.currentTimeMillis();
        try(Statement st = connection.createStatement())
        {
            st.execute("INSERT INTO guilds (guild_id, name, first_seen_at) VALUES (1, 'Guild', " + now + ")");
            st.execute("INSERT INTO users (user_id) VALUES (100)");
            st.execute("INSERT INTO users (user_id) VALUES (200)");
            st.execute("INSERT INTO user_profiles (user_id, guild_id, username, seen_at) VALUES (100, 1, 'Alice', " + now + ")");
            st.execute("INSERT INTO user_profiles (user_id, guild_id, username, seen_at) VALUES (200, 1, 'Bob', " + now + ")");
            st.execute("INSERT INTO tracks (id, source, identifier, uri, title, author, length_ms, is_stream) VALUES (1, 'youtube', 'a', 'https://youtu.be/a', 'Song A', 'Artist A', 120000, 0)");
            st.execute("INSERT INTO tracks (id, source, identifier, uri, title, author, length_ms, is_stream) VALUES (2, 'soundcloud', 'b', 'https://soundcloud.com/b', 'Song B', 'Artist B', 180000, 0)");
        }
        try(PreparedStatement ps = connection.prepareStatement("INSERT INTO play_sessions (id, guild_id, track_id, requested_by, started_at, ended_at, duration_ms, listeners_at_start) VALUES (?,1,?,?,?,?,?,?)"))
        {
            insertPlay(ps, 1, 1, 100, now - 300000, now - 180000, 120000, 2);
            insertPlay(ps, 2, 1, 100, now - 170000, now - 50000, 120000, 1);
            insertPlay(ps, 3, 2, 200, now - 40000, now - 10000, 30000, 1);
            insertPlay(ps, 4, 2, 200, now - 10000, now - 5000, 5000, 1);
            insertPlay(ps, 5, 1, 200, now - 1000000000L, 0L, 0L, 1);
        }
        try(PreparedStatement ps = connection.prepareStatement("INSERT INTO listener_sessions (play_session_id, user_id, joined_at, left_at) VALUES (?,?,?,?)"))
        {
            insertListener(ps, 1, 100, now - 300000, now - 240000);
            insertListener(ps, 2, 100, now - 170000, now - 110000);
            insertListener(ps, 3, 200, now - 40000, now - 10000);
            insertListener(ps, 1, 200, now - 300000, now - 270000);
            insertListener(ps, 4, 200, now - 20000, 0L);
            insertListener(ps, 5, 200, now - 1000000000L, 0L);
        }
        try(PreparedStatement ps = connection.prepareStatement("INSERT INTO queue_events (guild_id, user_id, track_id, event_type, created_at) VALUES (1,?,?,?,?)"))
        {
            insertQueueEvent(ps, 100, 1, "SKIP_VOTE", now - 100000);
            insertQueueEvent(ps, 100, 1, "SKIP_VOTE", now - 90000);
            insertQueueEvent(ps, 200, 1, "SKIP_RATIO_MET", now - 80000);
        }
    }

    private void insertPlay(PreparedStatement ps, long id, long trackId, long requestedBy, long startedAt, long endedAt, long duration, int listeners) throws Exception
    {
        ps.setLong(1, id);
        ps.setLong(2, trackId);
        ps.setLong(3, requestedBy);
        ps.setLong(4, startedAt);
        if(endedAt == 0L)
            ps.setNull(5, java.sql.Types.BIGINT);
        else
            ps.setLong(5, endedAt);
        if(duration == 0L)
            ps.setNull(6, java.sql.Types.BIGINT);
        else
            ps.setLong(6, duration);
        ps.setInt(7, listeners);
        ps.executeUpdate();
    }

    private void insertListener(PreparedStatement ps, long sessionId, long userId, long joinedAt, long leftAt) throws Exception
    {
        ps.setLong(1, sessionId);
        ps.setLong(2, userId);
        ps.setLong(3, joinedAt);
        if(leftAt == 0L)
            ps.setNull(4, java.sql.Types.BIGINT);
        else
            ps.setLong(4, leftAt);
        ps.executeUpdate();
    }

    private void insertQueueEvent(PreparedStatement ps, long userId, long trackId, String eventType, long createdAt) throws Exception
    {
        ps.setLong(1, userId);
        ps.setLong(2, trackId);
        ps.setString(3, eventType);
        ps.setLong(4, createdAt);
        ps.executeUpdate();
    }

    private String repeat(String value, int count)
    {
        StringBuilder builder = new StringBuilder();
        for(int i = 0; i < count; i++)
            builder.append(value);
        return builder.toString();
    }
}
