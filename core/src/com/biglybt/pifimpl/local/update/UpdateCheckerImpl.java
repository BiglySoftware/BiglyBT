/*
 * Created on 12-May-2004
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

package com.biglybt.pifimpl.local.update;

/**
 * @author parg
 *
 */

import java.util.ArrayList;
import java.util.List;

import com.biglybt.core.util.AEMonitor;
import com.biglybt.core.util.AESemaphore;
import com.biglybt.core.util.Debug;
import com.biglybt.pif.update.*;
import com.biglybt.pif.utils.resourcedownloader.ResourceDownloader;

public class
UpdateCheckerImpl
	implements UpdateChecker
{
	private UpdateCheckInstanceImpl		check_instance;
	private UpdatableComponentImpl		component;
	private AESemaphore					semaphore;

	private boolean						completed;
	private boolean						failed;
	private Throwable						failure;
	private boolean						cancelled;

	private boolean						sem_released;


	private List	listeners			= new ArrayList();
	private List	progress_listeners	= new ArrayList();

	private AEMonitor this_mon 	= new AEMonitor( "UpdateChecker" );

	protected
	UpdateCheckerImpl(
		UpdateCheckInstanceImpl	_check_instance,
		UpdatableComponentImpl	_component,
		AESemaphore				_sem )
	{
		check_instance		= _check_instance;
		component			= _component;
		semaphore			= _sem;
	}

	@Override
	public UpdateCheckInstance
	getCheckInstance()
	{
		return( check_instance );
	}

	@Override
	public Update
	addUpdate(
		String				name,
		String[]			description,
		String				old_version,
		String				new_version,
		ResourceDownloader	downloader,
		int					restart_required )
	{
		return(	addUpdate(
					name, description, old_version, new_version,
					new ResourceDownloader[]{ downloader },
					restart_required ));
	}

	@Override
	public Update
	addUpdate(
		String					name,
		String[]				description,
		String					old_version,
		String					new_version,
		ResourceDownloader[]	downloaders,
		int						restart_required )
	{
		reportProgress( "Adding update: " + name );

		return( check_instance.addUpdate(
					component, name, description, old_version, new_version,
					downloaders, restart_required ));
	}

	@Override
	public UpdateInstaller
	createInstaller()

		throws UpdateException
	{
		return( check_instance.createInstaller());
	}

	@Override
	public UpdatableComponent
	getComponent()
	{
		return( component.getComponent());
	}

	@Override
	public void
	completed()
	{
		try{
			this_mon.enter();

			if ( !sem_released ){

				completed	= true;

				for (int i=0;i<listeners.size();i++){

					try{
						((UpdateCheckerListener)listeners.get(i)).completed( this );

					}catch( Throwable e ){

						Debug.printStackTrace( e );
					}
				}

				sem_released	= true;

				semaphore.release();
			}
		}finally{

			this_mon.exit();
		}
	}

	@Override
	public void
	setFailed(
		Throwable reason )
	{
		try{
			this_mon.enter();

			if ( !sem_released ){

				failed	= true;
				failure	= reason;
				
				for (int i=0;i<listeners.size();i++){

					try{
						((UpdateCheckerListener)listeners.get(i)).failed( this );

					}catch( Throwable e ){

						Debug.printStackTrace( e );
					}
				}

				sem_released	= true;

				semaphore.release();
			}
		}finally{

			this_mon.exit();
		}
	}

	public boolean
	getFailed()
	{
		return( failed );
	}

	@Override
	public Throwable getFailureReason(){
		
		return( failure );
	}
	
	protected void
	cancel()
	{
		cancelled	= true;

		for (int i=0;i<listeners.size();i++){

			try{
				((UpdateCheckerListener)listeners.get(i)).cancelled( this );

			}catch( Throwable e ){

				Debug.printStackTrace(e);
			}
		}
	}

	@Override
	public void
	addListener(
		UpdateCheckerListener	l )
	{
		try{
			this_mon.enter();

			listeners.add( l );

			if ( failed ){

				l.failed( this );

			}else if ( completed ){

				l.completed( this );
			}

			if ( cancelled ){

				l.cancelled( this );

			}
		}finally{

			this_mon.exit();
		}
	}

	@Override
	public void
	removeListener(
		UpdateCheckerListener	l )
	{
		try{
			this_mon.enter();

			listeners.remove(l);

		}finally{

			this_mon.exit();
		}
	}

	@Override
	public void
	reportProgress(
		String		str )
	{
		List	ref = progress_listeners;

		for (int i=0;i<ref.size();i++){

			try{
				((UpdateProgressListener)ref.get(i)).reportProgress( str );

			}catch( Throwable e ){

				Debug.printStackTrace(e);
			}
		}
	}

	@Override
	public void
	addProgressListener(
		UpdateProgressListener	l )
	{
		try{
			this_mon.enter();

			List	new_l = new ArrayList( progress_listeners );

			new_l.add( l );

			progress_listeners	= new_l;

		}finally{

			this_mon.exit();
		}
	}

	@Override
	public void
	removeProgressListener(
		UpdateProgressListener	l )
	{
		try{
			this_mon.enter();

			List	new_l = new ArrayList( progress_listeners );

			new_l.remove( l );

			progress_listeners	= new_l;

		}finally{

			this_mon.exit();
		}
	}
}
