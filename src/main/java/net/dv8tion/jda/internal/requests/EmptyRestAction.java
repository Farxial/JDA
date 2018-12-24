/*
 * Copyright 2015-2018 Austin Keener & Michael Ritter & Florian Spieß
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package net.dv8tion.jda.internal.requests;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.requests.restaction.AuditableRestAction;
import net.dv8tion.jda.internal.JDAImpl;
import net.dv8tion.jda.internal.utils.Checks;

import java.util.concurrent.*;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

public class EmptyRestAction<T> implements AuditableRestAction<T>
{
    private final JDAImpl api;
    private final T returnObj;

    public EmptyRestAction(JDAImpl api)
    {
        this(api, null);
    }

    public EmptyRestAction(JDAImpl api, T returnObj)
    {
        this.api = api;
        this.returnObj = returnObj;
    }

    @Override
    public JDA getJDA()
    {
        return api;
    }

    @Override
    public AuditableRestAction<T> reason(String reason)
    {
        return this;
    }

    @Override
    public AuditableRestAction<T> setCheck(BooleanSupplier checks)
    {
        return this;
    }

    @Override
    public void queue(Consumer<? super T> success, Consumer<? super Throwable> failure)
    {
        if (success != null)
            success.accept(returnObj);
    }

    @Override
    public CompletableFuture<T> submit(boolean shouldQueue)
    {
        return CompletableFuture.completedFuture(returnObj);
    }

    @Override
    public ScheduledFuture<T> submitAfter(long delay, TimeUnit unit, ScheduledExecutorService executor)
    {
        Checks.notNull(unit, "TimeUnit");
        if (executor == null)
            executor = api.getRateLimitPool();
        return executor.schedule((Callable<T>) this::complete, delay, unit);
    }

    @Override
    public ScheduledFuture<?> queueAfter(long delay, TimeUnit unit, Consumer<? super T> success, Consumer<? super Throwable> failure, ScheduledExecutorService executor)
    {
        Checks.notNull(unit, "TimeUnit");
        if (executor == null)
            executor = api.getRateLimitPool();
        return executor.schedule(() -> queue(success, failure), delay, unit);
    }

    @Override
    public T complete(boolean shouldQueue)
    {
        return returnObj;
    }
}
