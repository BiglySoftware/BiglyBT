/*
 * Created on Jan 30, 2008
 * Created by Paul Gardner
 *
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
 */


package com.biglybt.plugin.net.netstatus;


import com.biglybt.core.util.AESemaphore;
import com.biglybt.core.util.AEThread2;
import com.biglybt.core.util.Debug;
import com.biglybt.pif.Plugin;
import com.biglybt.pif.PluginInterface;
import com.biglybt.pif.PluginListener;
import com.biglybt.pif.logging.LoggerChannel;
import com.biglybt.pif.ui.UIInstance;
import com.biglybt.pif.ui.UIManagerListener;
import com.biglybt.pif.ui.config.ActionParameter;
import com.biglybt.pif.ui.config.BooleanParameter;
import com.biglybt.pif.ui.config.StringParameter;
import com.biglybt.pif.ui.model.BasicPluginConfigModel;

public class
NetStatusPlugin
	implements Plugin
{
	public static final String VIEW_ID = "aznetstatus";

	private PluginInterface plugin_interface;

	private LoggerChannel	logger;

	// private StringParameter ping_target;
	private BooleanParameter	logging_detailed;

	private ActionParameter test_button;
	private StringParameter test_address;

	private NetStatusProtocolTester		protocol_tester;
	private AESemaphore					protocol_tester_sem	= new AESemaphore( "ProtTestSem" );

	public static void
	load(
		PluginInterface		plugin_interface )
	{
		String name =
			plugin_interface.getUtilities().getLocaleUtilities().getLocalisedMessageText( "Views.plugins." + VIEW_ID + ".title" );

		plugin_interface.getPluginProperties().setProperty( "plugin.version", 	"1.0" );
		plugin_interface.getPluginProperties().setProperty( "plugin.name", 		name );

	}

	@Override
	public void
	initialize(
		final PluginInterface		_plugin_interface )
	{
		plugin_interface	= _plugin_interface;

		logger = plugin_interface.getLogger().getChannel( "NetStatus" );

		logger.setDiagnostic();

		BasicPluginConfigModel config = plugin_interface.getUIManager().createBasicPluginConfigModel( "Views.plugins." + VIEW_ID + ".title" );

		logging_detailed = config.addBooleanParameter2( "plugin.aznetstatus.logfull", "plugin.aznetstatus.logfull", false );

		plugin_interface.getUIManager().addUIListener(
			new UIManagerListener()
			{
				@Override
				public void
				UIAttached(
					UIInstance		instance )
				{
					if ( instance.getUIType().equals(UIInstance.UIT_SWT) ){

						try{
								Class.forName(
										"com.biglybt.plugin.net.netstatus.swt.NetStatusPluginView").getMethod(
												"initSWTUI", UIInstance.class).invoke(null, instance);

						}catch( Throwable e ){

							e.printStackTrace();
						}
					}
				}

				@Override
				public void
				UIDetached(
					UIInstance		instance )
				{
				}
			});

		plugin_interface.addListener(
			new PluginListener()
			{
				@Override
				public void
				initializationComplete()
				{
					new AEThread2( "NetstatusPlugin:init", true )
					{
						@Override
						public void
						run()
						{
							try{
								protocol_tester = new NetStatusProtocolTester( NetStatusPlugin.this, plugin_interface );

								if ( test_button != null ){

									test_button.setEnabled( true );
								}
							}finally{

								protocol_tester_sem.releaseForever();
							}
						}
					}.start();
				}

				@Override
				public void
				closedownInitiated()
				{
				}

				@Override
				public void
				closedownComplete()
				{
				}
			});
	}

	public boolean
	isDetailedLogging()
	{
		return( logging_detailed.getValue());
	}

	public NetStatusProtocolTester
	getProtocolTester()
	{
		protocol_tester_sem.reserve();

		return( protocol_tester );
	}

	/*
	public String
	getPingTarget()
	{
		return( ping_target.getValue());
	}
	*/

	public void
	setBooleanParameter(
		String	name,
		boolean	value )
	{
		plugin_interface.getPluginconfig().setPluginParameter( name , value );
	}

	public boolean
	getBooleanParameter(
		String	name,
		boolean	def )
	{
		return( plugin_interface.getPluginconfig().getPluginBooleanParameter( name, def ));
	}

	public void
	log(
		String		str )
	{
		logger.log( str );
	}

	public void
	log(
		String		str,
		Throwable	e )
	{
		logger.log( str + ": " + Debug.getNestedExceptionMessage( e ));
	}
}
