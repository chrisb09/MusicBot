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
package com.jagrosh.jmusicbot.audio;

import com.jagrosh.jmusicbot.Bot;
import com.jagrosh.jmusicbot.settings.Settings;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.channel.ChannelType;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.channel.unions.MessageChannelUnion;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.utils.messages.MessageCreateData;
import net.dv8tion.jda.api.utils.messages.MessageEditData;

public class StatusMessageHandler
{
    private final Bot bot;
    private final Map<Long, StatusState> states;

    public StatusMessageHandler(Bot bot)
    {
        this.bot = bot;
        this.states = new ConcurrentHashMap<>();
    }

    public void init()
    {
        bot.getThreadpool().scheduleWithFixedDelay(() -> updateAll(), 0, 1, TimeUnit.SECONDS);
    }

    public void onMessageReceived(MessageReceivedEvent event)
    {
        if(!event.isFromGuild())
            return;
        Guild guild = event.getGuild();
        StatusState state = states.computeIfAbsent(guild.getIdLong(), id -> new StatusState());
        MessageChannelUnion channelUnion = event.getChannel();
        if(channelUnion == null || channelUnion.getType() != ChannelType.TEXT)
            return;
        TextChannel channel = channelUnion.asTextChannel();

        boolean isCommand = isCommandMessage(event.getMessage(), guild);
        Settings settings = bot.getSettingsManager().getSettings(guild);
        if(isCommand && !event.getAuthor().isBot() && settings.getTextChannel(guild) == null)
            state.lastCommandChannelId = channel.getIdLong();

        Long statusChannelId = resolveStatusChannelId(guild, state);
        if(statusChannelId == null || statusChannelId == 0L)
            return;
        if(isCommand && !event.getAuthor().isBot() && channel.getIdLong() == statusChannelId)
            state.pendingRepost = true;
        if(channel.getIdLong() != statusChannelId)
            return;

        if(state.messageId != 0L && event.getMessageIdLong() == state.messageId)
            return;

        if(state.pendingRepost)
        {
            long selfId = bot.getJDA().getSelfUser().getIdLong();
            if(event.getAuthor().getIdLong() == selfId)
            {
                repostStatusMessage(guild, channel, state);
                state.pendingRepost = false;
            }
            return;
        }

        // Avoid repost loops when the status message itself is created
        if(event.getAuthor().getIdLong() == bot.getJDA().getSelfUser().getIdLong() && state.createInFlight)
            return;

        repostStatusMessage(guild, channel, state);
    }

    public void onMessageDelete(Guild guild, long messageId)
    {
        StatusState state = states.get(guild.getIdLong());
        if(state == null)
            return;
        if(state.messageId == messageId)
            state.messageId = 0L;
    }

    public void onTrackUpdate(long guildId)
    {
        StatusState state = states.computeIfAbsent(guildId, id -> new StatusState());
        state.forceUpdate = true;
    }

    private void updateAll()
    {
        JDA jda = bot.getJDA();
        if(jda == null)
            return;
        for(Map.Entry<Long, StatusState> entry : states.entrySet())
        {
            long guildId = entry.getKey();
            StatusState state = entry.getValue();
            Guild guild = jda.getGuildById(guildId);
            if(guild == null)
                continue;
            TextChannel channel = resolveStatusChannel(guild, state);
            if(channel == null)
                continue;
            AudioHandler handler = (AudioHandler)guild.getAudioManager().getSendingHandler();
            if(handler == null)
                continue;
            if(state.createInFlight)
                continue;

            boolean isPlaying = handler.isMusicPlaying(jda);
            if(!isPlaying && !state.forceUpdate)
                continue;

            if(state.pendingRepost && (state.messageId == 0L || state.channelId != channel.getIdLong()))
                continue;

            MessageCreateData statusMessage = handler.getStatusMessage(jda);
            if(state.messageId == 0L || state.channelId != channel.getIdLong())
            {
                repostStatusMessage(guild, channel, state, statusMessage);
            }
            else
            {
                channel.editMessageById(state.messageId, MessageEditData.fromCreateData(statusMessage))
                        .queue(m -> {}, t -> state.messageId = 0L);
            }
            state.forceUpdate = false;
        }
    }

    private void repostStatusMessage(Guild guild, TextChannel channel, StatusState state)
    {
        MessageCreateData statusMessage = getStatusMessage(guild);
        if(statusMessage == null)
            return;
        repostStatusMessage(guild, channel, state, statusMessage);
    }

    private void repostStatusMessage(Guild guild, TextChannel channel, StatusState state, MessageCreateData statusMessage)
    {
        if(state.createInFlight)
            return;
        state.createInFlight = true;
        deleteOldStatusMessage(guild, state);
        channel.sendMessage(statusMessage).queue(m ->
        {
            state.channelId = channel.getIdLong();
            state.messageId = m.getIdLong();
            state.createInFlight = false;
        }, t ->
        {
            state.createInFlight = false;
        });
    }

    private void deleteOldStatusMessage(Guild guild, StatusState state)
    {
        if(state.messageId == 0L)
            return;
        TextChannel oldChannel = guild.getTextChannelById(state.channelId);
        if(oldChannel == null)
            return;
        oldChannel.deleteMessageById(state.messageId).queue(m -> {}, t -> {});
    }

    private MessageCreateData getStatusMessage(Guild guild)
    {
        AudioHandler handler = (AudioHandler)guild.getAudioManager().getSendingHandler();
        if(handler == null)
            return null;
        return handler.getStatusMessage(bot.getJDA());
    }

    private TextChannel resolveStatusChannel(Guild guild, StatusState state)
    {
        Long id = resolveStatusChannelId(guild, state);
        return id == null ? null : guild.getTextChannelById(id);
    }

    private Long resolveStatusChannelId(Guild guild, StatusState state)
    {
        Settings settings = bot.getSettingsManager().getSettings(guild);
        if(settings == null)
            return null;
        if(settings.getTextChannel(guild) != null)
            return settings.getTextChannel(guild).getIdLong();
        return state.lastCommandChannelId == 0L ? null : state.lastCommandChannelId;
    }

    private boolean isCommandMessage(Message message, Guild guild)
    {
        String content = message.getContentRaw();
        if(content == null || content.isEmpty())
            return false;
        Settings settings = bot.getSettingsManager().getSettings(guild);
        String prefix = settings.getPrefix() != null ? settings.getPrefix() : bot.getConfig().getPrefix();
        String altprefix = bot.getConfig().getAltPrefix();
        if(prefix != null && prefix.equalsIgnoreCase("@mention"))
        {
            long botId = bot.getJDA().getSelfUser().getIdLong();
            String mention = "<@" + botId + ">";
            String mentionNick = "<@!" + botId + ">";
            if(content.startsWith(mention) || content.startsWith(mentionNick))
                return true;
        }
        if(prefix != null && !prefix.equalsIgnoreCase("@mention") && content.startsWith(prefix))
            return true;
        return altprefix != null && !altprefix.isEmpty() && content.startsWith(altprefix);
    }

    private static class StatusState
    {
        private long channelId = 0L;
        private long messageId = 0L;
        private long lastCommandChannelId = 0L;
        private boolean pendingRepost = false;
        private boolean forceUpdate = false;
        private boolean createInFlight = false;
    }
}
