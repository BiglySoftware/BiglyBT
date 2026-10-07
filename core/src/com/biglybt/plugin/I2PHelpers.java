/*
 * Created on Mar 6, 2015
 * Created by Paul Gardner
 *
 * Copyright 2015 Azureus Software, Inc.  All rights reserved.
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
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA 02111-1307, USA.
 */


package com.biglybt.plugin;

import java.util.HashMap;
import java.util.Map;

import com.biglybt.core.Core;
import com.biglybt.core.CoreFactory;
import com.biglybt.core.internat.MessageText;
import com.biglybt.core.util.Debug;
import com.biglybt.core.util.SystemTime;
import com.biglybt.pif.PluginAdapter;
import com.biglybt.pif.PluginInterface;
import com.biglybt.pif.PluginManager;
import com.biglybt.ui.UIFunctions;
import com.biglybt.ui.UIFunctionsManager;
import com.biglybt.ui.UIFunctionsUserPrompter;

public class
I2PHelpers
{
	private static final Object i2p_check_lock		= new Object();
	private static final Object i2p_install_lock	= new Object();

	private static boolean i2p_installing = false;

	public static boolean
	isI2POperational()
	{
		PluginManager pm = CoreFactory.getSingleton().getPluginManager();
		
		PluginInterface pi = pm.getPluginInterfaceByID( "azneti2phelper" );
		
		if ( pi != null ){
			
			return( pi.getPluginState().isOperational());
			
		}else{
			
			return( false );
		}
	}
	
	public static boolean
	isI2PInstalled()
	{
		if ( isInstallingI2PHelper()){

			return( true );
		}

		PluginManager pm = CoreFactory.getSingleton().getPluginManager();

		return( pm.getPluginInterfaceByID( "azneti2phelper" ) != null );
	}

	public static boolean
	isInstallingI2PHelper()
	{
		synchronized( i2p_install_lock ){

			return( i2p_installing );
		}
	}

	public static boolean
	installI2PHelper(
		String				remember_id,
		final boolean[]		install_outcome,
		final Runnable		callback )
	{
		return installI2PHelper(null, remember_id, install_outcome, callback);
	}

	private static Map<String,Long>	declines = new HashMap<>();

	public static boolean
	installI2PHelper(
		String				extra_text,
		String				remember_id,
		final boolean[]		install_outcome,
		final Runnable		callback )
	{
		String decline_key = remember_id;

		if ( decline_key == null ){

			decline_key = extra_text;
		}

		if ( decline_key == null ){

			decline_key = "generic";
		}

		synchronized( i2p_install_lock ){

			Long decline = declines.get( decline_key );

			if ( decline != null && SystemTime.getMonotonousTime() - decline < 60*1000 ){

				return( false );
			}

			if ( i2p_installing ){

				Debug.out( "I2P Helper already installing" );

				return( false );
			}

			i2p_installing = true;
		}

		boolean	installing 	= false;

		boolean	declined	= false;

		try{
			UIFunctions uif = UIFunctionsManager.getUIFunctions();

			if ( uif == null ){

				Debug.out( "UIFunctions unavailable - can't install plugin" );

				return( false );
			}

			String title = MessageText.getString("azneti2phelper.install");

			String text = "";

			if ( extra_text != null ){

				text = extra_text + "\n\n";
			}

			text += MessageText.getString("azneti2phelper.install.text" );

			UIFunctionsUserPrompter prompter = uif.getUserPrompter(title, text, new String[] {
				MessageText.getString("Button.yes"),
				MessageText.getString("Button.no")
			}, 0);

			if ( remember_id != null ){

				prompter.setRemember(
					remember_id,
					false,
					MessageText.getString("MessageBoxWindow.nomoreprompting"));
			}

			prompter.setAutoCloseInMS(0);

			prompter.open(null);

			boolean	install = prompter.waitUntilClosed() == 0;

			if ( install ){

				installing = true;

				uif.installPlugin(
						"azneti2phelper",
						"azneti2phelper.install",
						new UIFunctions.actionListener()
						{
							@Override
							public void
							actionComplete(
								Object		result )
							{
								try{
									if ( callback != null ){

										if ( result instanceof Boolean ){

											install_outcome[0] = (Boolean)result;
										}

										callback.run();
									}
								}finally{

									synchronized( i2p_install_lock ){

										i2p_installing = false;
									}
								}
							}
						});

			}else{

				declined = true;

				Debug.out( "I2P Helper install declined (either user reply or auto-remembered)" );
			}

			return( install );

		}finally{


			synchronized( i2p_install_lock ){

				if ( !installing ){

					i2p_installing = false;
				}

				if ( declined ){

					declines.put( decline_key, SystemTime.getMonotonousTime());
				}
			}
		}
	}
	
	private static boolean i2p_checked = false;

	public static void
	checkI2PInstall(
		final String	host_name )
	{
		synchronized( i2p_check_lock ){

			if ( i2p_checked ){

				return;
			}

			try{
				Core core = CoreFactory.getSingleton();

				if ( core != null ){

					i2p_checked = true;

					PluginInterface pi = core.getPluginManager().getDefaultPluginInterface();

					pi.addListener(
						new PluginAdapter()
						{
							@Override
							public void
							initializationComplete()
							{
								if ( I2PHelpers.isI2PInstalled()){

									return;
								}

								final boolean[]	install_outcome = { false };

								String enable_i2p_reason =
									MessageText.getString( "azneti2phelper.install.reason.dns", new String[]{ host_name });

								I2PHelpers.installI2PHelper(
										enable_i2p_reason,
										"azneti2phelper.install.dns.resolve",
										install_outcome,
										new Runnable()
										{
											@Override
											public void
											run()
											{
												if ( !install_outcome[0] ){

												}
											}
										});
							}
						});
				}
			}catch( Throwable e ){

			}
		}
	}
}
