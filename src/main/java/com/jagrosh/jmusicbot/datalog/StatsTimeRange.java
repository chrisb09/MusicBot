/*
 * Copyright 2026
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package com.jagrosh.jmusicbot.datalog;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.Month;
import java.time.ZoneId;
import java.time.ZonedDateTime;

public class StatsTimeRange
{
    private final String key;
    private final String label;
    private final Long startMillis;
    private final Long endMillis;

    private StatsTimeRange(String key, String label, Long startMillis, Long endMillis)
    {
        this.key = key;
        this.label = label;
        this.startMillis = startMillis;
        this.endMillis = endMillis;
    }

    public static StatsTimeRange all()
    {
        return new StatsTimeRange("all", "all time", null, null);
    }

    public static StatsTimeRange parse(String token)
    {
        if(token == null || token.trim().isEmpty())
            return all();
        String normalized = token.trim().toLowerCase();
        ZonedDateTime now = ZonedDateTime.now();
        ZonedDateTime start;
        if(normalized.matches("\\d+[dwmy]"))
        {
            int amount = Integer.parseInt(normalized.substring(0, normalized.length() - 1));
            char unit = normalized.charAt(normalized.length() - 1);
            switch(unit)
            {
                case 'd':
                    start = now.minusDays(amount);
                    return new StatsTimeRange(normalized, "last " + amount + " day" + (amount == 1 ? "" : "s"), start.toInstant().toEpochMilli(), null);
                case 'w':
                    start = now.minusWeeks(amount);
                    return new StatsTimeRange(normalized, "last " + amount + " week" + (amount == 1 ? "" : "s"), start.toInstant().toEpochMilli(), null);
                case 'm':
                    start = now.minusMonths(amount);
                    return new StatsTimeRange(normalized, "last " + amount + " month" + (amount == 1 ? "" : "s"), start.toInstant().toEpochMilli(), null);
                case 'y':
                    start = now.minusYears(amount);
                    return new StatsTimeRange(normalized, "last " + amount + " year" + (amount == 1 ? "" : "s"), start.toInstant().toEpochMilli(), null);
                default:
                    break;
            }
        }
        switch(normalized)
        {
            case "today":
            case "day":
                start = now.toLocalDate().atStartOfDay(now.getZone());
                return new StatsTimeRange("today", "today", start.toInstant().toEpochMilli(), null);
            case "week":
            case "thisweek":
                LocalDate monday = now.toLocalDate().with(DayOfWeek.MONDAY);
                start = monday.atStartOfDay(now.getZone());
                return new StatsTimeRange("week", "this week", start.toInstant().toEpochMilli(), null);
            case "month":
            case "thismonth":
                start = now.withDayOfMonth(1).toLocalDate().atStartOfDay(now.getZone());
                return new StatsTimeRange("month", "this month", start.toInstant().toEpochMilli(), null);
            case "year":
            case "thisyear":
                start = now.withDayOfYear(1).toLocalDate().atStartOfDay(now.getZone());
                return new StatsTimeRange("year", "this year", start.toInstant().toEpochMilli(), null);
            case "all":
            case "alltime":
                return all();
            default:
                return null;
        }
    }

    public static StatsTimeRange previousMonth()
    {
        ZonedDateTime now = ZonedDateTime.now();
        ZonedDateTime start = now.minusMonths(1).withDayOfMonth(1).toLocalDate().atStartOfDay(now.getZone());
        ZonedDateTime end = start.plusMonths(1);
        return new StatsTimeRange("month", start.getMonth() + " " + start.getYear(), start.toInstant().toEpochMilli(), end.toInstant().toEpochMilli());
    }

    public static StatsTimeRange month(int year, int month)
    {
        if(month < 1 || month > 12)
            return null;
        ZonedDateTime start = ZonedDateTime.of(year, month, 1, 0, 0, 0, 0, ZoneId.systemDefault());
        ZonedDateTime end = start.plusMonths(1);
        return new StatsTimeRange(String.format("%04d-%02d", year, month), Month.of(month) + " " + year,
                start.toInstant().toEpochMilli(), end.toInstant().toEpochMilli());
    }

    public static StatsTimeRange previousYear()
    {
        ZonedDateTime now = ZonedDateTime.now();
        ZonedDateTime start = ZonedDateTime.of(now.getYear() - 1, 1, 1, 0, 0, 0, 0, ZoneId.systemDefault());
        ZonedDateTime end = start.plusYears(1);
        return new StatsTimeRange("year", Integer.toString(start.getYear()), start.toInstant().toEpochMilli(), end.toInstant().toEpochMilli());
    }

    public static StatsTimeRange year(int year)
    {
        ZonedDateTime start = ZonedDateTime.of(year, 1, 1, 0, 0, 0, 0, ZoneId.systemDefault());
        ZonedDateTime end = start.plusYears(1);
        return new StatsTimeRange(Integer.toString(year), Integer.toString(year), start.toInstant().toEpochMilli(), end.toInstant().toEpochMilli());
    }

    public String getKey()
    {
        return key;
    }

    public String getLabel()
    {
        return label;
    }

    public Long getStartMillis()
    {
        return startMillis;
    }

    public Long getEndMillis()
    {
        return endMillis;
    }
}
