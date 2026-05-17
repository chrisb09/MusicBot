/*
 * Copyright 2026
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package com.jagrosh.jmusicbot.commands.general;

import com.jagrosh.jdautilities.command.CommandEvent;
import com.jagrosh.jdautilities.commons.utils.FinderUtil;
import com.jagrosh.jmusicbot.Bot;
import com.jagrosh.jmusicbot.audio.AudioHandler;
import com.jagrosh.jmusicbot.commands.LoggedCommand;
import com.jagrosh.jmusicbot.datalog.CommandLogContext;
import com.jagrosh.jmusicbot.datalog.StatsFilters;
import com.jagrosh.jmusicbot.datalog.StatsAccess;
import com.jagrosh.jmusicbot.datalog.StatsMessageFormatter;
import com.jagrosh.jmusicbot.datalog.StatsSummary;
import com.jagrosh.jmusicbot.datalog.StatsTimeRange;
import com.jagrosh.jmusicbot.datalog.StatsRow;
import com.jagrosh.jmusicbot.settings.Settings;
import com.jagrosh.jmusicbot.settings.StatsReportFrequency;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import org.json.JSONObject;

public class StatsCmd extends LoggedCommand
{
    public StatsCmd(Bot bot)
    {
        super(bot);
        this.name = "stats";
        this.help = "shows playback statistics";
        this.arguments = "<server|me|user|tracks|listened|track|skips|sources|reports>";
        this.guildOnly = true;
        this.aliases = bot.getConfig().getAliases(this.name);
        this.botPermissions = new Permission[]{Permission.MESSAGE_EMBED_LINKS};
    }

    @Override
    public void doCommand(CommandEvent event)
    {
        if(bot.getDataLogService() == null)
        {
            event.replyWarning("Playback analytics are not enabled.");
            CommandLogContext.setError("analytics_disabled");
            return;
        }

        List<String> tokens = new ArrayList<>(Arrays.asList(event.getArgs().trim().split("\\s+")));
        if(tokens.size() == 1 && tokens.get(0).isEmpty())
            tokens.clear();
        String subcommand = tokens.isEmpty() ? "server" : tokens.remove(0).toLowerCase();
        switch(subcommand)
        {
            case "server":
                sendSummary(event, "Server stats", parseOptions(tokens), null, Section.ALL);
                break;
            case "me":
                sendSummary(event, "Your stats", parseOptions(tokens), event.getAuthor().getIdLong(), Section.ALL);
                break;
            case "user":
                handleUserStats(event, tokens);
                break;
            case "track":
                handleTrackStats(event, parseOptions(tokens));
                break;
            case "tracks":
            case "played":
                handleTrackListStats(event, "Top played tracks", parseOptions(tokens), false);
                break;
            case "listened":
            case "listens":
                handleTrackListStats(event, "Top listened tracks", parseOptions(tokens), true);
                break;
            case "skips":
                sendSummary(event, "Skip stats", parseOptions(tokens), null, Section.SKIPS);
                break;
            case "sources":
                sendSummary(event, "Source stats", parseOptions(tokens), null, Section.SOURCES);
                break;
            case "reports":
                handleReports(event, tokens);
                break;
            case "help":
                sendUsage(event);
                break;
            default:
                tokens.add(0, subcommand);
                handleUserStats(event, tokens);
        }
    }

    private void handleUserStats(CommandEvent event, List<String> tokens)
    {
        Options options = parseOptions(tokens);
        String targetText = String.join(" ", options.remaining).trim();
        if(targetText.isEmpty())
        {
            event.replyError("Please include a member.");
            CommandLogContext.setError("missing_member");
            return;
        }
        Long targetId = parseUserId(targetText);
        if(targetId != null)
        {
            Member member = event.getGuild().getMemberById(targetId);
            if(!StatsAccess.canViewUserStats(event.getAuthor().getIdLong(), targetId, canManageServer(event.getMember())))
            {
                event.replyError("You can only view another member's detailed stats if you can manage this server.");
                CommandLogContext.setError("missing_manage_server");
                return;
            }
            sendSummary(event, member == null ? "Stats for user " + targetId : "Stats for " + member.getEffectiveName(), options, targetId, Section.ALL);
            return;
        }
        List<Member> members = FinderUtil.findMembers(targetText, event.getGuild());
        if(members.isEmpty())
            members = findMembersByPartialName(targetText, event.getGuild().getMembers());
        if(members.isEmpty())
        {
            List<StatsRow> profileMatches = bot.getDataLogService().findUsersByProfile(event.getGuild().getIdLong(), targetText, 6);
            if(profileMatches.size() == 1)
            {
                StatsRow match = profileMatches.get(0);
                if(!StatsAccess.canViewUserStats(event.getAuthor().getIdLong(), match.getId(), canManageServer(event.getMember())))
                {
                    event.replyError("You can only view another member's detailed stats if you can manage this server.");
                    CommandLogContext.setError("missing_manage_server");
                    return;
                }
                sendSummary(event, "Stats for " + match.getLabel(), options, match.getId(), Section.ALL);
                return;
            }
            if(profileMatches.size() > 1)
            {
                event.replyWarning("Multiple analytics users matched `" + targetText + "`: " + formatProfileMatches(profileMatches) + ". Please be more specific or use a mention.");
                CommandLogContext.setError("member_ambiguous");
                return;
            }
            event.replyWarning("No member found matching `" + targetText + "`. Try a mention like `@user` or the Discord user ID.");
            CommandLogContext.setError("member_not_found");
            return;
        }
        if(members.size() > 1)
        {
            event.replyWarning("Multiple members matched `" + targetText + "`: " + formatMemberMatches(members) + ". Please be more specific or use a mention.");
            CommandLogContext.setError("member_ambiguous");
            return;
        }
        Member target = members.get(0);
        if(!StatsAccess.canViewUserStats(event.getAuthor().getIdLong(), target.getIdLong(), canManageServer(event.getMember())))
        {
            event.replyError("You can only view another member's detailed stats if you can manage this server.");
            CommandLogContext.setError("missing_manage_server");
            return;
        }
        sendSummary(event, "Stats for " + target.getEffectiveName(), options, target.getIdLong(), Section.ALL);
    }

    private void sendUsage(CommandEvent event)
    {
        event.reply("Use `stats server [range] [source:youtube]`, `stats me [range]`, `stats user <@user|user id|name> [range]`, `stats tracks [range] [limit:20] [userlist:[name,id]]`, `stats listened [range] [limit:20] [userlist:[name,id]]`, `stats track [range]`, `stats skips`, `stats sources`, or `stats reports`.");
    }

    private Long parseUserId(String value)
    {
        if(value == null)
            return null;
        String cleaned = value.trim();
        if(cleaned.startsWith("<@") && cleaned.endsWith(">"))
        {
            cleaned = cleaned.substring(2, cleaned.length() - 1);
            if(cleaned.startsWith("!"))
                cleaned = cleaned.substring(1);
        }
        if(!cleaned.matches("\\d{15,25}"))
            return null;
        try
        {
            return Long.parseLong(cleaned);
        }
        catch(NumberFormatException ex)
        {
            return null;
        }
    }

    private List<Member> findMembersByPartialName(String query, List<Member> members)
    {
        String normalizedQuery = normalizeName(query);
        List<Member> matches = new ArrayList<>();
        if(normalizedQuery.isEmpty())
            return matches;
        for(Member member : members)
        {
            if(member.getUser().isBot())
                continue;
            if(nameMatches(normalizedQuery, member.getEffectiveName())
                    || nameMatches(normalizedQuery, member.getUser().getName())
                    || nameMatches(normalizedQuery, member.getNickname()))
            {
                matches.add(member);
            }
        }
        return matches;
    }

    private boolean nameMatches(String normalizedQuery, String name)
    {
        if(name == null || name.isEmpty())
            return false;
        return normalizeName(name).contains(normalizedQuery);
    }

    private String normalizeName(String value)
    {
        return value == null ? "" : value.toLowerCase().replaceAll("[^a-z0-9]", "");
    }

    private String formatMemberMatches(List<Member> members)
    {
        StringBuilder builder = new StringBuilder();
        int max = Math.min(5, members.size());
        for(int i = 0; i < max; i++)
        {
            if(i > 0)
                builder.append(", ");
            Member member = members.get(i);
            builder.append("`").append(member.getEffectiveName()).append("`");
        }
        if(members.size() > max)
            builder.append(", and ").append(members.size() - max).append(" more");
        return builder.toString();
    }

    private String formatProfileMatches(List<StatsRow> rows)
    {
        StringBuilder builder = new StringBuilder();
        int max = Math.min(5, rows.size());
        for(int i = 0; i < max; i++)
        {
            if(i > 0)
                builder.append(", ");
            builder.append("`").append(rows.get(i).getLabel()).append("`");
        }
        if(rows.size() > max)
            builder.append(", and ").append(rows.size() - max).append(" more");
        return builder.toString();
    }

    private void handleTrackStats(CommandEvent event, Options options)
    {
        AudioHandler handler = (AudioHandler)event.getGuild().getAudioManager().getSendingHandler();
        AudioTrack track = handler == null ? null : handler.getPlayer().getPlayingTrack();
        if(track == null)
        {
            event.replyWarning("No track is currently playing.");
            CommandLogContext.setError("no_current_track");
            return;
        }
        long count = bot.getDataLogService().getGuildTrackPlayCount(event.getGuild(), track, options.range);
        EmbedBuilder eb = new EmbedBuilder()
                .setColor(event.getSelfMember().getColor())
                .setTitle(track.getInfo().title, track.getInfo().uri)
                .setDescription("Range: **" + options.range.getLabel() + "**\nPlayed **" + count + "** time" + (count == 1L ? "" : "s") + " on this server.");
        if(track.getInfo().author != null && !track.getInfo().author.isEmpty())
            eb.setFooter("Source: " + track.getInfo().author, null);
        event.getChannel().sendMessageEmbeds(eb.build()).queue();
        CommandLogContext.setMeta(new JSONObject().put("subcommand", "track").put("play_count", count));
    }

    private void handleTrackListStats(CommandEvent event, String title, Options options, boolean listened)
    {
        List<Long> userIds = resolveUserList(event, options.userQueries);
        if(userIds == null)
            return;
        if(!userIds.isEmpty() && !canViewUserList(event, userIds))
        {
            event.replyError("You can only filter by other members if you can manage this server.");
            CommandLogContext.setError("missing_manage_server");
            return;
        }
        List<StatsRow> rows = listened
                ? bot.getDataLogService().getTopListenedTracks(event.getGuild().getIdLong(), options.range, options.source, userIds, options.limit)
                : bot.getDataLogService().getTopRequestedTracks(event.getGuild().getIdLong(), options.range, options.source, userIds, options.limit);
        if(rows.isEmpty())
        {
            event.replyWarning("No playback analytics found for that report yet.");
            CommandLogContext.setError("no_data");
            return;
        }
        String description = "Range: **" + options.range.getLabel() + "**"
                + (options.source == null ? "" : "\nSource: **" + options.source + "**")
                + (userIds.isEmpty() ? "\nUsers: **all**" : "\nUsers: **" + userIds.size() + " selected**");
        EmbedBuilder eb = new EmbedBuilder()
                .setTitle(title)
                .setColor(event.getSelfMember().getColorRaw())
                .setDescription(description);
        StatsMessageFormatter.addChunkedField(eb,
                listened ? "Tracks by listening time" : "Tracks by plays",
                listened
                        ? StatsMessageFormatter.trackListenList(rows, options.limit)
                        : StatsMessageFormatter.countList(rows, "plays", options.limit),
                false);
        event.getChannel().sendMessageEmbeds(eb.build()).queue();
        CommandLogContext.setMeta(new JSONObject().put("range", options.range.getKey())
                .put("source", options.source == null ? JSONObject.NULL : options.source)
                .put("user_count", userIds.size())
                .put("limit", options.limit)
                .put("listened", listened));
    }

    private void handleReports(CommandEvent event, List<String> tokens)
    {
        if(tokens.isEmpty())
        {
            event.reply("Use `stats reports set <channel> <monthly|yearly|off>` or `stats reports now [month|year|YYYY-MM|MM/YYYY|YYYY]`.");
            return;
        }
        String action = tokens.remove(0).toLowerCase();
        if("now".equals(action))
        {
            if(!canManageServer(event.getMember()))
            {
                event.replyError("You need Manage Server to send stats reports.");
                CommandLogContext.setError("missing_manage_server");
                return;
            }
            StatsTimeRange range = parseReportRange(tokens);
            if(range == null)
            {
                event.replyError("Use `stats reports now month`, `stats reports now year`, `stats reports now 2026-04`, `stats reports now 04/2026`, or `stats reports now 2025`.");
                CommandLogContext.setError("invalid_report_range");
                return;
            }
            boolean sent = bot.getStatsReportScheduler().sendReport(event.getGuild(), event.getTextChannel(), range);
            if(sent)
            {
                event.replySuccess("Stats report generated for `" + range.getLabel() + "`.");
                CommandLogContext.setMeta(new JSONObject().put("subcommand", "reports_now").put("range", range.getKey()));
            }
            else
            {
                event.replyWarning("No playback analytics found for that report yet.");
                CommandLogContext.setError("no_data");
            }
            return;
        }
        if(!"set".equals(action))
        {
            event.replyWarning("Unknown reports action. Use `set` or `now`.");
            CommandLogContext.setError("unknown_reports_action");
            return;
        }
        if(!canManageServer(event.getMember()))
        {
            event.replyError("You need Manage Server to configure stats reports.");
            CommandLogContext.setError("missing_manage_server");
            return;
        }
        if(tokens.isEmpty())
        {
            event.replyError("Please include a channel and frequency, or `off`.");
            CommandLogContext.setError("missing_report_settings");
            return;
        }
        Settings settings = event.getClient().getSettingsFor(event.getGuild());
        if(tokens.size() == 1 && "off".equalsIgnoreCase(tokens.get(0)))
        {
            settings.setStatsReportFrequency(StatsReportFrequency.OFF);
            settings.setStatsReportChannel(null);
            event.replySuccess("Stats reports are now disabled.");
            CommandLogContext.setMeta(new JSONObject().put("subcommand", "reports_set").put("frequency", "OFF"));
            return;
        }
        if(tokens.size() < 2)
        {
            event.replyError("Use `stats reports set <channel> <monthly|yearly|off>`.");
            CommandLogContext.setError("missing_report_frequency");
            return;
        }
        String frequencyToken = tokens.remove(tokens.size() - 1);
        StatsReportFrequency frequency = StatsReportFrequency.parse(frequencyToken);
        if(frequency == StatsReportFrequency.OFF)
        {
            settings.setStatsReportFrequency(StatsReportFrequency.OFF);
            settings.setStatsReportChannel(null);
            event.replySuccess("Stats reports are now disabled.");
            CommandLogContext.setMeta(new JSONObject().put("subcommand", "reports_set").put("frequency", "OFF"));
            return;
        }
        String channelText = String.join(" ", tokens);
        @SuppressWarnings({"unchecked", "rawtypes"})
        List<TextChannel> channels = (List)FinderUtil.findTextChannels(channelText, event.getGuild());
        if(channels.isEmpty())
        {
            event.replyWarning("No text channel found matching `" + channelText + "`.");
            CommandLogContext.setError("text_channel_not_found");
            return;
        }
        if(channels.size() > 1)
        {
            event.replyWarning("Multiple text channels matched `" + channelText + "`. Please be more specific.");
            CommandLogContext.setError("text_channel_ambiguous");
            return;
        }
        settings.setStatsReportChannel(channels.get(0));
        settings.setStatsReportFrequency(frequency);
        event.replySuccess("Stats reports will be posted " + frequency.name().toLowerCase() + " in " + channels.get(0).getAsMention() + ".");
        CommandLogContext.setMeta(new JSONObject().put("subcommand", "reports_set")
                .put("channel_id", channels.get(0).getId()).put("frequency", frequency.name()));
    }

    private StatsTimeRange parseReportRange(List<String> tokens)
    {
        if(tokens.isEmpty())
            return StatsTimeRange.previousMonth();
        String value = String.join(" ", tokens).trim().toLowerCase();
        if(value.isEmpty() || "month".equals(value) || "monthly".equals(value))
            return StatsTimeRange.previousMonth();
        if("year".equals(value) || "yearly".equals(value))
            return StatsTimeRange.previousYear();
        if(value.matches("\\d{4}"))
            return StatsTimeRange.year(Integer.parseInt(value));
        if(value.matches("\\d{4}-\\d{1,2}"))
        {
            String[] parts = value.split("-");
            return StatsTimeRange.month(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]));
        }
        if(value.matches("\\d{1,2}/\\d{4}"))
        {
            String[] parts = value.split("/");
            return StatsTimeRange.month(Integer.parseInt(parts[1]), Integer.parseInt(parts[0]));
        }
        return null;
    }

    private void sendSummary(CommandEvent event, String title, Options options, Long userId, Section section)
    {
        StatsSummary summary = bot.getDataLogService().getStatsSummary(event.getGuild().getIdLong(), options.range, options.source, userId);
        if(summary.isEmpty())
        {
            event.replyWarning("No playback analytics found for that report yet.");
            CommandLogContext.setError("no_data");
            return;
        }
        EmbedBuilder eb = userId == null
                ? StatsMessageFormatter.summaryEmbed(title, summary, event.getSelfMember().getColorRaw())
                : StatsMessageFormatter.personalEmbed(title, summary, event.getSelfMember().getColorRaw());
        if(section == Section.SKIPS)
        {
            eb.clearFields();
            StatsMessageFormatter.addChunkedField(eb, "Skip actions", StatsMessageFormatter.countList(summary.getSkipActions(), "skips"), false);
            StatsMessageFormatter.addChunkedField(eb, "Skip votes", StatsMessageFormatter.countList(summary.getSkipVotes(), "votes"), false);
        }
        else if(section == Section.SOURCES)
        {
            eb.clearFields();
            StatsMessageFormatter.addChunkedField(eb, "Top sources", StatsMessageFormatter.countList(summary.getTopSources(), "plays"), false);
        }
        event.getChannel().sendMessageEmbeds(eb.build()).queue();
        CommandLogContext.setMeta(new JSONObject().put("range", options.range.getKey())
                .put("source", options.source == null ? JSONObject.NULL : options.source)
                .put("target_user_id", userId == null ? JSONObject.NULL : userId));
    }

    private Options parseOptions(List<String> tokens)
    {
        StatsTimeRange range = StatsTimeRange.all();
        String source = null;
        int limit = 5;
        List<String> remaining = new ArrayList<>();
        List<String> userQueries = new ArrayList<>();
        for(String token : tokens)
        {
            if(token == null || token.isEmpty())
                continue;
            if(token.toLowerCase().startsWith("source:"))
            {
                source = StatsFilters.normalizeSource(token.substring("source:".length()));
                continue;
            }
            String lower = token.toLowerCase();
            if(lower.startsWith("limit:") || lower.startsWith("top:") || lower.startsWith("n:"))
            {
                Integer parsedLimit = parseLimit(token.substring(token.indexOf(':') + 1));
                if(parsedLimit != null)
                    limit = parsedLimit;
                continue;
            }
            if(lower.startsWith("users:") || lower.startsWith("userlist:"))
            {
                String value = token.substring(token.indexOf(':') + 1).trim();
                if(value.startsWith("[") && value.endsWith("]"))
                    value = value.substring(1, value.length() - 1);
                for(String part : value.split(","))
                    if(!part.trim().isEmpty())
                        userQueries.add(part.trim());
                continue;
            }
            StatsTimeRange parsedRange = StatsTimeRange.parse(token);
            if(parsedRange != null)
            {
                range = parsedRange;
                continue;
            }
            Integer parsedLimit = parseLimit(token);
            if(parsedLimit != null)
            {
                limit = parsedLimit;
                continue;
            }
            remaining.add(token);
        }
        return new Options(range, source, remaining, userQueries, limit);
    }

    private Integer parseLimit(String token)
    {
        if(token == null || !token.matches("\\d+"))
            return null;
        int value = Integer.parseInt(token);
        if(value < 1)
            return 1;
        return Math.min(value, 25);
    }

    private List<Long> resolveUserList(CommandEvent event, List<String> queries)
    {
        List<Long> userIds = new ArrayList<>();
        for(String query : queries)
        {
            Long userId = parseUserId(query);
            if(userId != null)
            {
                userIds.add(userId);
                continue;
            }
            List<Member> members = FinderUtil.findMembers(query, event.getGuild());
            if(members.isEmpty())
                members = findMembersByPartialName(query, event.getGuild().getMembers());
            if(members.isEmpty())
            {
                List<StatsRow> profileMatches = bot.getDataLogService().findUsersByProfile(event.getGuild().getIdLong(), query, 6);
                if(profileMatches.size() == 1)
                {
                    userIds.add(profileMatches.get(0).getId());
                    continue;
                }
                if(profileMatches.size() > 1)
                {
                    event.replyWarning("Multiple analytics users matched `" + query + "`: " + formatProfileMatches(profileMatches) + ". Please be more specific or use a mention.");
                    CommandLogContext.setError("member_ambiguous");
                    return null;
                }
                event.replyWarning("No member found matching `" + query + "`. Try a mention or Discord user ID.");
                CommandLogContext.setError("member_not_found");
                return null;
            }
            if(members.size() > 1)
            {
                event.replyWarning("Multiple members matched `" + query + "`: " + formatMemberMatches(members) + ". Please be more specific or use a mention.");
                CommandLogContext.setError("member_ambiguous");
                return null;
            }
            userIds.add(members.get(0).getIdLong());
        }
        return userIds;
    }

    private boolean canViewUserList(CommandEvent event, List<Long> userIds)
    {
        for(Long userId : userIds)
            if(userId != event.getAuthor().getIdLong())
                return canManageServer(event.getMember());
        return true;
    }

    private boolean canManageServer(Member member)
    {
        return member != null && member.hasPermission(Permission.MANAGE_SERVER);
    }

    private enum Section
    {
        ALL,
        SKIPS,
        SOURCES
    }

    private static class Options
    {
        private final StatsTimeRange range;
        private final String source;
        private final List<String> remaining;
        private final List<String> userQueries;
        private final int limit;

        private Options(StatsTimeRange range, String source, List<String> remaining, List<String> userQueries, int limit)
        {
            this.range = range == null ? StatsTimeRange.all() : range;
            this.source = source;
            this.remaining = remaining;
            this.userQueries = userQueries;
            this.limit = limit;
        }
    }
}
