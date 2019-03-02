/*
 * Created on 15-Jun-2004
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

package com.biglybt.net.upnp.impl.services;

/**
 * @author parg
 *
 */

import java.net.InetSocketAddress;
import java.net.MalformedURLException;
import java.net.Socket;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

import com.biglybt.core.util.GeneralUtils;
import com.biglybt.core.util.UrlUtils;
import com.biglybt.net.upnp.*;
import com.biglybt.net.upnp.impl.device.UPnPDeviceImpl;
import com.biglybt.net.upnp.services.UPnPSpecificService;
import com.biglybt.pif.utils.xml.simpleparser.SimpleXMLParserDocument;
import com.biglybt.pif.utils.xml.simpleparser.SimpleXMLParserDocumentNode;
import com.biglybt.util.StringCompareUtils;

public class
UPnPServiceImpl
	implements UPnPService
{
	private final UPnPDeviceImpl	device;

	private String			service_type;
	private String			local_desc_url;
	private String			local_control_url;

	private List			actions;
	private List			state_vars;

	private boolean			direct_invoke;

	private URL				preferred_control_url;

	public
	UPnPServiceImpl(
		UPnPDeviceImpl					_device,
		String							indent,
		SimpleXMLParserDocumentNode		service_node )
	{
		device		= _device;

		service_type 		= service_node.getChild("ServiceType").getValue().trim();

		local_desc_url		= service_node.getChild("SCPDURL").getValue();

		local_control_url	= service_node.getChild("controlURL").getValue();

		device.getUPnP().log( indent + service_type + ":desc=" + device.getAbsoluteURL(local_desc_url) + ", control=" + device.getAbsoluteURL(local_control_url));
	}

	@Override
	public UPnPDevice
	getDevice()
	{
		return( device );
	}

	@Override
	public String
	getServiceType()
	{
		return( service_type );
	}

	@Override
	public boolean
	isConnectable()
	{
		try{
			List<URL> urls = getControlURLs();

			for ( URL url: urls ){

				Socket socket = new Socket();

				try{
					int	port = url.getPort();

					if ( port <= 0 ){

						port = url.getDefaultPort();
					}

					socket.connect( new InetSocketAddress( url.getHost(), port ), 5000 );

					if ( getPreferredControlURL() == null ){

						setPreferredControlURL( url );
					}

					return( true );

				}finally{

					try{
						socket.close();

					}catch( Throwable e ){

					}
				}
			}
		}catch( Throwable e ){
		}

		return( false );
	}

	@Override
	public UPnPAction[]
	getActions()

		throws UPnPException
	{
		if ( actions == null ){

			loadDescription();
		}

		UPnPAction[]	res = new UPnPAction[actions.size()];

		actions.toArray( res );

		return( res );
	}

	@Override
	public UPnPAction
	getAction(
		String	name )

		throws UPnPException
	{
		UPnPAction[]	my_actions = getActions();

		for (int i=0;i<my_actions.length;i++){

			if ( my_actions[i].getName().equalsIgnoreCase( name )){

				return( my_actions[i] );
			}
		}

		return( null );
	}

	@Override
	public UPnPStateVariable[]
	getStateVariables()

		throws UPnPException
	{
		if ( state_vars == null ){

			loadDescription();
		}

		UPnPStateVariable[]	res = new UPnPStateVariable[state_vars.size()];

		state_vars.toArray( res );

		return( res );
	}

	@Override
	public UPnPStateVariable
	getStateVariable(
		String	name )

		throws UPnPException
	{
		UPnPStateVariable[]	vars = getStateVariables();

		for (int i=0;i<vars.length;i++){

			if ( vars[i].getName().equalsIgnoreCase( name )){

				return( vars[i] );
			}
		}

		return( null );
	}

	public URL
	getDescriptionURL()

		throws UPnPException
	{
		return( getURL( device.getAbsoluteURL( local_desc_url )));
	}

	@Override
	public List<URL>
	getControlURLs()

		throws UPnPException
	{
		List<URL>	result = new ArrayList<>();

		String control_url = device.getAbsoluteURL( local_control_url );

		URL main_url =  getURL( control_url );

		result.add( main_url );

		List<URL>	alt_locs = device.getRootDevice().getAlternativeLocations();

		if ( alt_locs.size() > 0 ){

			for ( URL alt_loc: alt_locs ){

				URL alt_url = main_url;

				alt_url = UrlUtils.setHost( alt_url, alt_loc.getHost());
				alt_url = UrlUtils.setPort( alt_url, alt_loc.getPort());

				result.add( alt_url );
			}
		}

		if ( result.size() > 1 && preferred_control_url != null  ){

			if ( 	!result.get(0).equals( preferred_control_url ) &&
					result.contains( preferred_control_url )){

				result.remove( preferred_control_url );
				result.add( 0, preferred_control_url );
			}
		}

		return( result );
	}

	@Override
	public void
	setPreferredControlURL(
		URL url)
	{
		preferred_control_url	= url;
	}

	protected URL
	getPreferredControlURL()
	{
		return( preferred_control_url );
	}

	protected URL
	getURL(
		String	basis )

		throws UPnPException
	{
		try{
			URL	target;

			String	lc_basis = basis.toLowerCase();

			if ( lc_basis.startsWith( "http" ) || lc_basis.startsWith( "https" )){

					// absolute

				target = new URL( basis );

			}else{

					// relative

				URL	root_location = device.getRootDevice().getLocation();

				target = new URL( root_location.getProtocol() + "://" +
									root_location.getHost() +
									(root_location.getPort() == -1?"":":" + root_location.getPort()) +
									(basis.startsWith( "/" )?"":"/") + basis );

			}

			return( target );

		}catch( MalformedURLException e ){

			throw( new UPnPException( "Malformed URL", e ));
		}
	}

	protected void
	loadDescription()

		throws UPnPException
	{
		SimpleXMLParserDocument	doc = device.getUPnP().downloadXML( device, getDescriptionURL());

		parseActions( doc.getChild( "ActionList" ));

		parseStateVars( doc.getChild( "ServiceStateTable"));
	}

	protected void
	parseActions(
		SimpleXMLParserDocumentNode	action_list )
	{
		actions	= new ArrayList();

		SimpleXMLParserDocumentNode[]	kids = action_list.getChildren();

		for (int i=0;i<kids.length;i++){

			actions.add( new UPnPActionImpl( this, kids[i] ));
		}
	}

	protected void
	parseStateVars(
		SimpleXMLParserDocumentNode	action_list )
	{
		state_vars	= new ArrayList();

		SimpleXMLParserDocumentNode[]	kids = action_list.getChildren();

		for (int i=0;i<kids.length;i++){

			state_vars.add( new UPnPStateVariableImpl( this, kids[i] ));
		}
	}

	@Override
	public UPnPSpecificService
	getSpecificService()
	{
		if ( GeneralUtils.startsWithIgnoreCase( service_type, "urn:schemas-upnp-org:service:WANIPConnection:")){

			return( new UPnPSSWANIPConnectionImpl( this ));

		}else if ( GeneralUtils.startsWithIgnoreCase( service_type, "urn:schemas-upnp-org:service:WANPPPConnection:")){

			return( new UPnPSSWANPPPConnectionImpl( this ));

		}else if ( GeneralUtils.startsWithIgnoreCase( service_type, "urn:schemas-upnp-org:service:WANCommonInterfaceConfig:")){

			return( new UPnPSSWANCommonInterfaceConfigImpl( this ));

		}else if ( GeneralUtils.startsWithIgnoreCase( service_type, "urn:schemas-upnp-org:service:VuzeOfflineDownloaderService:")){

			return( new UPnPSSOfflineDownloaderImpl( this ));

		}else{

			return( null );
		}
	}

	@Override
	public boolean
	getDirectInvocations()
	{
		return( direct_invoke );
	}

	@Override
	public void
	setDirectInvocations(
		boolean	force )
	{
		direct_invoke	= force;
	}
}
