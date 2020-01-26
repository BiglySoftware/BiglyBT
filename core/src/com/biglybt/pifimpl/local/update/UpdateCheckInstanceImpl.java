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

import java.util.*;

import com.biglybt.core.logging.LogEvent;
import com.biglybt.core.logging.LogIDs;
import com.biglybt.core.logging.Logger;
import com.biglybt.core.util.AEMonitor;
import com.biglybt.core.util.AESemaphore;
import com.biglybt.core.util.AEThread2;
import com.biglybt.core.util.Debug;
import com.biglybt.pif.update.*;
import com.biglybt.pif.utils.resourcedownloader.ResourceDownloader;

public class
UpdateCheckInstanceImpl
	implements UpdateCheckInstance
{
	private static final LogIDs LOGID = LogIDs.CORE;

	private static UpdateCheckInstanceImpl	active_checker;


	private List<UpdateCheckInstanceListener>	listeners			= new ArrayList<>();
	private List<UpdateImpl>					updates 			= new ArrayList<>();
	private List<UpdateManagerDecisionListener>	decision_listeners	= new ArrayList<>();


	private AESemaphore	sem 	= new AESemaphore("UpdateCheckInstance");

	private UpdateManager	manager;
	private int				check_type;
	private String			name;

	private UpdatableComponentImpl[]		components;
	private UpdateCheckerImpl[]				checkers;

	private boolean		completed;
	private boolean		cancelled;

	private boolean		automatic	= true;
	private boolean		low_noise	= false;

	protected AEMonitor this_mon 	= new AEMonitor( "UpdateCheckInstance" );

	private Map<Integer,Object>	properties = new HashMap<>();

	{
		properties.put( PT_UI_STYLE, PT_UI_STYLE_DEFAULT );
	}

	protected
	UpdateCheckInstanceImpl(
		UpdateManager				_manager,
		int							_check_type,
		String						_name,
		UpdatableComponentImpl[]	_components )
	{
		manager		= _manager;
		check_type	= _check_type;
		name		= _name;
		components	= _components;

		checkers	= new UpdateCheckerImpl[components.length];

 		for (int i=0;i<components.length;i++){

			UpdatableComponentImpl	comp = components[i];

			checkers[i] = new UpdateCheckerImpl( this, comp, sem );
		}
	}

	@Override
	public int
	getType()
	{
		return( check_type );
	}

	@Override
	public String
	getName()
	{
		return( name );
	}

	@Override
	public void
	addUpdatableComponent(
		UpdatableComponent		component,
		boolean					mandatory )
	{
			// add new component

		UpdatableComponentImpl	comp = new UpdatableComponentImpl( component, mandatory );

		UpdatableComponentImpl[]	new_comps = new UpdatableComponentImpl[components.length+1];

		System.arraycopy( components, 0, new_comps, 0, components.length );

		new_comps[components.length]	= comp;

		components	= new_comps;

			// add a new checker

		UpdateCheckerImpl	checker = new UpdateCheckerImpl( this, comp, sem );

		UpdateCheckerImpl[]	new_checkers = new UpdateCheckerImpl[checkers.length+1];

		System.arraycopy( checkers, 0, new_checkers, 0, checkers.length );

		new_checkers[checkers.length]	= checker;

		checkers	= new_checkers;
	}

	@Override
	public void
	setAutomatic(
		boolean	a )
	{
		automatic = a;
	}

	@Override
	public boolean
	isAutomatic()
	{
		return( automatic );
	}

	@Override
	public void
	setLowNoise(
		boolean	a )
	{
		low_noise = a;
	}

	@Override
	public boolean
	isLowNoise()
	{
		return( low_noise );
	}

	@Override
	public Object
	getProperty(
		int		property_name )
	{
		return( properties.get( property_name ));
	}

	@Override
	public void
	setProperty(
		int		property_name,
		Object	value )
	{
		properties.put( property_name, value );
	}

	@Override
	public void
	start()
	{
			// only run one at a time - easiest approach is just to use a couple of threads
			// to backoff + check for completion

		boolean	run_now;

		synchronized( UpdateCheckInstanceImpl.class ){

			if ( active_checker == null ){

				// System.out.println( "UCI: starting " + getName());

				active_checker = this;

				run_now = true;

				new AEThread2( "UCI:clearer" )
				{
					@Override
					public void
					run()
					{
							// wait until the process completes then clear down the 'active' one

						while( true ){

							try{
								Thread.sleep(1000);

							}catch( Throwable e ){
							}

							if ( isCompleteOrCancelled()){

								boolean	done = true;

								if ( completed ){

									Update[] updates = getUpdates();

									for ( Update update: updates ){

										if ( !( update.isCancelled() || update.isComplete())){

											done = false;

											break;
										}
									}
								}

								if ( done ){

									try{
										Thread.sleep( 5000 );

									}catch( Throwable e ){
									}

									// System.out.println( "UCI: done " + getName());

									synchronized( UpdateCheckInstanceImpl.class ){

										active_checker = null;
									}

									break;
								}
							}
						}
					}
				}.start();

			}else{

				run_now = false;

				// System.out.println( "UCI: waiting " + getName());

				new AEThread2( "UCI:waiter" )
				{
					@Override
					public void
					run()
					{
							// wait until inactive then re-attempt

						while( true ){

							try{
								Thread.sleep(1000);

							}catch( Throwable e ){
							}

							boolean	retry = false;

							synchronized( UpdateCheckInstanceImpl.class ){

								if ( active_checker == null ){

									retry = true;
								}
							}

							if ( retry ){

								UpdateCheckInstanceImpl.this.start();

								break;
							}
						}
					}
				}.start();
			}
		}

		if ( run_now ){

			startSupport();
		}
	}

	private void
	startSupport()
	{
		for (int i=0;i<components.length;i++){

			final UpdateCheckerImpl			checker = checkers[i];

			new AEThread2( "UpdatableComponent Checker:" + i, true )
			{
				@Override
				public void
				run()
				{
					try{
						checker.getComponent().checkForUpdate( checker );

					}catch( Throwable e ){

						checker.reportProgress( "Update check failed: " + Debug.getNestedExceptionMessage( e ));

						e.printStackTrace();

						checker.setFailed( new Exception( "Update check failed", e ));
					}
				}
			}.start();
		}

		new AEThread2( "UpdatableComponent Completion Waiter", true )
			{
				@Override
				public void
				run()
				{
					for (int i=0;i<components.length;i++){

						sem.reserve();
					}

					boolean	any_failed = false;
					
					try{
						boolean	mandatory_failed = false;

						for (int i=0;i<checkers.length;i++){

							if ( checkers[i].getFailed()){
								
								any_failed = true;

								if ( components[i].isMandatory()){

									mandatory_failed	= true;
								}
							}
						}

						List<UpdateImpl>	target_updates = new ArrayList<>();

							// if any mandatory checks failed then we can't do any more

						if ( mandatory_failed ){

							if (Logger.isEnabled())
								Logger.log(new LogEvent(LOGID, LogEvent.LT_ERROR,
											"Dropping all updates as a mandatory update check failed"));

						}else{
								// If there are any manadatory updates then we just go ahead with them and drop the rest

							boolean	mandatory_only	= false;

							for (int i=0;i<updates.size();i++){

								UpdateImpl	update = (UpdateImpl)updates.get(i);

								if ( update.isMandatory()){

									mandatory_only	= true;

									break;
								}
							}

							for (int i=0;i<updates.size();i++){

								UpdateImpl	update = (UpdateImpl)updates.get(i);

								if ( update.isMandatory() || !mandatory_only ){

									target_updates.add( update );

								}else{
									if (Logger.isEnabled())
									  Logger.log(new LogEvent(LOGID, LogEvent.LT_ERROR,
									                          "Dropping update '" + update.getName()
	                                            + "' as non-mandatory and "
	                                            + "mandatory updates found"));
								}
							}
						}

							// maintain order here as we apply updates in the order we
							// were requested to

						Collections.sort(
							target_updates,
							new Comparator<UpdateImpl>()
							{
								@Override
								public int
								compare(
									UpdateImpl o1, UpdateImpl o2)
								{
									int i1 = getIndex( o1 );
									int i2 = getIndex( o2 );

									return( i1 - i2 );
								}

								private int
								getIndex(
									UpdateImpl	update )
								{
									UpdatableComponentImpl component = update.getComponent();

									for (int i=0;i<components.length;i++){

										if ( components[i] == component ){

											return( i );
										}
									}

									Debug.out( "Missing component!" );

									return( 0 );
								}
							});

						updates	= target_updates;

					}finally{

						try{
							this_mon.enter();

							if ( cancelled ){

								return;
							}

							completed	= true;

						}finally{

							this_mon.exit();
						}

					}

					if ( any_failed ){
						
					}
					for (int i=0;i<listeners.size();i++){

						try{
							((UpdateCheckInstanceListener)listeners.get(i)).complete( UpdateCheckInstanceImpl.this );

						}catch( Throwable e ){

							Debug.printStackTrace(e);
						}
					}
				}
			}.start();
	}

	protected UpdateImpl
	addUpdate(
		UpdatableComponentImpl	comp,
		String					update_name,
		String[]				desc,
		String					old_version,
		String					new_version,
		ResourceDownloader[]	downloaders,
		int						restart_required )
	{
		try{
			this_mon.enter();

			UpdateImpl	update =
				new UpdateImpl( this, comp, update_name, desc, old_version, new_version,
								downloaders, comp.isMandatory(), restart_required );

			updates.add( update );

			if ( cancelled ){

				update.cancel();
			}

			return( update );

		}finally{

			this_mon.exit();
		}
	}

	@Override
	public Update[]
	getUpdates()
	{
		try{
			this_mon.enter();

			Update[]	res = new Update[updates.size()];

			updates.toArray( res );

			return( res );

		}finally{

			this_mon.exit();
		}
	}

	@Override
	public UpdateChecker[]
	getCheckers()
	{
		return( checkers );
	}

	@Override
	public UpdateInstaller
	createInstaller()

		throws UpdateException
	{
		return( manager.createInstaller());
	}

	@Override
	public boolean
	isCompleteOrCancelled()
	{
		try{
			this_mon.enter();

			return( completed || cancelled );

		}finally{

			this_mon.exit();
		}
	}
	@Override
	public void
	cancel()
	{
		boolean	just_do_updates = false;

		try{
			this_mon.enter();

			if ( completed ){

				just_do_updates = true;
			}

			cancelled	= true;

		}finally{

			this_mon.exit();
		}


		for (int i=0;i<updates.size();i++){

			((UpdateImpl)updates.get(i)).cancel();
		}

		if ( !just_do_updates ){

			for (int i=0;i<checkers.length;i++){

				if ( checkers[i] != null ){

					checkers[i].cancel();
				}
			}

			for (int i=0;i<listeners.size();i++){

				try{
					((UpdateCheckInstanceListener)listeners.get(i)).cancelled( this );

				}catch( Throwable e ){

					Debug.printStackTrace(e);
				}
			}
		}
	}

	@Override
	public boolean
	isCancelled()
	{
		return( cancelled );
	}

	@Override
	public UpdateManager
	getManager()
	{
		return( manager );
	}

	protected Object
	getDecision(
		Update		update,
		int			decision_type,
		String		decision_name,
		String		decision_description,
		Object		decision_data )
	{
		for (int i=0;i<decision_listeners.size();i++){

			Object res =
				((UpdateManagerDecisionListener)decision_listeners.get(i)).decide(
						update, decision_type, decision_name, decision_description, decision_data );

			if ( res != null ){

				return( res );
			}
		}

		return( null );
	}

	@Override
	public void
	addDecisionListener(
		UpdateManagerDecisionListener	l )
	{
		decision_listeners.add(l);
	}

	@Override
	public void
	removeDecisionListener(
		UpdateManagerDecisionListener	l )
	{
		decision_listeners.remove(l);
	}

	@Override
	public void
	addListener(
		UpdateCheckInstanceListener	l )
	{
		listeners.add( l );

		if ( completed ){

			l.complete( this );

		}else if ( cancelled ){

			l.cancelled( this );
		}
	}

	@Override
	public void
	removeListener(
		UpdateCheckInstanceListener	l )
	{
		listeners.remove(l);
	}
}
