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
package com.jagrosh.jmusicbot.datalog;

import org.json.JSONObject;

public final class CommandLogContext
{
    private static final ThreadLocal<CommandLogEntry> CONTEXT = new ThreadLocal<>();

    private CommandLogContext()
    {
    }

    public static void clear()
    {
        CONTEXT.remove();
    }

    public static void setMeta(JSONObject meta)
    {
        getOrCreate().meta = meta;
    }

    public static void setResult(String result)
    {
        getOrCreate().result = result;
    }

    public static void setError(String reason)
    {
        CommandLogEntry entry = getOrCreate();
        entry.result = "ERROR";
        if(reason != null && !reason.isEmpty())
        {
            if(entry.meta == null)
                entry.meta = new JSONObject();
            entry.meta.put("error_reason", reason);
        }
    }

    public static void setManualLogging()
    {
        getOrCreate().manual = true;
    }

    public static CommandLogEntry consume()
    {
        CommandLogEntry entry = CONTEXT.get();
        CONTEXT.remove();
        return entry;
    }

    private static CommandLogEntry getOrCreate()
    {
        CommandLogEntry entry = CONTEXT.get();
        if(entry == null)
        {
            entry = new CommandLogEntry();
            CONTEXT.set(entry);
        }
        return entry;
    }

    public static final class CommandLogEntry
    {
        public JSONObject meta;
        public String result;
        public boolean manual;
    }
}
