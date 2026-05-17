/*
 * Copyright 2026
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package com.jagrosh.jmusicbot.datalog;

import com.jagrosh.jmusicbot.utils.TimeUtil;
import net.dv8tion.jda.api.EmbedBuilder;

public final class StatsMessageFormatter
{
    private static final int EMBED_FIELD_VALUE_LIMIT = 1024;
    private static final int SAFE_FIELD_VALUE_LIMIT = 1000;

    private StatsMessageFormatter() {}

    public static EmbedBuilder summaryEmbed(String title, StatsSummary summary, int color)
    {
        EmbedBuilder eb = new EmbedBuilder()
                .setTitle(title)
                .setColor(color)
                .setDescription("Range: **" + summary.getRange().getLabel() + "**"
                        + (summary.getSource() == null ? "" : "\nSource: **" + summary.getSource() + "**")
                        + "\nPlays: **" + summary.getTotalPlays() + "**"
                        + "\nListening time: **" + TimeUtil.formatTime(summary.getTotalListeningMillis()) + "**");
        addCountField(eb, "Top tracks", summary.getTopTracks(), "plays");
        addTimeField(eb, "Top listeners", summary.getTopListeners());
        addCountField(eb, "Top requesters", summary.getTopRequesters(), "plays");
        addCountField(eb, "Top sources", summary.getTopSources(), "plays");
        addCountField(eb, "Skip actions", summary.getSkipActions(), "skips");
        addCountField(eb, "Skip votes", summary.getSkipVotes(), "votes");
        return eb;
    }

    public static EmbedBuilder personalEmbed(String title, StatsSummary summary, int color)
    {
        long skipActions = sumCounts(summary.getSkipActions());
        long skipVotes = sumCounts(summary.getSkipVotes());
        EmbedBuilder eb = new EmbedBuilder()
                .setTitle(title)
                .setColor(color)
                .setDescription("Range: **" + summary.getRange().getLabel() + "**"
                        + (summary.getSource() == null ? "" : "\nSource: **" + summary.getSource() + "**")
                        + "\nRequests: **" + summary.getTotalPlays() + "**"
                        + "\nListening time: **" + TimeUtil.formatTime(summary.getTotalListeningMillis()) + "**"
                        + "\nSkip actions: **" + skipActions + "**"
                        + "\nSkip votes: **" + skipVotes + "**");
        addCountField(eb, "Top tracks", summary.getTopTracks(), "plays");
        addCountField(eb, "Top sources", summary.getTopSources(), "plays");
        return eb;
    }

    public static String countList(Iterable<StatsRow> rows, String unit)
    {
        return countList(rows, unit, 5);
    }

    public static String countList(Iterable<StatsRow> rows, String unit, int limit)
    {
        StringBuilder builder = new StringBuilder();
        int index = 1;
        for(StatsRow row : rows)
        {
            if(index > limit)
                break;
            builder.append(index++).append(". **").append(row.getLabel()).append("** - ")
                    .append(row.getCount()).append(" ").append(unit);
            if(row.getDetail() != null && !row.getDetail().isEmpty())
                builder.append(" (").append(row.getDetail()).append(")");
            builder.append("\n");
        }
        return builder.length() == 0 ? "No data" : builder.toString();
    }

    public static String timeList(Iterable<StatsRow> rows)
    {
        return timeList(rows, 5);
    }

    public static String timeList(Iterable<StatsRow> rows, int limit)
    {
        StringBuilder builder = new StringBuilder();
        int index = 1;
        for(StatsRow row : rows)
        {
            if(index > limit)
                break;
            builder.append(index++).append(". **").append(row.getLabel()).append("** - ")
                    .append(TimeUtil.formatTime(row.getMillis())).append("\n");
        }
        return builder.length() == 0 ? "No data" : builder.toString();
    }

    public static String trackListenList(Iterable<StatsRow> rows, int limit)
    {
        StringBuilder builder = new StringBuilder();
        int index = 1;
        for(StatsRow row : rows)
        {
            if(index > limit)
                break;
            builder.append(index++).append(". **").append(row.getLabel()).append("** - ")
                    .append(TimeUtil.formatTime(row.getMillis()));
            if(row.getCount() > 0L)
                builder.append(" (").append(row.getCount()).append(" listen").append(row.getCount() == 1L ? "" : "s").append(")");
            if(row.getDetail() != null && !row.getDetail().isEmpty())
                builder.append(" (").append(row.getDetail()).append(")");
            builder.append("\n");
        }
        return builder.length() == 0 ? "No data" : builder.toString();
    }

    private static void addCountField(EmbedBuilder eb, String name, Iterable<StatsRow> rows, String unit)
    {
        addChunkedField(eb, name, countList(rows, unit), false);
    }

    private static void addTimeField(EmbedBuilder eb, String name, Iterable<StatsRow> rows)
    {
        addChunkedField(eb, name, timeList(rows), false);
    }

    public static void addChunkedField(EmbedBuilder eb, String name, String value, boolean inline)
    {
        if(value == null || value.isEmpty())
        {
            eb.addField(name, "No data", inline);
            return;
        }

        String[] lines = value.split("\\n");
        StringBuilder chunk = new StringBuilder();
        int part = 1;
        for(String rawLine : lines)
        {
            if(rawLine == null || rawLine.isEmpty())
                continue;
            String line = fitFieldLine(rawLine);
            int nextLength = chunk.length() + line.length() + 1;
            if(chunk.length() > 0 && nextLength > SAFE_FIELD_VALUE_LIMIT)
            {
                eb.addField(partName(name, part++), chunk.toString(), inline);
                chunk.setLength(0);
            }
            if(chunk.length() > 0)
                chunk.append("\n");
            chunk.append(line);
        }
        if(chunk.length() == 0)
            chunk.append("No data");
        eb.addField(partName(name, part), chunk.toString(), inline);
    }

    private static String partName(String name, int part)
    {
        return part <= 1 ? name : name + " " + part;
    }

    private static String fitFieldLine(String line)
    {
        if(line.length() <= EMBED_FIELD_VALUE_LIMIT)
            return line;
        return line.substring(0, SAFE_FIELD_VALUE_LIMIT - 3) + "...";
    }

    private static long sumCounts(Iterable<StatsRow> rows)
    {
        long count = 0L;
        for(StatsRow row : rows)
            count += row.getCount();
        return count;
    }
}
