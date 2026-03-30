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
package com.jagrosh.jmusicbot.commands;

import com.jagrosh.jdautilities.command.Command;
import com.jagrosh.jdautilities.command.CommandEvent;
import com.jagrosh.jmusicbot.Bot;
import com.jagrosh.jmusicbot.datalog.CommandLogContext;
import org.json.JSONObject;

public abstract class LoggedCommand extends Command
{
    protected final Bot bot;

    public LoggedCommand(Bot bot)
    {
        this.bot = bot;
    }

    @Override
    protected final void execute(CommandEvent event)
    {
        CommandLogContext.clear();
        try
        {
            doCommand(event);
            CommandLogContext.CommandLogEntry entry = CommandLogContext.consume();
            if(entry != null && entry.manual)
                return;
            String result = entry == null || entry.result == null ? "OK" : entry.result;
            JSONObject meta = entry == null ? null : entry.meta;
            logCommand(event, result, meta);
        }
        catch(Exception ex)
        {
            CommandLogContext.CommandLogEntry entry = CommandLogContext.consume();
            JSONObject meta = entry == null ? null : entry.meta;
            if(meta == null)
                meta = new JSONObject();
            meta.put("error_reason", ex.getClass().getSimpleName());
            logCommand(event, "ERROR", meta);
            throw ex;
        }
    }

    public abstract void doCommand(CommandEvent event);

    protected void logCommand(CommandEvent event, String result, JSONObject meta)
    {
        if(bot.getDataLogService() == null || event == null || event.getGuild() == null)
            return;
        bot.getDataLogService().logCommandEvent(event.getGuild(), event.getAuthor(), getName(),
                event.getArgs(), result, meta == null ? null : meta.toString());
    }
}
