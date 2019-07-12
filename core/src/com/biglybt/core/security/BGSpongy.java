/*
 * Copyright (C) Bigly Software, Inc, All Rights Reserved.
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA 02111-1307  USA
 */

package com.biglybt.core.security;

import java.security.MessageDigest;
import java.util.*;
import java.util.function.Consumer;

import com.biglybt.core.Core;
import com.biglybt.core.CoreFactory;
import com.biglybt.core.config.COConfigurationManager;
import com.biglybt.core.config.ParameterListener;
import com.biglybt.core.internat.MessageText;
import com.biglybt.core.util.AENetworkClassifier;
import com.biglybt.core.util.AESemaphore;
import com.biglybt.core.util.Constants;
import com.biglybt.core.util.CopyOnWriteList;
import com.biglybt.core.util.Debug;
import com.biglybt.core.util.SystemTime;
import com.biglybt.pif.PluginAdapter;
import com.biglybt.pif.PluginEvent;
import com.biglybt.pif.PluginEventListener;
import com.biglybt.pif.PluginInterface;
import com.biglybt.pif.ipc.IPCInterface;
import com.biglybt.ui.UIFunctions;
import com.biglybt.ui.UIFunctionsManager;
import com.biglybt.ui.UIFunctionsUserPrompter;

public class 
BGSpongy
{
	private static final CopyOnWriteList<PluginInterface>		plugins = new CopyOnWriteList<>();

	private static AESemaphore plugin_init_complete = new AESemaphore( "init:waiter" );
	
	private static volatile Core				core;
	
	private static volatile IPCInterface		ipc;
	
	
	private static Object spongy_install_lock = new Object();
	
	private static boolean 				spongy_installing;
	private static boolean				spongy_installed;
	private static boolean 				spongy_install_failed;
	private static List<AESemaphore>	spongy_install_waiters = new ArrayList<>();

	
	public static void
	initialize(
		Core		_core )
	{
		core	= _core;
		
		try{			
			PluginInterface default_pi = core.getPluginManager().getDefaultPluginInterface();

			default_pi.addEventListener(
					new PluginEventListener()
					{
						@Override
						public void
						handleEvent(
							PluginEvent ev )
						{
							int	type = ev.getType();

							if ( type == PluginEvent.PEV_PLUGIN_OPERATIONAL ){

								pluginAdded((PluginInterface)ev.getValue());
							}
							if ( type == PluginEvent.PEV_PLUGIN_NOT_OPERATIONAL ){

								pluginRemoved((PluginInterface)ev.getValue());
							}
						}
					});

			PluginInterface[] plugins = default_pi.getPluginManager().getPlugins( true );

			for ( PluginInterface pi: plugins ){

				if ( pi.getPluginState().isOperational()){

					pluginAdded( pi );
				}
			}

			default_pi.addListener(
				new PluginAdapter()
				{
					@Override
					public void
					initializationComplete()
					{
						plugin_init_complete.releaseForever();
					}
				});

		}catch( Throwable e ){

			Debug.out( e );
		}
	}
	
	private static void
	pluginAdded(
		PluginInterface pi )
	{
		String pid = pi.getPluginID();

		if ( pid.equals( "bgspongy" )){

			plugins.add( pi );
			
			ipc	= pi.getIPC();
		}
	}

	private static void
	pluginRemoved(
		PluginInterface pi )
	{
		String pid = pi.getPluginID();

		if ( pid.equals( "bgspongy" )){

			plugins.remove( pi );
			
			if ( plugins.isEmpty()){
				
				ipc = null;
			}
		}
	}
	
	/**
	 * 
	 * @param algorithm digest algorithm e.g. SHA3-256
	 * @param max_wait	<0: infinite; 0: no wait; >0: max wait millis
	 */
	
	public static MessageDigest
	getDigest(
		String						algorithm,
		long						max_wait )
	{
		IPCInterface ipc = getICP( max_wait );
		
		if ( ipc == null ){
			
			return( null );
			
		}else{
			try{
				Map<String,Object>	args = new HashMap<>();
				
				args.put( "alg", algorithm );
				
				MessageDigest digest = (MessageDigest)ipc.invoke( "getDigest", new Object[]{ args });
				
				return( digest );
				
			}catch( Throwable e ){
				
				Debug.out( e );
				
				return( null );
			}
			
		}
	}
	
	private static IPCInterface
	getICP(
		long		max_wait )
	{
		IPCInterface result = ipc;
		
		if ( result != null ){
			
			return( result );
		}
		
		if ( !plugin_init_complete.isReleasedForever()){
			
			if ( max_wait == 0 ){
				
				return( null );
				
			}else if ( max_wait < 0 ){
				
				plugin_init_complete.reserve();
				
			}else{
				
				long start = SystemTime.getMonotonousTime();
				
				if ( !plugin_init_complete.reserve( max_wait )){
					
					return( null );
				}
				
				max_wait = Math.max( 0,  max_wait - ( SystemTime.getMonotonousTime() - start ));
			}
			
			result = ipc;
			
			if ( result != null ){
				
				return( result );
			}
		}
		
		AESemaphore waiter = null;
		
		synchronized( spongy_install_lock ){
			
			if ( spongy_installed ){
				
				return( ipc );
				
			}else if ( spongy_install_failed ){
				
				return( null );
			}
			
			if ( spongy_installing ){
				
				if ( max_wait != 0 ){
				
					waiter = new AESemaphore( "bgspongy" );
				
					spongy_install_waiters.add( waiter );
				}
				
			}else{
				
				PluginInterface existing = core.getPluginManager().getPluginInterfaceByID( "bgspongy", false );
				
				if ( existing != null ){
					
					return( null );
				}
				
				if ( max_wait != 0 ){
				
					waiter = new AESemaphore( "bgspongy" );
				
					spongy_install_waiters.add( waiter );
				}
				
				spongy_installing = true;
				
				UIFunctions uif = UIFunctionsManager.getUIFunctions();
				
				if ( uif == null ){
					
					installCompleted( false );
					
				}else{
					
					try{
				
						uif.installPlugin(
							"bgspongy",
							"bgspongy.install",
							new UIFunctions.actionListener()
							{
								@Override
								public void
								actionComplete(
									Object		result )
								{
									if ( result instanceof Boolean && (Boolean)result ){

										installCompleted( true );
										
									}else{
										
										installCompleted( false );
									}
								}
							});
					}catch( Throwable e ){
						
						Debug.out( e );
						
						installCompleted( false );
					}
				}
			}
		}
		
		if ( waiter != null ){
		
			if ( max_wait > 0 ){
				
				waiter.reserve( max_wait );
				
			}else{
				
				waiter.reserve();
			}
		}
		
		return( ipc );
	}
	
	private static void
	installCompleted(
		boolean	ok )
	{
		synchronized( spongy_install_lock ){
			
			if ( ok ){
				
				spongy_installed = true;
				
			}else{
				
				spongy_install_failed = true;
			}
			
			spongy_installing = false;
			
			for ( AESemaphore sem: spongy_install_waiters ){
				
				sem.release();
			}
		}
	}
}
