/*
 * Copyright 2026
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package com.jagrosh.jmusicbot.datalog;

import java.util.Collections;
import java.util.List;

public class StatsSummary
{
    private final StatsTimeRange range;
    private final String source;
    private final long totalPlays;
    private final long totalListeningMillis;
    private final List<StatsRow> topTracks;
    private final List<StatsRow> topRequesters;
    private final List<StatsRow> topListeners;
    private final List<StatsRow> topSources;
    private final List<StatsRow> skipVotes;
    private final List<StatsRow> skipActions;

    public StatsSummary(StatsTimeRange range, String source, long totalPlays, long totalListeningMillis,
                        List<StatsRow> topTracks, List<StatsRow> topRequesters, List<StatsRow> topListeners,
                        List<StatsRow> topSources, List<StatsRow> skipVotes, List<StatsRow> skipActions)
    {
        this.range = range;
        this.source = source;
        this.totalPlays = totalPlays;
        this.totalListeningMillis = totalListeningMillis;
        this.topTracks = topTracks == null ? Collections.emptyList() : topTracks;
        this.topRequesters = topRequesters == null ? Collections.emptyList() : topRequesters;
        this.topListeners = topListeners == null ? Collections.emptyList() : topListeners;
        this.topSources = topSources == null ? Collections.emptyList() : topSources;
        this.skipVotes = skipVotes == null ? Collections.emptyList() : skipVotes;
        this.skipActions = skipActions == null ? Collections.emptyList() : skipActions;
    }

    public StatsTimeRange getRange()
    {
        return range;
    }

    public String getSource()
    {
        return source;
    }

    public long getTotalPlays()
    {
        return totalPlays;
    }

    public long getTotalListeningMillis()
    {
        return totalListeningMillis;
    }

    public List<StatsRow> getTopTracks()
    {
        return topTracks;
    }

    public List<StatsRow> getTopRequesters()
    {
        return topRequesters;
    }

    public List<StatsRow> getTopListeners()
    {
        return topListeners;
    }

    public List<StatsRow> getTopSources()
    {
        return topSources;
    }

    public List<StatsRow> getSkipVotes()
    {
        return skipVotes;
    }

    public List<StatsRow> getSkipActions()
    {
        return skipActions;
    }

    public boolean isEmpty()
    {
        return totalPlays == 0L && totalListeningMillis == 0L
                && topTracks.isEmpty() && topRequesters.isEmpty() && topListeners.isEmpty()
                && topSources.isEmpty() && skipVotes.isEmpty() && skipActions.isEmpty();
    }
}
