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

import com.jagrosh.jdautilities.command.CommandEvent;
import com.jagrosh.jmusicbot.Bot;
import com.jagrosh.jmusicbot.commands.OwnerCommand;
import com.jagrosh.jmusicbot.datalog.CommandLogContext;
import net.dv8tion.jda.api.OnlineStatus;
import org.json.JSONObject;

/**
 *
 * @author John Grosh <john.a.grosh@gmail.com>
 */
public class SetstatusCmd extends OwnerCommand
{
    public SetstatusCmd(Bot bot)
    {
        super(bot);
        this.name = "setstatus";
        this.help = "sets the status the bot displays";
        this.arguments = "<status>";
        this.aliases = bot.getConfig().getAliases(this.name);
        this.guildOnly = false;
    }
    
    @Override
    public void doCommand(CommandEvent event) 
    {
        try {
            OnlineStatus status = OnlineStatus.fromKey(event.getArgs());
            if(status==OnlineStatus.UNKNOWN)
            {
                event.replyError("Please include one of the following statuses: `ONLINE`, `IDLE`, `DND`, `INVISIBLE`");
                CommandLogContext.setError("invalid_status");
            }
            else
            {
                OnlineStatus before = event.getJDA().getPresence().getStatus();
                event.getJDA().getPresence().setStatus(status);
                event.replySuccess("Set the status to `"+status.getKey().toUpperCase()+"`");
                CommandLogContext.setMeta(new JSONObject()
                        .put("before", before == null ? JSONObject.NULL : before.getKey())
                        .put("after", status.getKey()));
            }
        } catch(Exception e) {
            event.reply(event.getClient().getError()+" The status could not be set!");
            CommandLogContext.setError("set_status_failed");
        }
    }
}
