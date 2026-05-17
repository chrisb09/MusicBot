/*
 * Copyright 2026
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package com.jagrosh.jmusicbot.datalog;

import com.jagrosh.jmusicbot.Bot;
import com.jagrosh.jmusicbot.settings.Settings;
import com.jagrosh.jmusicbot.settings.StatsReportFrequency;
import java.time.LocalDate;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class StatsReportScheduler
{
    private static final Logger LOG = LoggerFactory.getLogger("StatsReports");

    private final Bot bot;
    private final Map<Long, String> sentPeriods = new ConcurrentHashMap<>();

    public StatsReportScheduler(Bot bot)
    {
        this.bot = bot;
    }

    public void init()
    {
        bot.getThreadpool().scheduleWithFixedDelay(() -> runScheduledReports(), 1, 1, TimeUnit.HOURS);
    }

    private void runScheduledReports()
    {
        if(bot.getDataLogService() == null)
            return;
        JDA jda = bot.getJDA();
        if(jda == null)
            return;
        LocalDate today = LocalDate.now();
        for(Guild guild : jda.getGuilds())
        {
            Settings settings = bot.getSettingsManager().getSettings(guild);
            if(settings.getStatsReportFrequency() == StatsReportFrequency.OFF)
                continue;
            if(settings.getStatsReportFrequency() == StatsReportFrequency.MONTHLY && today.getDayOfMonth() != 1)
                continue;
            if(settings.getStatsReportFrequency() == StatsReportFrequency.YEARLY && (today.getDayOfMonth() != 1 || today.getMonthValue() != 1))
                continue;

            TextChannel channel = settings.getStatsReportChannel(guild);
            if(channel == null)
                continue;
            StatsTimeRange range = settings.getStatsReportFrequency() == StatsReportFrequency.YEARLY
                    ? StatsTimeRange.previousYear()
                    : StatsTimeRange.previousMonth();
            String periodKey = settings.getStatsReportFrequency().name() + ":" + range.getLabel();
            if(periodKey.equals(sentPeriods.get(guild.getIdLong())))
                continue;
            if(sendReport(guild, channel, range))
                sentPeriods.put(guild.getIdLong(), periodKey);
        }
    }

    public boolean sendReport(Guild guild, TextChannel channel, StatsTimeRange range)
    {
        if(bot.getDataLogService() == null || guild == null || channel == null)
            return false;
        StatsSummary summary = bot.getDataLogService().getStatsSummary(guild.getIdLong(), range, null, null);
        if(summary.isEmpty())
            return false;
        channel.sendMessageEmbeds(StatsMessageFormatter.summaryEmbed("Server rewind - " + range.getLabel(), summary,
                guild.getSelfMember().getColorRaw()).build()).queue(
                        m -> {},
                        t -> LOG.debug("Failed to send stats report for guild {}: {}", guild.getId(), t.getMessage()));
        return true;
    }
}
