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

package com.biglybt.net.upnp.impl.ssdp;

import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.URL;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;

import com.biglybt.core.util.*;
import com.biglybt.net.upnp.UPnPException;
import com.biglybt.net.upnp.UPnPSSDP;
import com.biglybt.net.upnp.UPnPSSDPListener;
import com.biglybt.net.upnp.impl.SSDPIGD;
import com.biglybt.net.upnp.impl.SSDPIGDListener;
import com.biglybt.net.upnp.impl.UPnPImpl;

/**
 * @author parg
 *
 */

public class
SSDPIGDImpl
	implements SSDPIGD, UPnPSSDPListener
{
	private UPnPImpl upnp;
	private SSDPCore		ssdp_core;

	private boolean			first_result			= true;
	private long			last_explicit_search	= 0;

	private List			listeners	= new ArrayList();

	protected AEMonitor		this_mon	= new AEMonitor( "SSDP" );


	public
	SSDPIGDImpl(
		UPnPImpl		_upnp,
		String[]		_selected_interfaces )

		throws UPnPException
	{
		upnp	= _upnp;

		ssdp_core	=
			SSDPCore.getSingleton(
				upnp.getAdapter(),
				UPnPSSDP.SSDP_GROUP_ADDRESS,
				UPnPSSDP.SSDP_GROUP_PORT,
				0,
				_selected_interfaces );

		ssdp_core.addListener( this );
	}

	@Override
	public SSDPCore
	getSSDP()
	{
		return( ssdp_core );
	}

	@Override
	public void
	start()

		throws UPnPException
	{
		try{
			upnp.getAdapter().createThread(
					"SSDP:queryLoop",
					new AERunnable()
					{
						@Override
						public void
						runSupport()
						{
							queryLoop();
						}
					});

		}catch( Throwable e ){

			Debug.printStackTrace( e );

			throw( new UPnPException( "Failed to initialise SSDP", e ));
		}
	}



	@Override
	public void
	searchNow()
	{
		long	now = SystemTime.getCurrentTime();

		if ( now - last_explicit_search < 10000 ){

			return;
		}

		last_explicit_search	= now;

		search();
	}

	@Override
	public void
	searchNow(
		String[]	STs )
	{
		ssdp_core.search( STs );
	}

	protected void
	queryLoop()
	{
		while(true){

			try{
				search();

				Thread.sleep( 60000 );

			}catch( Throwable e ){

				Debug.printStackTrace( e );
			}

		}
	}

	protected void
	search()
	{
		ssdp_core.search( new String[]{ "upnp:rootdevice" });
	}


	@Override
	public void
	receivedResult(
		NetworkInterface	network_interface,
		InetAddress			local_address,
		InetAddress			originator,
		String				usn,
		URL					location,
		String				st,
		String				al )
	{
		try{
			this_mon.enter();

			if ( st.equalsIgnoreCase( "upnp:rootdevice" )){

				gotRoot( network_interface, local_address, usn, location );
			}
		}finally{

			first_result	= false;

			this_mon.exit();
		}
	}

	@Override
	public void
	receivedNotify(
		NetworkInterface	network_interface,
		InetAddress			local_address,
		InetAddress			originator,
		String				usn,
		URL					location,
		String				nt,
		String				nts )
	{
		try{
			this_mon.enter();

			if (nt.contains("upnp:rootdevice")){

				if (nts.contains("alive")){

						// alive can be reported on any interface

					try{

						InetAddress	dev = InetAddress.getByName( location.getHost());

						byte[]	dev_bytes = dev.getAddress();

						boolean[]	dev_bits = bytesToBits( dev_bytes );

							// try and work out what bind address this location corresponds to

						NetworkInterface	best_ni 	= null;
						InetAddress			best_addr	= null;

						int	best_prefix	= 0;

						List<NetworkInterface>	x = NetUtils.getNetworkInterfaces();

						for ( final NetworkInterface this_ni: x ){

							Enumeration<InetAddress> ni_addresses = this_ni.getInetAddresses();

							while (ni_addresses.hasMoreElements()){

								InetAddress this_address = ni_addresses.nextElement();

								byte[]	this_bytes = this_address.getAddress();

								if ( dev_bytes.length == this_bytes.length ){

									boolean[]	this_bits = bytesToBits( this_bytes );

									for (int i=0;i<this_bits.length;i++){

										if ( dev_bits[i] != this_bits[i] ){

											break;
										}

										if ( i > best_prefix ){

											best_prefix	= i;

											best_ni		= this_ni;
											best_addr	= this_address;
										}
									}
								}
							}
						}

						if ( best_ni != null ){

							if ( first_result ){

								upnp.log( location + " -> " + best_ni.getDisplayName() + "/" + best_addr + " (prefix=" + (best_prefix + 1 ) + ")");
							}

							gotRoot( best_ni, best_addr, usn, location );

						}else{

							gotAlive( usn, location );
						}
					}catch( Throwable e ){

						gotAlive( usn, location );
					}
				}else if (nts.contains("byebye")){

					lostRoot( local_address, usn );
				}
			}
		}finally{

			first_result	= false;

			this_mon.exit();
		}
	}

	@Override
	public String[]
	receivedSearch(
		NetworkInterface	network_interface,
		InetAddress			local_address,
		InetAddress			originator,
		String				ST )
	{
		// not interested, loopback or other search

		return( null );
	}


	protected boolean[]
	bytesToBits(
		byte[]	bytes )
	{
		boolean[]	res = new boolean[bytes.length*8];

		for (int i=0;i<bytes.length;i++){

			byte	b = bytes[i];

			for (int j=0;j<8;j++){

				res[i*8+j] = (b&(byte)(0x01<<(7-j))) != 0;
			}
		}

		return( res );
	}

	protected void
	gotRoot(
		NetworkInterface	network_interface,
		InetAddress			local_address,
		String				usn,
		URL					location )
	{
		for (int i=0;i<listeners.size();i++){

			try{
				((SSDPIGDListener)listeners.get(i)).rootDiscovered( network_interface, local_address, usn, location );

			}catch( Throwable e ){

				Debug.printStackTrace(e);
			}
		}
	}

	protected void
	gotAlive(
		String	usn,
		URL		location )
	{
		for (int i=0;i<listeners.size();i++){

			try{
				((SSDPIGDListener)listeners.get(i)).rootAlive( usn, location );

			}catch( Throwable e ){

				Debug.printStackTrace(e);
			}
		}
	}

	protected void
	lostRoot(
		InetAddress	local_address,
		String		usn )
	{
		for (int i=0;i<listeners.size();i++){

			try{
				((SSDPIGDListener)listeners.get(i)).rootLost( local_address, usn );

			}catch( Throwable e ){

				Debug.printStackTrace(e);
			}
		}
	}

	@Override
	public void
	interfaceChanged(
		NetworkInterface	network_interface )
	{
		for (int i=0;i<listeners.size();i++){

			try{
				((SSDPIGDListener)listeners.get(i)).interfaceChanged( network_interface );

			}catch( Throwable e ){

				Debug.printStackTrace(e);
			}
		}
	}

	@Override
	public void
	addListener(
		SSDPIGDListener	l )
	{
		listeners.add( l );
	}

	@Override
	public void
	removeListener(
		SSDPIGDListener	l )
	{
		listeners.remove(l);
	}
}
