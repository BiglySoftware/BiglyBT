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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.biglybt.net.upnp.UPnPActionArgument;
import com.biglybt.net.upnp.UPnPActionInvocation;
import com.biglybt.net.upnp.UPnPException;
import com.biglybt.net.upnp.UPnPService;
import com.biglybt.net.upnp.impl.device.UPnPDeviceImpl;
import com.biglybt.pif.utils.xml.simpleparser.SimpleXMLParserDocument;
import com.biglybt.pif.utils.xml.simpleparser.SimpleXMLParserDocumentNode;

public class
UPnPActionInvocationImpl
	implements UPnPActionInvocation
{
	protected UPnPActionImpl		action;

	protected List	arg_names	= new ArrayList();
	protected List	arg_values	= new ArrayList();

	protected
	UPnPActionInvocationImpl(
		UPnPActionImpl		_action )
	{
		action		= _action;
	}

	@Override
	public void
	addArgument(
		String	name,
		String	value )
	{
		arg_names.add( name );

		arg_values.add( value );
	}

	@Override
	public UPnPActionArgument[]
	invoke()

		throws UPnPException
	{
		UPnPService service = action.getService();

		String	soap_action = service.getServiceType() + "#" + action.getName();
		SimpleXMLParserDocument resp_doc = null;

		try{
			String	request =
				"<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"+
				"<s:Envelope xmlns:s=\"http://schemas.xmlsoap.org/soap/envelope/\" s:encodingStyle=\"http://schemas.xmlsoap.org/soap/encoding/\">\n"+
					"  <s:Body>\n";

			request += "    <u:" + action.getName() +
							" xmlns:u=\"" + service.getServiceType()+ "\">\n";


			for (int i=0;i<arg_names.size();i++){

				String	name 	= (String)arg_names.get(i);
				String	value 	= (String)arg_values.get(i);

				request += "      <" + name + ">" + value + "</" + name + ">\n";
			}

			request += "    </u:" + action.getName() + ">\n";

			request += "  </s:Body>\n"+
						"</s:Envelope>";

				// try standard POST

			resp_doc	= ((UPnPDeviceImpl)action.getService().getDevice()).getUPnP().performSOAPRequest( service, soap_action, request );

			SimpleXMLParserDocumentNode	body = resp_doc.getChild( "Body" );

			SimpleXMLParserDocumentNode faultSection = body.getChild( "Fault" );

			if ( faultSection != null ){

				String faultValue = faultSection.getValue();
				if (faultValue != null && faultValue.length() > 0) {
  				throw (new UPnPException("Invoke of '" + soap_action
  						+ "' failed - fault reported: " + faultValue, soap_action,
  						action, resp_doc, faultValue, -1));
				}

				SimpleXMLParserDocumentNode faultDetail = faultSection.getChild("detail");
				if (faultDetail != null) {
					SimpleXMLParserDocumentNode error = faultDetail.getChild("UPnPError");
					if (error != null) {
						int errCodeNumber = -1;
						String errDescValue = null;

						SimpleXMLParserDocumentNode errCode = error.getChild("errorCode");
						if (errCode != null) {
							String errCodeValue = errCode.getValue();
							try {
								errCodeNumber = Integer.parseInt(errCodeValue);
							} catch (Throwable t) {
							}
						}

						SimpleXMLParserDocumentNode errDesc = error.getChild("errorDescription");
						if (errDesc != null) {
							errDescValue = errDesc.getValue();
							if (errDescValue != null && errDescValue.length() == 0) {
								errDescValue = null;
							}
						}

	  				throw (new UPnPException("Invoke of '" + soap_action
	  						+ "' failed - fault reported: " + errDescValue, soap_action,
	  						action, resp_doc, errDescValue, errCodeNumber));
					}
				}
			}

			SimpleXMLParserDocumentNode	resp_node = body.getChild( action.getName() + "Response" );

			if ( resp_node == null ){

				throw (new UPnPException("Invoke of '" + soap_action
						+ "' failed - response missing: " + body.getValue(), soap_action,
						action, resp_doc, null, -1));
			}

			SimpleXMLParserDocumentNode[]	out_nodes = resp_node.getChildren();

			UPnPActionArgument[]	resp = new UPnPActionArgument[out_nodes.length];

			for (int i=0;i<out_nodes.length;i++){

				resp[i] = new UPnPActionArgumentImpl( out_nodes[i].getName(), out_nodes[i].getValue());
			}

			return( resp );

		}catch( Throwable e ){

			if ( e instanceof UPnPException ){

				throw((UPnPException)e);
			}

			throw new UPnPException("Invoke of '" + soap_action + "' on '"
					+ action.getService().getControlURLs() + "' failed: "
					+ e.getMessage(), e, soap_action, action, resp_doc);
		}
	}

	@Override
	public Map
	invoke2()

  		throws UPnPException
	{
		UPnPActionArgument[]	res = invoke();

		Map	map = new HashMap();

		for (int i=0;i<res.length;i++){

			map.put( res[i].getName(), res[i].getValue());
		}

		return( map );
	}
}
