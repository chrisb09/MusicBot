/*
 * Copyright 2016 John Grosh (jagrosh).
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

import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import com.jagrosh.jdautilities.command.CommandEvent;
import com.jagrosh.jmusicbot.Bot;
import com.jagrosh.jmusicbot.commands.OwnerCommand;
import com.jagrosh.jmusicbot.datalog.CommandLogContext;
import net.dv8tion.jda.api.entities.channel.ChannelType;
import org.json.JSONObject;

/**
 *
 * @author John Grosh (jagrosh)
 */
public class EvalCmd extends OwnerCommand 
{
    private final String engine;
    
    public EvalCmd(Bot bot)
    {
        super(bot);
        this.name = "eval";
        this.help = "evaluates nashorn code";
        this.aliases = bot.getConfig().getAliases(this.name);
        this.engine = bot.getConfig().getEvalEngine();
        this.guildOnly = false;
    }
    
    @Override
    public void doCommand(CommandEvent event) 
    {
        CommandLogContext.setManualLogging();
        ScriptEngine se = new ScriptEngineManager().getEngineByName(engine);
        if(se == null)
        {
            event.replyError("The eval engine provided in the config (`"+engine+"`) doesn't exist. This could be due to an invalid "
                    + "engine name, or the engine not existing in your version of java (`"+System.getProperty("java.version")+"`).");
            logCommandEvent(event, "ERROR", new JSONObject().put("error_reason", "engine_not_found"));
            return;
        }
        se.put("bot", bot);
        se.put("event", event);
        se.put("jda", event.getJDA());
        if (event.getChannelType() != ChannelType.PRIVATE) {
            se.put("guild", event.getGuild());
            se.put("channel", event.getChannel());
        }
        try
        {
            event.reply(event.getClient().getSuccess()+" Evaluated Successfully:\n```\n"+se.eval(event.getArgs())+" ```");
            logCommandEvent(event, "OK", new JSONObject().put("invoked", true));
        } 
        catch(Exception e)
        {
            event.reply(event.getClient().getError()+" An exception was thrown:\n```\n"+e+" ```");
            logCommandEvent(event, "ERROR", new JSONObject().put("error_reason", "eval_exception"));
        }
    }

    private void logCommandEvent(CommandEvent event, String result, JSONObject meta)
    {
        if(bot.getDataLogService() == null || event == null || event.getGuild() == null)
            return;
        bot.getDataLogService().logCommandEvent(event.getGuild(), event.getAuthor(), getName(),
                null, result, meta == null ? null : meta.toString());
    }
    
}
