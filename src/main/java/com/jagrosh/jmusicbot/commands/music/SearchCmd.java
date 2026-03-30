/*
 * Copyright 2016 John Grosh <john.a.grosh@gmail.com>.
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
package com.jagrosh.jmusicbot.commands.music;

import com.jagrosh.jmusicbot.audio.RequestMetadata;
import com.jagrosh.jmusicbot.utils.TimeUtil;
import com.sedmelluq.discord.lavaplayer.player.AudioLoadResultHandler;
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException.Severity;
import com.sedmelluq.discord.lavaplayer.track.AudioPlaylist;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import java.util.concurrent.TimeUnit;
import com.jagrosh.jdautilities.command.CommandEvent;
import com.jagrosh.jdautilities.menu.OrderedMenu;
import com.jagrosh.jmusicbot.Bot;
import com.jagrosh.jmusicbot.audio.AudioHandler;
import com.jagrosh.jmusicbot.audio.QueuedTrack;
import com.jagrosh.jmusicbot.commands.MusicCommand;
import com.jagrosh.jmusicbot.datalog.CommandLogContext;
import com.jagrosh.jmusicbot.utils.FormatUtil;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Message;
import org.json.JSONObject;

/**
 *
 * @author John Grosh <john.a.grosh@gmail.com>
 */
public class SearchCmd extends MusicCommand 
{
    protected String searchPrefix = "ytsearch:";
    private final OrderedMenu.Builder builder;
    private final String searchingEmoji;
    
    public SearchCmd(Bot bot)
    {
        super(bot);
        this.searchingEmoji = bot.getConfig().getSearching();
        this.name = "search";
        this.aliases = bot.getConfig().getAliases(this.name);
        this.arguments = "<query>";
        this.help = "searches Youtube for a provided query";
        this.beListening = true;
        this.bePlaying = false;
        this.botPermissions = new Permission[]{Permission.MESSAGE_EMBED_LINKS};
        builder = new OrderedMenu.Builder()
                .allowTextInput(true)
                .useNumbers()
                .useCancelButton(true)
                .setEventWaiter(bot.getWaiter())
                .setTimeout(1, TimeUnit.MINUTES);
    }
    @Override
    public void doCommand(CommandEvent event) 
    {
        CommandLogContext.setManualLogging();
        if(event.getArgs().isEmpty())
        {
            event.replyError("Please include a query.");
            logCommandEvent(event, "ERROR", new JSONObject().put("error_reason", "missing_query"));
            return;
        }
        event.reply(searchingEmoji+" Searching... `["+event.getArgs()+"]`", 
                m -> bot.getPlayerManager().loadItemOrdered(event.getGuild(), searchPrefix + event.getArgs(), new ResultHandler(m,event)));
    }
    
    private class ResultHandler implements AudioLoadResultHandler 
    {
        private final Message m;
        private final CommandEvent event;
        
        private ResultHandler(Message m, CommandEvent event)
        {
            this.m = m;
            this.event = event;
        }
        
        @Override
        public void trackLoaded(AudioTrack track)
        {
            if(bot.getConfig().isTooLong(track))
            {
                m.editMessage(FormatUtil.filter(event.getClient().getWarning()+" This track (**"+track.getInfo().title+"**) is longer than the allowed maximum: `"
                        + TimeUtil.formatTime(track.getDuration())+"` > `"+bot.getConfig().getMaxTime()+"`")).queue();
                logCommandEvent(event, "ERROR", new JSONObject().put("error_reason", "track_too_long"));
                return;
            }
            AudioHandler handler = (AudioHandler)event.getGuild().getAudioManager().getSendingHandler();
            RequestMetadata rm = RequestMetadata.fromResultHandler(track, event, "SEARCH", null, event.getArgs());
            int pos = handler.addTrack(new QueuedTrack(track, rm))+1;
            if(bot.getDataLogService() != null)
            {
                bot.getDataLogService().logSearchSelection(event.getGuild(), event.getAuthor(), event.getArgs(), 1, track);
                bot.getDataLogService().logQueueAdd(event.getGuild(), event.getAuthor(), track, "SEARCH", pos, event.getArgs(), null);
            }
            m.editMessage(FormatUtil.filter(event.getClient().getSuccess()+" Added **"+track.getInfo().title
                    +"** (`"+ TimeUtil.formatTime(track.getDuration())+"`) "+(pos==0 ? "to begin playing"
                        : " to the queue at position "+pos))).queue();
            logCommandEvent(event, "OK", new JSONObject().put("queue_position", pos).put("selected_index", 1));
        }

        @Override
        public void playlistLoaded(AudioPlaylist playlist)
        {
            builder.setColor(event.getSelfMember().getColor())
                    .setText(FormatUtil.filter(event.getClient().getSuccess()+" Search results for `"+event.getArgs()+"`:"))
                    .setChoices(new String[0])
                    .setSelection((msg,i) -> 
                    {
                        AudioTrack track = playlist.getTracks().get(i-1);
                        if(bot.getConfig().isTooLong(track))
                        {
                            event.replyWarning("This track (**"+track.getInfo().title+"**) is longer than the allowed maximum: `"
                                    + TimeUtil.formatTime(track.getDuration())+"` > `"+bot.getConfig().getMaxTime()+"`");
                            logCommandEvent(event, "ERROR", new JSONObject().put("error_reason", "track_too_long"));
                            return;
                        }
                        AudioHandler handler = (AudioHandler)event.getGuild().getAudioManager().getSendingHandler();
                        RequestMetadata rm = RequestMetadata.fromResultHandler(track, event, "SEARCH", null, event.getArgs());
                        int pos = handler.addTrack(new QueuedTrack(track, rm))+1;
                        if(bot.getDataLogService() != null)
                        {
                            bot.getDataLogService().logSearchSelection(event.getGuild(), event.getAuthor(), event.getArgs(), i, track);
                            bot.getDataLogService().logQueueAdd(event.getGuild(), event.getAuthor(), track, "SEARCH", pos, event.getArgs(), null);
                        }
                        event.replySuccess("Added **" + FormatUtil.filter(track.getInfo().title)
                                + "** (`" + TimeUtil.formatTime(track.getDuration()) + "`) " + (pos==0 ? "to begin playing" 
                                    : " to the queue at position "+pos));
                    })
                    .setCancel((msg) -> {})
                    .setUsers(event.getAuthor())
                    ;
            for(int i=0; i<4 && i<playlist.getTracks().size(); i++)
            {
                AudioTrack track = playlist.getTracks().get(i);
                builder.addChoices("`["+ TimeUtil.formatTime(track.getDuration())+"]` [**"+track.getInfo().title+"**]("+track.getInfo().uri+")");
            }
            builder.build().display(m);
            logCommandEvent(event, "OK", new JSONObject().put("result_count", playlist.getTracks().size()));
        }

        @Override
        public void noMatches() 
        {
            m.editMessage(FormatUtil.filter(event.getClient().getWarning()+" No results found for `"+event.getArgs()+"`.")).queue();
            if(bot.getDataLogService() != null)
                bot.getDataLogService().logSearchEventWithMeta(event.getGuild(), event.getAuthor(), event.getArgs(), null, null,
                        new JSONObject().put("error_reason", "no_matches").toString());
            logCommandEvent(event, "ERROR", new JSONObject().put("error_reason", "no_matches"));
        }

        @Override
        public void loadFailed(FriendlyException throwable) 
        {
            if(throwable.severity==Severity.COMMON)
                m.editMessage(event.getClient().getError()+" Error loading: "+throwable.getMessage()).queue();
            else
                m.editMessage(event.getClient().getError()+" Error loading track.").queue();
            if(bot.getDataLogService() != null)
                bot.getDataLogService().logSearchEventWithMeta(event.getGuild(), event.getAuthor(), event.getArgs(), null, null,
                        new JSONObject().put("error_reason", throwable.getMessage()).toString());
            logCommandEvent(event, "ERROR", new JSONObject().put("error_reason", throwable.getMessage()));
        }
    }

    private void logCommandEvent(CommandEvent event, String result, JSONObject meta)
    {
        if(bot.getDataLogService() == null || event == null || event.getGuild() == null)
            return;
        bot.getDataLogService().logCommandEvent(event.getGuild(), event.getAuthor(), getName(),
                event.getArgs(), result, meta == null ? null : meta.toString());
    }
}
