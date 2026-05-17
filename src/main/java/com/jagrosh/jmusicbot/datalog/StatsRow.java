/*
 * Copyright 2026
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package com.jagrosh.jmusicbot.datalog;

public class StatsRow
{
    private final long id;
    private final String label;
    private final String detail;
    private final long count;
    private final long millis;

    public StatsRow(long id, String label, String detail, long count, long millis)
    {
        this.id = id;
        this.label = label;
        this.detail = detail;
        this.count = count;
        this.millis = millis;
    }

    public long getId()
    {
        return id;
    }

    public String getLabel()
    {
        return label;
    }

    public String getDetail()
    {
        return detail;
    }

    public long getCount()
    {
        return count;
    }

    public long getMillis()
    {
        return millis;
    }
}
