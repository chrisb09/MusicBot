/*
 * Copyright 2017 John Grosh <john.a.grosh@gmail.com>.
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
package com.jagrosh.jmusicbot.commands.owner;

import java.io.IOException;
import java.io.InputStream;
import com.jagrosh.jdautilities.command.CommandEvent;
import com.jagrosh.jmusicbot.Bot;
import com.jagrosh.jmusicbot.commands.OwnerCommand;
import com.jagrosh.jmusicbot.datalog.CommandLogContext;
import com.jagrosh.jmusicbot.utils.OtherUtil;
import net.dv8tion.jda.api.entities.Icon;
import org.json.JSONObject;

/**
 *
 * @author John Grosh <john.a.grosh@gmail.com>
 */
public class SetavatarCmd extends OwnerCommand 
{
    public SetavatarCmd(Bot bot)
    {
        super(bot);
        this.name = "setavatar";
        this.help = "sets the avatar of the bot";
        this.arguments = "<url>";
        this.aliases = bot.getConfig().getAliases(this.name);
        this.guildOnly = false;
    }
    
    @Override
    public void doCommand(CommandEvent event) 
    {
        CommandLogContext.setManualLogging();
        String url;
        if(event.getArgs().isEmpty())
            if(!event.getMessage().getAttachments().isEmpty() && event.getMessage().getAttachments().get(0).isImage())
                url = event.getMessage().getAttachments().get(0).getUrl();
            else
                url = null;
        else
            url = event.getArgs();
        InputStream s = OtherUtil.imageFromUrl(url);
        if(s==null)
        {
            event.reply(event.getClient().getError()+" Invalid or missing URL");
            logCommandEvent(event, "ERROR", new JSONObject().put("error_reason", "invalid_avatar_url"));
        }
        else
        {
            try {
            event.getSelfUser().getManager().setAvatar(Icon.from(s)).queue(
                    v -> {
                        event.reply(event.getClient().getSuccess()+" Successfully changed avatar.");
                        logCommandEvent(event, "OK", new JSONObject()
                                .put("source", event.getArgs().isEmpty() ? "attachment" : "url"));
                    }, 
                    t -> {
                        event.reply(event.getClient().getError()+" Failed to set avatar.");
                        logCommandEvent(event, "ERROR", new JSONObject().put("error_reason", "avatar_set_failed"));
                    });
            } catch(IOException e) {
                event.reply(event.getClient().getError()+" Could not load from provided URL.");
                logCommandEvent(event, "ERROR", new JSONObject().put("error_reason", "avatar_load_failed"));
            }
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
