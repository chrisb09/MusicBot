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

import com.jagrosh.jdautilities.command.CommandEvent;
import com.jagrosh.jmusicbot.Bot;
import com.jagrosh.jmusicbot.audio.AudioHandler;
import com.jagrosh.jmusicbot.audio.QueuedTrack;
import com.jagrosh.jmusicbot.commands.MusicCommand;
import com.jagrosh.jmusicbot.datalog.CommandLogContext;
import com.jagrosh.jmusicbot.settings.Settings;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.User;
import org.json.JSONObject;

/**
 *
 * @author John Grosh <john.a.grosh@gmail.com>
 */
public class RemoveCmd extends MusicCommand 
{
    public RemoveCmd(Bot bot)
    {
        super(bot);
        this.name = "remove";
        this.help = "removes a song from the queue";
        this.arguments = "<position|ALL>";
        this.aliases = bot.getConfig().getAliases(this.name);
        this.beListening = true;
        this.bePlaying = true;
    }

    @Override
    public void doCommand(CommandEvent event) 
    {
        AudioHandler handler = (AudioHandler)event.getGuild().getAudioManager().getSendingHandler();
        if(handler.getQueue().isEmpty())
        {
            event.replyError("There is nothing in the queue!");
            CommandLogContext.setError("empty_queue");
            return;
        }
        if(event.getArgs().equalsIgnoreCase("all"))
        {
            int sizeBefore = handler.getQueue().size();
            int count = handler.getQueue().removeAll(event.getAuthor().getIdLong());
            if(count==0)
            {
                event.replyWarning("You don't have any songs in the queue!");
                CommandLogContext.setError("no_entries_for_user");
            }
            else
                event.replySuccess("Successfully removed your "+count+" entries.");
            JSONObject meta = new JSONObject()
                    .put("removed_count", count)
                    .put("queue_size_before", sizeBefore)
                    .put("queue_size_after", handler.getQueue().size());
            CommandLogContext.setMeta(meta);
            if(bot.getDataLogService() != null)
                bot.getDataLogService().logQueueEventWithMeta(event.getGuild(), event.getAuthor(), null,
                        "REMOVE_ALL", "SELF", null, null, null, meta.toString());
            return;
        }
        int pos;
        try {
            pos = Integer.parseInt(event.getArgs());
        } catch(NumberFormatException e) {
            pos = 0;
        }
        if(pos<1 || pos>handler.getQueue().size())
        {
            event.replyError("Position must be a valid integer between 1 and "+handler.getQueue().size()+"!");
            CommandLogContext.setError("invalid_position");
            return;
        }
        Settings settings = event.getClient().getSettingsFor(event.getGuild());
        boolean isDJ = event.getMember().hasPermission(Permission.MANAGE_SERVER);
        if(!isDJ)
            isDJ = event.getMember().getRoles().contains(settings.getRole(event.getGuild()));
        QueuedTrack qt = handler.getQueue().get(pos-1);
        if(qt.getIdentifier()==event.getAuthor().getIdLong())
        {
            handler.getQueue().remove(pos-1);
            event.replySuccess("Removed **"+qt.getTrack().getInfo().title+"** from the queue");
            JSONObject meta = new JSONObject().put("position", pos);
            CommandLogContext.setMeta(meta);
            if(bot.getDataLogService() != null)
                bot.getDataLogService().logQueueEventWithMeta(event.getGuild(), event.getAuthor(), qt.getTrack(),
                        "REMOVE", "SELF", pos, null, null, meta.toString());
        }
        else if(isDJ)
        {
            handler.getQueue().remove(pos-1);
            User u;
            try {
                u = event.getJDA().getUserById(qt.getIdentifier());
            } catch(Exception e) {
                u = null;
            }
            event.replySuccess("Removed **"+qt.getTrack().getInfo().title
                    +"** from the queue (requested by "+(u==null ? "someone" : "**"+u.getName()+"**")+")");
            JSONObject meta = new JSONObject().put("position", pos).put("removed_by_dj", true);
            CommandLogContext.setMeta(meta);
            if(bot.getDataLogService() != null)
                bot.getDataLogService().logQueueEventWithMeta(event.getGuild(), event.getAuthor(), qt.getTrack(),
                        "REMOVE", "DJ", pos, null, null, meta.toString());
        }
        else
        {
            event.replyError("You cannot remove **"+qt.getTrack().getInfo().title+"** because you didn't add it!");
            CommandLogContext.setError("not_owner");
        }
    }
}
