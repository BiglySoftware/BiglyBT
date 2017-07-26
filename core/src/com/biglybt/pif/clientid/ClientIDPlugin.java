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

package com.biglybt.pif.clientid;

import java.util.Properties;

import com.biglybt.core.Core;
import com.biglybt.core.config.COConfigurationManager;
import com.biglybt.core.config.ParameterListener;
import com.biglybt.core.download.DownloadManager;
import com.biglybt.core.global.GlobalManager;
import com.biglybt.core.peer.util.PeerUtils;
import com.biglybt.core.peermanager.messaging.bittorrent.BTHandshake;
import com.biglybt.core.util.Constants;
import com.biglybt.core.util.HashWrapper;
import com.biglybt.pifimpl.local.clientid.ClientIDManagerImpl;

/**
 * @author parg
 *
 */

public class
ClientIDPlugin
{
	private static final String CLIENT_NAME 	= Constants.BIGLY_PROTOCOL_NAME + " " + Constants.AZUREUS_VERSION;
	private static final String CLIENT_NAME_SM 	= Constants.BIGLY_PROTOCOL_NAME + " (Swarm Merging) " + Constants.AZUREUS_VERSION;

	private static boolean		send_os;

	public static void
	initialize(
		Core _core )
	{
		final Core core		= _core;

		final String	param = "Tracker Client Send OS and Java Version";

		COConfigurationManager.addAndFireParameterListener(param, new ParameterListener() {
			@Override
			public void parameterChanged(String param) {
				send_os = COConfigurationManager.getBooleanParameter(param);
			}
		});

		ClientIDManagerImpl.getSingleton().setGenerator(
			new ClientIDGenerator()
			{
				@Override
				public byte[]
				generatePeerID(
					byte[]		hash,
					boolean		for_tracker )
				{
					return( PeerUtils.createPeerID());
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

				@Override
				public Object
				getProperty(
					byte[]	hash,
					String	property_name )
				{
					if ( property_name == ClientIDGenerator.PR_CLIENT_NAME ){

						try{
							GlobalManager gm = core.getGlobalManager();

							DownloadManager dm = gm.getDownloadManager( new HashWrapper( hash ));

							if ( dm != null &&  gm.isSwarmMerging( dm ) != null ){

								return( CLIENT_NAME_SM );
							}
						}catch( Throwable e ){
						}

						return( CLIENT_NAME );

					}else if ( property_name == ClientIDGenerator.PR_MESSAGING_MODE ){

						return( BTHandshake.AZ_RESERVED_MODE );

					}else{

						return( null );
					}
				}
			},
			false );
	}

	protected static void
	doHTTPProperties(
		Properties			properties )
	{
		Boolean	raw = (Boolean)properties.get( ClientIDGenerator.PR_RAW_REQUEST );

		if ( raw != null && raw ){

			return;
		}

		String	version = Constants.AZUREUS_VERSION;

			// trim of any _Bnn or _CVS suffix as unfortunately some trackers can't cope with this
			// (well, apparently can't cope with B10)
			// its not a big deal anyway

		int	pos = version.indexOf('_');

		if ( pos != -1 ){

			version = version.substring(0,pos);
		}

		String	agent = Constants.AZUREUS_NAME + " " + version;

		if ( send_os ){

			agent += ";" + Constants.OSName;

			agent += ";Java " + Constants.JAVA_VERSION;
		}

		properties.put( ClientIDGenerator.PR_USER_AGENT, agent );
	}
}
