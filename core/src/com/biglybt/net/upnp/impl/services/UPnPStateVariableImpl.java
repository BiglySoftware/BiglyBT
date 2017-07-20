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

import com.biglybt.net.upnp.UPnPException;
import com.biglybt.net.upnp.UPnPService;
import com.biglybt.net.upnp.UPnPStateVariable;
import com.biglybt.net.upnp.impl.device.UPnPDeviceImpl;
import com.biglybt.pif.utils.xml.simpleparser.SimpleXMLParserDocument;
import com.biglybt.pif.utils.xml.simpleparser.SimpleXMLParserDocumentNode;

public class
UPnPStateVariableImpl
	implements UPnPStateVariable
{
	protected UPnPServiceImpl		service;
	protected String				name;

	protected
	UPnPStateVariableImpl(
		UPnPServiceImpl					_service,
		SimpleXMLParserDocumentNode		node )
	{
		service	= _service;

		name	= node.getChild( "name" ).getValue().trim();
	}

	@Override
	public String
	getName()
	{
		return( name );
	}

	@Override
	public UPnPService
	getService()
	{
		return( service );
	}

	@Override
	public String
	getValue()

		throws UPnPException
	{
		try{
			String	soap_action = "urn:schemas-upnp-org:control-1-0#QueryStateVariable";

			String	request =
				"<?xml version=\"1.0\" encoding=\"utf-8\"?>"+
				"<s:Envelope xmlns:s=\"http://schemas.xmlsoap.org/soap/envelope/\" s:encodingStyle=\"http://schemas.xmlsoap.org/soap/encoding/\">"+
					"<s:Body>";

			request += 	"<u:QueryStateVariable xmlns:u=\"urn:schemas-upnp-org:control-1-0\">" +
							"<u:varName>" + name + "</u:varName>" +
						"</u:QueryStateVariable>";

			request += 	"</s:Body>"+
						"</s:Envelope>";

			SimpleXMLParserDocument resp_doc	= ((UPnPDeviceImpl)service.getDevice()).getUPnP().performSOAPRequest( service, soap_action, request );

			SimpleXMLParserDocumentNode	body = resp_doc.getChild( "Body" );

			SimpleXMLParserDocumentNode fault = body.getChild( "Fault" );

			if ( fault != null ){

				throw( new UPnPException( "Invoke fails - fault reported: " + fault.getValue()));
			}

			SimpleXMLParserDocumentNode	resp_node = body.getChild( "QueryStateVariableResponse" );

			if ( resp_node == null ){

				throw( new UPnPException( "Invoke fails - response missing: " + body.getValue()));
			}

			SimpleXMLParserDocumentNode	value_node = resp_node.getChild( "return" );

			return( value_node.getValue());

		}catch( Throwable e ){

			if ( e instanceof UPnPException ){

				throw((UPnPException)e);
			}

			throw( new UPnPException( "Invoke fails", e ));
		}
	}
}