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
import net.dv8tion.jda.api.entities.Activity;
import org.json.JSONObject;

/**
 *
 * @author John Grosh <john.a.grosh@gmail.com>
 */
public class SetgameCmd extends OwnerCommand
{
    public SetgameCmd(Bot bot)
    {
        super(bot);
        this.name = "setgame";
        this.help = "sets the game the bot is playing";
        this.arguments = "[action] [game]";
        this.aliases = bot.getConfig().getAliases(this.name);
        this.guildOnly = false;
        this.children = new OwnerCommand[]{
            new SetlistenCmd(),
            new SetstreamCmd(),
            new SetwatchCmd()
        };
    }
    
    @Override
    public void doCommand(CommandEvent event) 
    {
        String title = event.getArgs().toLowerCase().startsWith("playing") ? event.getArgs().substring(7).trim() : event.getArgs();
        try
        {
            event.getJDA().getPresence().setActivity(title.isEmpty() ? null : Activity.playing(title));
            event.reply(event.getClient().getSuccess()+" **"+event.getSelfUser().getName()
                    +"** is "+(title.isEmpty() ? "no longer playing anything." : "now playing `"+title+"`"));
            CommandLogContext.setMeta(new JSONObject()
                    .put("activity_type", "PLAYING")
                    .put("title", title.isEmpty() ? JSONObject.NULL : title));
        }
        catch(Exception e)
        {
            event.reply(event.getClient().getError()+" The game could not be set!");
            CommandLogContext.setError("set_game_failed");
        }
    }
    
    private class SetstreamCmd extends OwnerCommand
    {
        private SetstreamCmd()
        {
            super(SetgameCmd.this.bot);
            this.name = "stream";
            this.aliases = new String[]{"twitch","streaming"};
            this.help = "sets the game the bot is playing to a stream";
            this.arguments = "<username> <game>";
            this.guildOnly = false;
        }

        @Override
        public void doCommand(CommandEvent event)
        {
            String[] parts = event.getArgs().split("\\s+", 2);
            if(parts.length<2)
            {
                event.replyError("Please include a twitch username and the name of the game to 'stream'");
                CommandLogContext.setError("missing_stream_args");
                return;
            }
            try
            {
                event.getJDA().getPresence().setActivity(Activity.streaming(parts[1], "https://twitch.tv/"+parts[0]));
                event.replySuccess("**"+event.getSelfUser().getName()
                        +"** is now streaming `"+parts[1]+"`");
                CommandLogContext.setMeta(new JSONObject()
                        .put("activity_type", "STREAMING")
                        .put("title", parts[1])
                        .put("user", parts[0]));
            }
            catch(Exception e)
            {
                event.reply(event.getClient().getError()+" The game could not be set!");
                CommandLogContext.setError("set_game_failed");
            }
        }
    }
    
    private class SetlistenCmd extends OwnerCommand
    {
        private SetlistenCmd()
        {
            super(SetgameCmd.this.bot);
            this.name = "listen";
            this.aliases = new String[]{"listening"};
            this.help = "sets the game the bot is listening to";
            this.arguments = "<title>";
            this.guildOnly = false;
        }

        @Override
        public void doCommand(CommandEvent event)
        {
            if(event.getArgs().isEmpty())
            {
                event.replyError("Please include a title to listen to!");
                CommandLogContext.setError("missing_listen_title");
                return;
            }
            String title = event.getArgs().toLowerCase().startsWith("to") ? event.getArgs().substring(2).trim() : event.getArgs();
            try
            {
                event.getJDA().getPresence().setActivity(Activity.listening(title));
                event.replySuccess("**"+event.getSelfUser().getName()+"** is now listening to `"+title+"`");
                CommandLogContext.setMeta(new JSONObject().put("activity_type", "LISTENING").put("title", title));
            } catch(Exception e) {
                event.reply(event.getClient().getError()+" The game could not be set!");
                CommandLogContext.setError("set_game_failed");
            }
        }
    }
    
    private class SetwatchCmd extends OwnerCommand
    {
        private SetwatchCmd()
        {
            super(SetgameCmd.this.bot);
            this.name = "watch";
            this.aliases = new String[]{"watching"};
            this.help = "sets the game the bot is watching";
            this.arguments = "<title>";
            this.guildOnly = false;
        }

        @Override
        public void doCommand(CommandEvent event)
        {
            if(event.getArgs().isEmpty())
            {
                event.replyError("Please include a title to watch!");
                CommandLogContext.setError("missing_watch_title");
                return;
            }
            String title = event.getArgs();
            try
            {
                event.getJDA().getPresence().setActivity(Activity.watching(title));
                event.replySuccess("**"+event.getSelfUser().getName()+"** is now watching `"+title+"`");
                CommandLogContext.setMeta(new JSONObject().put("activity_type", "WATCHING").put("title", title));
            } catch(Exception e) {
                event.reply(event.getClient().getError()+" The game could not be set!");
                CommandLogContext.setError("set_game_failed");
            }
        }
    }
}
