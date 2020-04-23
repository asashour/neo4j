/*
 * Copyright (c) 2002-2020 "Neo4j,"
 * Neo4j Sweden AB [http://neo4j.com]
 *
 * This file is part of Neo4j.
 *
 * Neo4j is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.neo4j.test.rule;

import org.hamcrest.Matcher;
import org.hamcrest.TypeSafeMatcher;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.neo4j.test.OtherThreadExecutor;

import static java.util.concurrent.TimeUnit.SECONDS;

public class OtherThreadRuleJUnit5<STATE> implements BeforeEachCallback, AfterEachCallback
{
    private final String name;
    private final long timeout;
    private final TimeUnit unit;
    private volatile OtherThreadExecutor<STATE> executor;

    public OtherThreadRuleJUnit5()
    {
        this( null );
    }

    public OtherThreadRuleJUnit5( String name )
    {
        this( name, 60, SECONDS );
    }

    public OtherThreadRuleJUnit5( long timeout, TimeUnit unit )
    {
        this( null, timeout, unit );
    }

    public OtherThreadRuleJUnit5( String name, long timeout, TimeUnit unit )
    {
        this.name = name;
        this.timeout = timeout;
        this.unit = unit;
    }

    public <RESULT> Future<RESULT> execute( OtherThreadExecutor.WorkerCommand<STATE, RESULT> cmd )
    {
        Future<RESULT> future = executor.executeDontWait( cmd );
        try
        {
            executor.awaitStartExecuting();
        }
        catch ( InterruptedException e )
        {
            throw new RuntimeException( "Interrupted while awaiting start of execution.", e );
        }
        return future;
    }

    protected STATE initialState()
    {
        return null;
    }

    public static Matcher<OtherThreadRuleJUnit5> isWaiting()
    {
        return isThreadState( Thread.State.WAITING, Thread.State.TIMED_WAITING );
    }

    private static Matcher<OtherThreadRuleJUnit5> isThreadState( final Thread.State... eitherOfStates )
    {
        return new TypeSafeMatcher<>()
        {
            @Override
            protected boolean matchesSafely( OtherThreadRuleJUnit5 rule )
            {
                try
                {
                    rule.executor.waitUntilThreadState( eitherOfStates );
                    return true;
                }
                catch ( TimeoutException e )
                {
                    rule.executor.printStackTrace( System.err );
                    return false;
                }
            }

            @Override
            public void describeTo( org.hamcrest.Description description )
            {
                description.appendText( "Thread blocked in state WAITING" );
            }
        };
    }

    public OtherThreadExecutor<STATE> get()
    {
        return executor;
    }

    public void interrupt()
    {
        executor.interrupt();
    }

    @Override
    public String toString()
    {
        OtherThreadExecutor<STATE> otherThread = executor;
        if ( otherThread == null )
        {
            return "OtherThreadRule[state=dead]";
        }
        return otherThread.toString();
    }

    @Override
    public void beforeEach( ExtensionContext context )
    {
        String displayName = context.getDisplayName();

        String threadName = name != null
                ? name + '-' + displayName
                : displayName;
        init( threadName );
    }

    @Override
    public void afterEach( ExtensionContext context )
    {
        try
        {
            executor.close();
        }
        finally
        {
            executor = null;
        }
    }

    public void init( String threadName )
    {
        executor = new OtherThreadExecutor<>( threadName, timeout, unit, initialState() );
    }

    public void close()
    {
        executor.close();
    }
}
