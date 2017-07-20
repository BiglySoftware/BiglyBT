/*
 * Created on 14-Jun-2004
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

package com.biglybt.net.upnp;

import com.biglybt.net.upnp.impl.services.UPnPActionImpl;
import com.biglybt.pif.utils.xml.simpleparser.SimpleXMLParserDocument;

/**
 * @author parg
 *
 */

public class
UPnPException
	extends Exception
{
	public String soap_action;
	public UPnPActionImpl action;
	public String fault;

	public int fault_code;
	public SimpleXMLParserDocument resp_doc;

	public
	UPnPException(
		String		str )
	{
		super( str );
	}
	public
	UPnPException(
		String		str,
		Throwable 	cause )
	{
		super( str, cause );
	}

	public
	UPnPException(
			String string,
			Throwable e,
			String soap_action,
			UPnPActionImpl action,
			SimpleXMLParserDocument resp_doc)
	{
			super(string, e);
  		this.soap_action = soap_action;
  		this.action = action;
  		this.resp_doc = resp_doc;
	}

	public UPnPException(
			String message,
			String soap_action,
			UPnPActionImpl action,
			SimpleXMLParserDocument resp_doc,
			String fault,
			int fault_code)
	{
		super(message);
		this.soap_action = soap_action;
		this.action = action;
		this.resp_doc = resp_doc;
		this.fault = fault;
		this.fault_code = fault_code;
	}
}
