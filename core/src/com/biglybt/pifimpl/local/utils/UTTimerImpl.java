/*
 * Created on 29-Apr-2004
 * Created by Paul Gardner
 * Copyright (C) Azureus Software, Inc, All Rights Reserved.
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 *
 */

package com.biglybt.pifimpl.local.utils;

/**
 * @author parg
 *
 */

import com.biglybt.core.CoreFactory;
import com.biglybt.core.util.*;
import com.biglybt.pif.PluginInterface;
import com.biglybt.pif.utils.UTTimer;
import com.biglybt.pif.utils.UTTimerEvent;
import com.biglybt.pif.utils.UTTimerEventPerformer;

public class
UTTimerImpl
	implements UTTimer
{
	private PluginInterface		plugin_interface;
	private Timer				timer;

	private boolean				destroyed;

	public
	UTTimerImpl(
		String				name,
		boolean				lightweight )
	{
			// this constructor is for external (non-az) use, e.g. someone using the UPnP component
			// in their own app
		if (!CoreFactory.isCoreAvailable()) {
			System.err.println("Trying to create Timer after Core shutdown");
			return;
		}

		if ( !lightweight ){

			timer = new Timer( name );
		}
	}

	protected
	UTTimerImpl(
		PluginInterface		pi,
		String				name,
		boolean				lightweight )
	{
		plugin_interface	= pi;

		if (!CoreFactory.isCoreAvailable()) { // isCoreShutDown
			System.err.println("Trying to create Timer after Core shutdown");
			return;
		}
		if ( !lightweight ){

			timer = new Timer( "Plugin " + pi.getPluginID() + ":" + name );
		}
	}

	protected
	UTTimerImpl(
		PluginInterface pi,
		String				name,
		int priority )
	{
		if (!CoreFactory.isCoreAvailable()) { // isCoreShutDown
			System.err.println("Trying to create Timer after Core shutdown");
			return;
		}
		plugin_interface	= pi;

		timer = new Timer( "Plugin " + pi.getPluginID() + ":" + name, 1, priority );
	}

	protected
	UTTimerImpl(
		PluginInterface 	pi,
		String				name,
		int					max_threads,
		int 				priority )
	{
		if (!CoreFactory.isCoreAvailable()) { // isCoreShutDown
			System.err.println("Trying to create Timer after Core shutdown");
			return;
		}
		plugin_interface	= pi;

		timer = new Timer( "Plugin " + pi.getPluginID() + ":" + name, max_threads, priority );
	}
	
	@Override
	public UTTimerEvent
	addEvent(
		long						when,
		final UTTimerEventPerformer	ext_performer )
	{
		if ( destroyed ){

			throw( new RuntimeException( "Timer has been destroyed" ));
		}

		final timerEvent	res = new timerEvent();

		TimerEventPerformer	performer =
			new TimerEventPerformer()
			{
				@Override
				public void
				perform(
					TimerEvent		ev )
				{
					UtilitiesImpl.callWithPluginThreadContext(
							plugin_interface,
							new Runnable()
							{
								@Override
								public void
								run()
								{
									res.perform( ext_performer );
								}
							});
				}
			};

		if ( timer == null ){

			res.setEvent( SimpleTimer.addEvent( "Plugin:" + ext_performer.getClass(), when, performer ));

		}else{

			res.setEvent( timer.addEvent( "Plugin:" + ext_performer.getClass(), when, performer ));

		}

		return( res );
	}

	@Override
	public UTTimerEvent
	addPeriodicEvent(
		long						periodic_millis,
		final UTTimerEventPerformer	ext_performer )
	{
		if ( destroyed ){

			throw( new RuntimeException( "Timer has been destroyed" ));
		}

		final timerEvent	res = new timerEvent();

		TimerEventPerformer	performer =
			new TimerEventPerformer()
			{
				@Override
				public void
				perform(
					TimerEvent		ev )
				{
					UtilitiesImpl.callWithPluginThreadContext(
							plugin_interface,
							new Runnable()
							{
								@Override
								public void
								run()
								{
									try{

										res.perform( ext_performer );

									}catch( Throwable e ){

										Debug.out("Plugin '" + plugin_interface.getPluginName() + " ("
											+ plugin_interface.getPluginID() + " "
											+ plugin_interface.getPluginVersion()
											+ ") caused an error while processing a timer event", e);
									}
								}
							});
				}
			};

		if ( timer == null ){

			res.setEvent( SimpleTimer.addPeriodicEvent( "Plugin:" + ext_performer.getClass(), periodic_millis, performer ));

		}else{

			res.setEvent( timer.addPeriodicEvent( "Plugin:" + ext_performer.getClass(), periodic_millis, performer ));

		}

		return( res );
	}
	
	@Override
	public int 
	getMaxThreads()
	{
		if ( timer != null ){
			
			return( timer.getThreadPool().getMaxThreads());
		}
		
		return( 0 );
	}
	
	@Override
	public int 
	getActiveThreads()
	{
		if ( timer != null ){
			
			return( timer.getThreadPool().getRunningCount());
		}
		
		return( 0 );
	}

	@Override
	public void
	destroy()
	{
		destroyed	= true;

		if ( timer != null ){

			timer.destroy();
		}
	}

	private static class
	timerEvent
		implements UTTimerEvent
	{
		protected TimerEvent				ev;
		protected TimerEventPeriodic		pev;

		protected void
		setEvent(
			TimerEventPeriodic	_ev )
		{
			pev		= _ev;
		}

		protected void
		setEvent(
			TimerEvent	_ev )
		{
			ev		= _ev;
		}

		protected void
		perform(
			UTTimerEventPerformer	p )
		{
			p.perform( this );
		}

		@Override
		public void
		cancel()
		{
			if ( ev != null ){

				ev.cancel();

			}else{

				pev.cancel();
			}
		}
	}
}
