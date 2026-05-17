/*
 * Copyright 2026
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package com.jagrosh.jmusicbot.datalog;

public final class StatsAccess
{
    private StatsAccess() {}

    public static boolean canViewUserStats(long requesterId, long targetId, boolean canManageServer)
    {
        return requesterId == targetId || canManageServer;
    }
}
