/*
 * Created on 10-Jun-2004
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

package com.biglybt.pifimpl.local.tracker;


/**
 * @author parg
 *
 */

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import com.biglybt.core.tracker.host.TRHostAuthenticationListener;
import com.biglybt.core.tracker.server.TRTrackerServerListener2;
import com.biglybt.core.util.AEMonitor;
import com.biglybt.pif.PluginInterface;
import com.biglybt.pif.tracker.Tracker;
import com.biglybt.pif.tracker.web.TrackerWebContext;
import com.biglybt.pif.tracker.web.TrackerWebPageGenerator;
import com.biglybt.pifimpl.local.utils.UtilitiesImpl;

public abstract class
TrackerWCHelper
	implements TrackerWebContext, TRHostAuthenticationListener
{
	private PluginInterface		plugin_interface;

	private Tracker		tracker;
	private List		generators 	= new ArrayList();

	protected AEMonitor this_mon 	= new AEMonitor( "TrackerWCHelper" );

	protected
	TrackerWCHelper()
	{
		plugin_interface = UtilitiesImpl.getPluginThreadContext();
	}

	protected void
	setTracker(
		Tracker		_tracker )
	{
		tracker	= _tracker;
	}

	public boolean
	handleExternalRequest(
		final TRTrackerServerListener2.ExternalRequest	external_request )

		throws IOException
	{
		return(UtilitiesImpl.callWithPluginThreadContext(
			plugin_interface,
			new UtilitiesImpl.runnableWithReturnAndException<Boolean,IOException>()
			{
				@Override
				public Boolean
				run()

					throws IOException
				{
					TrackerWebPageRequestImpl	request = new TrackerWebPageRequestImpl( tracker, TrackerWCHelper.this, external_request );
					TrackerWebPageResponseImpl	reply 	= new TrackerWebPageResponseImpl( request );

					for (int i=0;i<generators.size();i++){

						TrackerWebPageGenerator	generator;

						try{
							this_mon.enter();

							if ( i >= generators.size()){

								break;
							}

							generator = (TrackerWebPageGenerator)generators.get(i);

						}finally{

							this_mon.exit();
						}

						if ( generator.generate( request, reply )){

							reply.complete();

							return( true );
						}
					}


					return( false );
				}
			}));
	}


	@Override
	public TrackerWebPageGenerator[]
	getPageGenerators()
	{
		TrackerWebPageGenerator[]	res = new TrackerWebPageGenerator[generators.size()];

		generators.toArray( res );

		return( res );
	}

	@Override
	public void
	addPageGenerator(
		TrackerWebPageGenerator	generator )
	{
		try{
			this_mon.enter();

			generators.add( generator );

		}finally{

			this_mon.exit();
		}
	}

	@Override
	public void
	removePageGenerator(
		TrackerWebPageGenerator	generator )
	{
		try{
			this_mon.enter();

			generators.remove( generator );

		}finally{

			this_mon.exit();
		}

	}

	@Override
	public void
	destroy()
	{
		generators.clear();
	}

}
