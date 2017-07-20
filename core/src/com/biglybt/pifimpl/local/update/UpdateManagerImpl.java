/*
 * Created on 07-May-2004
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

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import com.biglybt.core.Core;
import com.biglybt.core.util.*;
import com.biglybt.pif.update.*;
import com.biglybt.pifimpl.local.utils.UtilitiesImpl;
import com.biglybt.platform.PlatformManagerFactory;

public class
UpdateManagerImpl
	implements UpdateManager, UpdateCheckInstanceListener
{
	private static UpdateManagerImpl		singleton;

	public static UpdateManager
	getSingleton(
		Core core )
	{
		if ( singleton == null ){

			singleton = new UpdateManagerImpl( core );
		}

		return( singleton );
	}

	private Core core;

	private List<UpdateCheckInstanceImpl>	checkers = new ArrayList<>();

	private List<UpdatableComponentImpl>	components 				= new ArrayList<>();
	private List	listeners				= new ArrayList();
	private List	verification_listeners	= new ArrayList();

	private List<UpdateInstaller>	installers	= new ArrayList<>();

	protected AEMonitor	this_mon 	= new AEMonitor( "UpdateManager" );

	protected
	UpdateManagerImpl(
		Core _core )
	{
		core	= _core;

		UpdateInstallerImpl.checkForFailedInstalls( this );

			// cause the platform manager to register any updateable components

		try{
			PlatformManagerFactory.getPlatformManager();

		}catch( Throwable e ){

		}
	}

	protected Core
	getCore()
	{
		return( core );
	}

	@Override
	public void
	registerUpdatableComponent(
		UpdatableComponent		component,
		boolean					mandatory )
	{
		try{
			this_mon.enter();

			components.add( new UpdatableComponentImpl( component, mandatory ));
		}finally{

			this_mon.exit();
		}
	}

	@Override
	public UpdateCheckInstance[]
	getCheckInstances()
	{
		try{
			this_mon.enter();

			return( checkers.toArray( new UpdateCheckInstance[ checkers.size()]));

		}finally{

			this_mon.exit();
		}
	}

	@Override
	public UpdateCheckInstance
	createUpdateCheckInstance()
	{
		return( createUpdateCheckInstance( UpdateCheckInstance.UCI_UPDATE, "" ));
	}

	@Override
	public UpdateCheckInstance
	createUpdateCheckInstance(
		int			type,
		String		name )
	{
		try{
			this_mon.enter();

			UpdatableComponentImpl[]	comps = new UpdatableComponentImpl[components.size()];

			components.toArray( comps );

			UpdateCheckInstanceImpl	res = new UpdateCheckInstanceImpl( this, type, name, comps );

			checkers.add( res );

			res.addListener( this );

			for (int i=0;i<listeners.size();i++){

				((UpdateManagerListener)listeners.get(i)).checkInstanceCreated( res );
			}

			return( res );

		}finally{

			this_mon.exit();
		}
	}

	@Override
	public UpdateCheckInstanceImpl
	createEmptyUpdateCheckInstance(
		int			type,
		String		name )
	{
		return( createEmptyUpdateCheckInstance( type, name, false ));
	}

	public UpdateCheckInstanceImpl
	createEmptyUpdateCheckInstance(
		int			type,
		String		name,
		boolean		low_noise )
	{
		try{
			this_mon.enter();

			UpdatableComponentImpl[]	comps = new UpdatableComponentImpl[0];

			UpdateCheckInstanceImpl	res = new UpdateCheckInstanceImpl( this, type, name, comps );

			res.setLowNoise( low_noise );

			checkers.add( res );

			res.addListener( this );

			for (int i=0;i<listeners.size();i++){

				((UpdateManagerListener)listeners.get(i)).checkInstanceCreated( res );
			}

			return( res );

		}finally{

			this_mon.exit();
		}
	}

	@Override
	public UpdateInstaller
	createInstaller()

		throws UpdateException
	{
		UpdateInstaller	installer = new UpdateInstallerImpl( this );

		installers.add( installer );

		return( installer );
	}

	@Override
	public UpdateInstaller[]
	getInstallers()
	{
		UpdateInstaller[]	res = new UpdateInstaller[installers.size()];

		installers.toArray( res );

		return( res );
	}

	@Override
	public boolean hasPendingInstalls() {
		if (installers.size() > 0) {
			return true;
		}

		File	update_dir = new File( getUserDir() + File.separator +UpdateInstallerImpl.UPDATE_DIR );

		File[]	dirs = update_dir.listFiles();
		if ( dirs != null ){
			for (File dir : dirs) {
				if (!dir.isDirectory()) {
					continue;
				}

				if (new File(dir, "install.fail").exists()) {
					continue;
				}

				if (new File(dir, UpdateInstallerImpl.ACTIONS_UTF8).exists()) {
					return true;
				}
				if (new File(dir, UpdateInstallerImpl.ACTIONS_LEGACY).exists()) {
					return true;
				}
			}
		}

		return false;
	}

	@Override
	public void
	cancelled(
		UpdateCheckInstance		instance )
	{
		complete( instance );
	}

	@Override
	public void
	complete(
		UpdateCheckInstance		instance )
	{
		try{
			this_mon.enter();

			checkers.remove( instance );

		}finally{

			this_mon.exit();
		}
	}

	protected void
	removeInstaller(
		UpdateInstaller	installer )
	{
		installers.remove( installer );
	}

	/**
	 * @note Exactly the same as {@link UtilitiesImpl#getProgramDir()}
	 */
	@Override
	public String
	getInstallDir()
	{
		String	str = SystemProperties.getApplicationPath();

		if ( str.endsWith(File.separator)){

			str = str.substring(0,str.length()-1);
		}

		return( str );
	}

	@Override
	public String
	getUserDir()
	{
		String	str = SystemProperties.getUserPath();

		if ( str.endsWith(File.separator)){

			str = str.substring(0,str.length()-1);
		}

		return( str );
	}

	@Override
	public void
	applyUpdates(
		boolean	restart_after )

		throws UpdateException
	{
		try{
			if ( restart_after ){

				core.requestRestart();

			}else{

				core.requestStop();
			}
		}catch( Throwable e ){

			throw( new UpdateException( "UpdateManager:applyUpdates fails", e ));
		}
	}

	public InputStream
	verifyData(
		Update			update,
		InputStream		is,
		boolean			force )

		throws UpdateException
	{
		boolean	queried 	= false;
		boolean	ok			= false;
		Throwable	failure	= null;

		try{
			File	temp = AETemporaryFileHandler.createTempFile();

			FileUtil.copyFile( is, temp );

			try{
				AEVerifier.verifyData( temp );

				ok	= true;

				return( new FileInputStream( temp ));

			}catch( AEVerifierException e ){

				if ( (!force) && e.getFailureType() == AEVerifierException.FT_SIGNATURE_MISSING ){

					for (int i=0;i<verification_listeners.size();i++){

						try{
							queried	= true;

							if ( ((UpdateManagerVerificationListener)verification_listeners.get(i)).acceptUnVerifiedUpdate(
									update )){

								ok	= true;

								return( new FileInputStream( temp ));
							}
						}catch( Throwable f ){

							Debug.printStackTrace(f);
						}
					}
				}

				failure	= e;

				throw( e );
			}
		}catch( UpdateException e ){

			failure	= e;

			throw( e );

		}catch( Throwable e ){

			failure	= e;

			throw( new UpdateException( "Verification failed", e ));

		}finally{

			if ( !( queried || ok )){

				if ( failure == null ){

					failure = new UpdateException( "Verification failed" );
				}

				for (int i=0;i<verification_listeners.size();i++){

					try{
						((UpdateManagerVerificationListener)verification_listeners.get(i)).verificationFailed( update, failure );

					}catch( Throwable f ){

						Debug.printStackTrace(f);
					}
				}
			}
		}
	}


	@Override
	public void
	addVerificationListener(
		UpdateManagerVerificationListener	l )
	{
		verification_listeners.add( l );
	}

	@Override
	public void
	removeVerificationListener(
		UpdateManagerVerificationListener	l )
	{
		verification_listeners.add( l );
	}

	@Override
	public void
	addListener(
		UpdateManagerListener	l )
	{
		listeners.add(l);
	}

	@Override
	public void
	removeListener(
		UpdateManagerListener	l )
	{
		listeners.remove(l);
	}
}
