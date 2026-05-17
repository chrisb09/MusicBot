/*
 * Copyright 2026
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package com.jagrosh.jmusicbot.datalog;

public final class StatsFilters
{
    private StatsFilters() {}

    public static String normalizeSource(String source)
    {
        if(source == null)
            return null;
        String normalized = source.trim().toLowerCase();
        if(normalized.isEmpty() || "all".equals(normalized) || "any".equals(normalized))
            return null;
        if("yt".equals(normalized) || "youtube.com".equals(normalized) || "youtu.be".equals(normalized))
            return "youtube";
        if("sc".equals(normalized) || "soundcloud.com".equals(normalized))
            return "soundcloud";
        return normalized;
    }
}
