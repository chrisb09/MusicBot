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
package com.jagrosh.jmusicbot.commands.general;

import com.jagrosh.jdautilities.command.CommandEvent;
import com.jagrosh.jdautilities.examples.command.AboutCommand;
import com.jagrosh.jmusicbot.Bot;
import com.jagrosh.jmusicbot.JMusicBot;
import com.jagrosh.jmusicbot.utils.OtherUtil;
import java.awt.Color;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDAInfo;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.ApplicationInfo;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.ChannelType;
import org.json.JSONObject;

public class LoggedAboutCmd extends AboutCommand
{
    private final Bot bot;
    private final Color color;
    private final String description;
    private final String[] features;
    private final Permission[] perms;
    private boolean isAuthor = true;
    private String replacementCharacter = "+";
    private String oauthLink;

    public LoggedAboutCmd(Bot bot, Color color, String description, String[] features, Permission[] perms)
    {
        super(color, description, features, perms);
        this.bot = bot;
        this.color = color;
        this.description = description;
        this.features = features == null ? new String[0] : features.clone();
        this.perms = perms == null ? new Permission[0] : perms.clone();
    }

    @Override
    public void setIsAuthor(boolean isAuthor)
    {
        super.setIsAuthor(isAuthor);
        this.isAuthor = isAuthor;
    }

    @Override
    public void setReplacementCharacter(String replacementCharacter)
    {
        super.setReplacementCharacter(replacementCharacter);
        this.replacementCharacter = replacementCharacter;
    }

    @Override
    protected void execute(CommandEvent event)
    {
        if(oauthLink == null)
            oauthLink = resolveOauthLink(event);

        EmbedBuilder builder = new EmbedBuilder()
                .setColor(event.isFromType(ChannelType.TEXT)
                        ? event.getGuild().getSelfMember().getColor()
                        : color)
                .setAuthor("All about " + event.getSelfUser().getName() + "!",
                        null,
                        event.getSelfUser().getAvatarUrl())
                .setDescription(buildDescription(event));

        addStats(builder, event);
        builder.setFooter("Last restart", null)
                .setTimestamp(event.getClient().getStartTime());

        event.reply(builder.build());
        if(bot.getDataLogService() != null && event.getGuild() != null)
        {
            bot.getDataLogService().logCommandEvent(event.getGuild(), event.getAuthor(), getName(),
                    event.getArgs(), "OK", new JSONObject().put("about", true).toString());
        }
    }

    private String resolveOauthLink(CommandEvent event)
    {
        try
        {
            ApplicationInfo info = event.getJDA().retrieveApplicationInfo().complete();
            return info.isBotPublic() ? info.getInviteUrl(0, perms) : "";
        }
        catch(Exception ex)
        {
            JMusicBot.LOG.error("Could not generate invite link", ex);
            return "";
        }
    }

    private String buildDescription(CommandEvent event)
    {
        String owner = getOwnerDisplay(event);
        String links = getLinksSection(event);
        StringBuilder builder = new StringBuilder()
                .append("Hello! I am **").append(event.getSelfUser().getName()).append("**, ")
                .append(description)
                .append("\nI ")
                .append(isAuthor ? "was written in Java" : "am owned")
                .append(" by **").append(owner).append("**")
                .append(" using Chew's [Commands Extension](https://github.com/Chew/JDA-Chewtils) (")
                .append(OtherUtil.getCommandLibraryVersion())
                .append(") and the [JDA library](https://github.com/DV8FromTheWorld/JDA) (")
                .append(JDAInfo.VERSION)
                .append(")\nType `")
                .append(event.getClient().getTextualPrefix())
                .append(event.getClient().getHelpWord())
                .append("` to see my commands!");

        if(!links.isEmpty())
            builder.append(links);

        builder.append("\n\nSome of my features include: ```css");
        for(String feature : features)
        {
            builder.append("\n")
                    .append(event.getClient().getSuccess().startsWith("<")
                            ? replacementCharacter
                            : event.getClient().getSuccess())
                    .append(" ")
                    .append(feature);
        }
        builder.append("```");
        return builder.toString();
    }

    private String getOwnerDisplay(CommandEvent event)
    {
        User owner = event.getJDA().getUserById(event.getClient().getOwnerId());
        return owner == null ? "<@" + event.getClient().getOwnerId() + ">" : owner.getName();
    }

    private String getLinksSection(CommandEvent event)
    {
        String serverInvite = event.getClient().getServerInvite();
        boolean hasServerInvite = serverInvite != null && !serverInvite.isEmpty();
        boolean hasOauthLink = oauthLink != null && !oauthLink.isEmpty();
        if(!hasServerInvite && !hasOauthLink)
            return "";

        StringBuilder builder = new StringBuilder("\n");
        if(hasServerInvite)
            builder.append("Join my server [`here`](").append(serverInvite).append(")");
        else if(hasOauthLink)
            builder.append("Please");

        if(hasOauthLink)
        {
            if(hasServerInvite)
                builder.append(", or");
            builder.append(" [`invite`](").append(oauthLink).append(") me to your server");
        }
        builder.append("!");
        return builder.toString();
    }

    private void addStats(EmbedBuilder builder, CommandEvent event)
    {
        JDA jda = event.getJDA();
        if(jda.getShardInfo() == JDA.ShardInfo.SINGLE)
        {
            builder.addField("Stats", jda.getGuilds().size() + " servers\n1 shard", true)
                    .addField("Users",
                            jda.getUsers().size() + " unique\n"
                                    + jda.getGuilds().stream().mapToInt(Guild::getMemberCount).sum() + " total",
                            true)
                    .addField("Channels",
                            jda.getTextChannels().size() + " Text\n" + jda.getVoiceChannels().size() + " Voice",
                            true);
            return;
        }

        builder.addField("Stats",
                        event.getClient().getTotalGuilds() + " Servers\nShard "
                                + (jda.getShardInfo().getShardId() + 1) + "/" + jda.getShardInfo().getShardTotal(),
                        true)
                .addField("This shard", jda.getUsers().size() + " Users\n" + jda.getGuilds().size() + " Servers", true)
                .addField("",
                        jda.getTextChannels().size() + " Text Channels\n" + jda.getVoiceChannels().size() + " Voice Channels",
                        true);
    }
}
