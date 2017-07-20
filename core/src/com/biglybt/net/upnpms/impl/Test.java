/*
 * Created on Dec 19, 2012
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


package com.biglybt.net.upnpms.impl;

import java.net.URL;
import java.util.Arrays;
import java.util.List;

import com.biglybt.net.upnpms.UPNPMSBrowser;
import com.biglybt.net.upnpms.UPNPMSBrowserListener;
import com.biglybt.net.upnpms.UPNPMSContainer;
import com.biglybt.net.upnpms.UPNPMSNode;

public class
Test
{
	private static void
	dump(
		UPNPMSContainer container,
		String				indent )

		throws Exception
	{
		System.out.println( indent + container.getTitle() + " - " + container.getID());

		indent += "    ";

		List<UPNPMSNode>	kids = container.getChildren();

		for ( UPNPMSNode kid: kids ){

			if ( kid instanceof UPNPMSContainer ){

				dump((UPNPMSContainer)kid, indent );
			}else{

				System.out.println( indent + kid.getTitle() + " - " + kid.getID());

			}
		}
	}

	public static void
	main(
		String[]	args )
	{
		try{
			UPNPMSBrowser browser =
				new UPNPMSBrowserImpl(
						"Vuze",
						Arrays.asList( new URL[]{ new URL( "http://192.168.1.5:2869/upnphost/udhisapi.dll?control=uuid:82aaab53-afaf-4d8f-bdd8-c1e438e7a348+urn:upnp-org:serviceId:ContentDirectory" )}),
						new UPNPMSBrowserListener()
						{
							@Override
							public void
							setPreferredURL(URL url)
							{
							}
						});

			UPNPMSContainer root = browser.getRoot();

			dump( root, "" );

		}catch( Throwable e ){

			e.printStackTrace();
		}
	}
}
