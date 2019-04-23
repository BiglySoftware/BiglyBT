/*
 * Created on 29-Dec-2004
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

package com.biglybt.pifimpl.local.clientid;

import java.util.Properties;

import com.biglybt.core.Core;
import com.biglybt.core.config.COConfigurationManager;
import com.biglybt.core.download.DownloadManager;
import com.biglybt.core.global.GlobalManager;
import com.biglybt.core.peer.util.PeerUtils;
import com.biglybt.core.peermanager.messaging.bittorrent.BTHandshake;
import com.biglybt.core.util.Constants;
import com.biglybt.core.util.Debug;
import com.biglybt.core.util.HashWrapper;
import com.biglybt.pif.Plugin;
import com.biglybt.pif.PluginAdapter;
import com.biglybt.pif.PluginInterface;
import com.biglybt.pif.PluginManager;
import com.biglybt.pif.clientid.ClientIDGenerator;
import com.biglybt.pif.ui.config.*;
import com.biglybt.pif.ui.model.BasicPluginConfigModel;

/**
 * @author parg
 *
 */

public class
ClientIDPlugin
	implements Plugin, ClientIDGenerator
{
		// client names need to be of the form <name> <version>
		// or <name> <whatever> <version>
		// as PEPeerTransportProtocol depends on this
	
	private static final String BIGLY_NAME				= Constants.BIGLY_PROTOCOL_NAME;
	private static final String BIGLY_VERSION			= Constants.AZUREUS_VERSION;
	
	private static final String BIGLYBT_CLIENT_NAME 	= BIGLY_NAME + " " + BIGLY_VERSION;
	private static final String BIGLYBT_CLIENT_NAME_SM 	= BIGLY_NAME + " (Swarm Merging) " + BIGLY_VERSION;

	private static final String ML_BIGLY_VERSION			= getMainline( BIGLY_VERSION );
	private static final String ML_BIGLYBT_CLIENT_NAME 		= BIGLY_NAME + " " + ML_BIGLY_VERSION;
	private static final String ML_BIGLYBT_CLIENT_NAME_SM 	= BIGLY_NAME + " (Swarm Merging) " + ML_BIGLY_VERSION;
	
	private static final String VUZE_5750 = "Vuze 5.7.5.0";
	
	private static final String CONFIG_PREFIX 		= "Plugin.bgclientid.plugin.";
	private static final String CONFIG_SELECTION	= "bgclientid.selection";
	private static final String CONFIG_MAINLINE		= "bgclientid.mainline";
			
	private static final String[] CLIENTS = { BIGLYBT_CLIENT_NAME, VUZE_5750 };
	
	private static final String 	CLIENT_DEFAULT		= BIGLYBT_CLIENT_NAME;
	private static final boolean	MAINLINE_DEFAULT	= false;
	
	
	
	
	private static String 	client 				= CLIENT_DEFAULT;
	private static boolean	use_main_version	= MAINLINE_DEFAULT;

	
	private static boolean		send_os;

	private static String
	getMainline(
		String		str )
	{
		int	pos = str.indexOf( "_" );
		
		if ( pos != -1 ){
			
			str = str.substring( 0, pos );
			
			try {
				int num = Integer.parseInt( "" + str.charAt( str.length()-1));
				
				if (( num & 0x01) == 1 ) {
					
					str = str.substring( 0, str.length()-1 ) + ( num-1 );
				}
			}catch( Throwable e ){
				
				Debug.out( e );
			}
		}
		
		return( str );
	}
	
	private Core		core;
	
	public
	ClientIDPlugin()
	{
		
	}
	
	public Properties
	getInitialProperties()
	{
		Properties properties = new Properties();
		
		properties.setProperty( "plugin.version", 	"1.0" );
		properties.setProperty( "plugin.name", 		"Client Identification" );

		return( properties );
	}
	
	public void
	initialize(
		Core _core )
	{
		core	= _core;
		
		PluginManager.registerPlugin(
			this,
			"bgclientid",
			"bgclientid.plugin" );
		
		final String	param = "Tracker Client Send OS and Java Version";

		COConfigurationManager.addAndFireParameterListener(
			param, 
			new com.biglybt.core.config.ParameterListener() {
				@Override
				public void parameterChanged(String param) {
					send_os = COConfigurationManager.getBooleanParameter(param);
				}
			});

			// need to pick these up early!
		
		client 				= COConfigurationManager.getStringParameter( CONFIG_PREFIX + CONFIG_SELECTION, CLIENT_DEFAULT );
		use_main_version 	= COConfigurationManager.getBooleanParameter( CONFIG_PREFIX + CONFIG_MAINLINE, MAINLINE_DEFAULT );
		
		ClientIDManagerImpl.getSingleton().setGenerator( this, false );
	}

	@Override
	public byte[]
	generatePeerID(
		byte[]		hash,
		boolean		for_tracker )
	{
		byte[] id = PeerUtils.createPeerID();
		
		if ( client.equals( BIGLYBT_CLIENT_NAME )){
			
			if ( use_main_version ){
							
				id[6]	= (byte)ML_BIGLY_VERSION.charAt( ML_BIGLY_VERSION.length()-1 );
			}
		}else{

			byte[] b_prefix = "-AZ5750".getBytes(Constants.ISO_8859_1);

			for (int i = 0; i < b_prefix.length && i < id.length; i++) {
				id[i] = b_prefix[i];
			}
		}
		
		return( id );
	}

	@Override
	public Object
	getProperty(
		byte[]	hash,
		String	property_name )
	{
		if ( property_name == ClientIDGenerator.PR_CLIENT_NAME ){

			if ( client.equals( BIGLYBT_CLIENT_NAME )){
				
				try{
					GlobalManager gm = core.getGlobalManager();
	
					DownloadManager dm = gm.getDownloadManager( new HashWrapper( hash ));
	
					if ( dm != null &&  gm.isSwarmMerging( dm ) != null ){
	
						return( use_main_version?ML_BIGLYBT_CLIENT_NAME_SM:BIGLYBT_CLIENT_NAME_SM );
					}
				}catch( Throwable e ){
				}
	
				return( use_main_version?ML_BIGLYBT_CLIENT_NAME:BIGLYBT_CLIENT_NAME );
				
			}else{
				
				return( VUZE_5750 );
			}
		}else if ( property_name == ClientIDGenerator.PR_MESSAGING_MODE ){

			if ( client.equals( BIGLYBT_CLIENT_NAME )){
			
				return( BTHandshake.AZ_RESERVED_MODE );
				
			}else{
				
				return( BTHandshake.AZ_RESERVED_MODE );
			}
		}else{

			return( null );
		}
	}
	
	protected void
	doHTTPProperties(
		Properties			properties )
	{
		Boolean	raw = (Boolean)properties.get( ClientIDGenerator.PR_RAW_REQUEST );

		if ( raw != null && raw ){

			return;
		}
		
		if ( client.equals( BIGLYBT_CLIENT_NAME )){

			String	version = use_main_version?ML_BIGLY_VERSION:BIGLY_VERSION;
	
				// trim of any _Bnn or _CVS suffix as unfortunately some trackers can't cope with this
				// (well, apparently can't cope with B10)
				// its not a big deal anyway
	
			int	pos = version.indexOf('_');
	
			if ( pos != -1 ){
	
				version = version.substring(0,pos);
			}
	
			String	agent = BIGLY_NAME + " " + version;
	
			if ( send_os ){
	
				agent += ";" + Constants.OSName;
	
				agent += ";Java " + Constants.JAVA_VERSION;
			}
	
			properties.put( ClientIDGenerator.PR_USER_AGENT, agent );
			
		}else{
				
			String	agent = VUZE_5750;
	
			if ( send_os ){
	
				agent += ";" + Constants.OSName;
	
				agent += ";Java " + Constants.JAVA_VERSION;
			}
	
			properties.put( ClientIDGenerator.PR_USER_AGENT, agent );
		}
	}
	
	
	@Override
	public void
	generateHTTPProperties(
		byte[]		hash,
		Properties	properties )
	{
		doHTTPProperties( properties );
	}

	@Override
	public String[]
	filterHTTP(
		byte[]		hash,
		String[]	lines_in )
	{
		return( lines_in );
	}

	
	public void
	initialize(
		PluginInterface 	pi )
	{
		pi.addListener(
			new PluginAdapter(){
				
				@Override
				public void initializationComplete(){
					
					initializeSupport( pi );
				}
			});
	}
	
	private void
	initializeSupport(
		PluginInterface		pi )
	{
		ClientIDGenerator gen = ClientIDManagerImpl.getSingleton().getGenerator();
					
		if ( gen != this ) {
			
			return;
		}

		BasicPluginConfigModel	config_model =
				pi.getUIManager().createBasicPluginConfigModel( ConfigSection.SECTION_PLUGINS,
						"bgclientid.name" );

		config_model.addLabelParameter2( "bgclientid.info" );
		
		final StringListParameter client_param = config_model.addStringListParameter2( CONFIG_SELECTION, CONFIG_SELECTION, CLIENTS,	CLIENT_DEFAULT );
		
		
		client = client_param.getValue();
			
		final BooleanParameter mainline = config_model.addBooleanParameter2( CONFIG_MAINLINE, CONFIG_MAINLINE,  MAINLINE_DEFAULT);

		client_param.addListener(
				new ParameterListener(){
					
					public void 
					parameterChanged(
						Parameter param )
					{
						client = client_param.getValue();
						
						mainline.setEnabled( client.equals( BIGLYBT_CLIENT_NAME ));
					}
				});
		
		mainline.setEnabled( client.equals( BIGLYBT_CLIENT_NAME ));

		mainline.addListener(
				new ParameterListener() {
					
					public void 
					parameterChanged(
						Parameter param )
					{
						use_main_version = mainline.getValue();
					}
				});
				
		use_main_version = mainline.getValue();
	}  
}
