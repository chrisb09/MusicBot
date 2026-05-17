/*
 * Copyright 2026
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package com.jagrosh.jmusicbot.settings;

public enum StatsReportFrequency
{
    OFF,
    MONTHLY,
    YEARLY;

    public static StatsReportFrequency parse(String value)
    {
        if(value == null)
            return OFF;
        for(StatsReportFrequency frequency : values())
            if(frequency.name().equalsIgnoreCase(value))
                return frequency;
        return OFF;
    }
}
