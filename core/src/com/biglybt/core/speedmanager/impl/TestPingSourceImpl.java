/*
 * Created on Aug 8, 2007
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


package com.biglybt.core.speedmanager.impl;

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;

import com.biglybt.core.dht.speed.DHTSpeedTester;
import com.biglybt.core.dht.speed.DHTSpeedTesterContact;
import com.biglybt.core.dht.speed.DHTSpeedTesterContactListener;
import com.biglybt.core.dht.speed.DHTSpeedTesterListener;
import com.biglybt.core.util.CopyOnWriteList;
import com.biglybt.core.util.SimpleTimer;
import com.biglybt.core.util.TimerEvent;
import com.biglybt.core.util.TimerEventPerformer;

public abstract class
TestPingSourceImpl
	implements DHTSpeedTester
{
	private final SpeedManagerAlgorithmProviderAdapter		adapter;

	private volatile int		contact_num;

	private final List	listeners 	= new ArrayList();

	final CopyOnWriteList	sources		= new CopyOnWriteList();

	private int		period;

	protected
	TestPingSourceImpl(
		SpeedManagerAlgorithmProviderAdapter		_adapter )
	{
		adapter	= _adapter;

		SimpleTimer.addPeriodicEvent(
			"TestPingSourceImpl",
			1000,
			new TimerEventPerformer()
			{
				private int	ticks;

				@Override
				public void
				perform(
					TimerEvent event )
				{
					ticks++;

					List	sources_to_update;

					synchronized( sources ){

						while( sources.size() < contact_num ){

							addContact( new testSource());
						}

						sources_to_update = sources.getList();
					}

					if ( period > 0 ){

						if ( ticks % period == 0 ){

							testSource[]	contacts = new testSource[sources_to_update.size()];

							sources_to_update.toArray( contacts );

							update( contacts );
						}
					}
				}
			});
	}

	protected SpeedManagerAlgorithmProviderAdapter
	getAdapter()
	{
		return( adapter );
	}

	protected void
	update(
		testSource[]		contacts )
	{
		int[]	round_trip_times = new int[contacts.length];

		updateSources( contacts );

		for (int i=0;i<round_trip_times.length;i++){

			round_trip_times[i] = contacts[i].getRTT();
		}

		for (int i=0;i<listeners.size();i++){

			((DHTSpeedTesterListener)listeners.get(i)).resultGroup( contacts, round_trip_times );
		}
	}

	protected abstract void
	updateSources(
		testSource[]	sources );

	@Override
	public int
	getContactNumber()
	{
		return( contact_num );
	}

	@Override
	public void
	setContactNumber(
		int		number )
	{
		contact_num = number;
	}

	protected void
	addContact(
		testSource	contact )
	{
		synchronized( sources ){

			sources.add( contact );
		}

		for (int i=0;i<listeners.size();i++){

			((DHTSpeedTesterListener)listeners.get(i)).contactAdded(contact);
		}
	}

	protected void
	removeContact(
		testSource	contact )
	{
		synchronized( sources ){

			sources.remove( contact );
		}
	}


	@Override
	public void
	destroy()
	{
		for (int i=0;i<listeners.size();i++){

			((DHTSpeedTesterListener)listeners.get(i)).destroyed();
		}
	}

	@Override
	public void
	addListener(
		DHTSpeedTesterListener	listener )
	{
		listeners.add( listener );
	}

	@Override
	public void
	removeListener(
		DHTSpeedTesterListener	listener )
	{
		listeners.remove( listener );
	}

	protected class
	testSource
		implements DHTSpeedTesterContact
	{
		private final InetSocketAddress address = new InetSocketAddress( 1 );

		private final List	listeners = new ArrayList();

		private int		rtt;

		@Override
		public InetSocketAddress
		getAddress()
		{
			return( address );
		}

		@Override
		public String
		getString()
		{
			return( "test source" );
		}

		@Override
		public int
		getPingPeriod()
		{
			return( period );
		}

		@Override
		public void
		setPingPeriod(
			int		period_secs )
		{
			period = period_secs;
		}

		protected int
		getRTT()
		{
			return( rtt );
		}

		protected void
		setRTT(
			int		_rtt )
		{
			rtt	= _rtt;
		}

		protected void
		failed()
		{
			for (int i=0;i<listeners.size();i++){

				((DHTSpeedTesterContactListener)listeners.get(i)).contactDied( this );
			}
		}

		@Override
		public void
		destroy()
		{
			removeContact( this );
		}

		@Override
		public void
		addListener(
			DHTSpeedTesterContactListener	listener )
		{
			listeners.add( listener );
		}

		@Override
		public void
		removeListener(
			DHTSpeedTesterContactListener	listener )
		{
			listeners.remove( listener );
		}
	}
}
