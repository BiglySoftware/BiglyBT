/*
 * Created on May 29, 2014
 * Created by Paul Gardner
 *
 * Copyright 2014 Azureus Software, Inc.  All rights reserved.
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


package com.biglybt.core.dht.transport.udp.impl;

import java.util.*;

import com.biglybt.core.dht.transport.DHTTransportAlternativeContact;
import com.biglybt.core.dht.transport.DHTTransportAlternativeNetwork;

public class
DHTTransportAlternativeNetworkImpl
	implements DHTTransportAlternativeNetwork
{
	private static final int	LIVE_AGE_SECS 		= 20*60;
	private static final int	LIVEISH_AGE_SECS 	= 40*60;
	private static final int	MAX_CONTACTS_PUB	= 64;
	private static final int	MAX_CONTACTS_ANON	= 16;

	private static final boolean	TRACE = false;

	private final int	network;
	private final int	max_contacts;

	private final TreeSet<DHTTransportAlternativeContact> contacts =
			new TreeSet<>(
					new Comparator<DHTTransportAlternativeContact>() {
						@Override
						public int
						compare(
								DHTTransportAlternativeContact o1,
								DHTTransportAlternativeContact o2) 
						{
							int res = o2.getLastAlive() - o1.getLastAlive();

							if (res == 0) {

								res = o1.getID() - o2.getID();
							}

							return (res);
						}
					});


	protected
	DHTTransportAlternativeNetworkImpl(
		int		_net )
	{
		network	= _net;

		max_contacts = ( network == AT_I2P || network == AT_TOR )?MAX_CONTACTS_ANON:MAX_CONTACTS_PUB;
	}

	@Override
	public int
	getNetworkType()
	{
		return( network );
	}

	@Override
	public List<DHTTransportAlternativeContact>
	getContacts(
		int			max )
	{
		return( getContacts( max, false ));
	}

	protected List<DHTTransportAlternativeContact>
	getContacts(
		int			max,
		boolean		live_only )
	{
		if ( max == 0 ){

			max = max_contacts;
		}

		List<DHTTransportAlternativeContact> result = new ArrayList<>(max);

		Set<Integer>	used_ids = new HashSet<>();

		synchronized( contacts ){

			Iterator<DHTTransportAlternativeContact> it = contacts.iterator();

			while( it.hasNext()){

				DHTTransportAlternativeContact contact = it.next();

				if ( live_only && contact.getAge() > LIVEISH_AGE_SECS ){

					break;
				}

				Integer id = contact.getID();

				if ( used_ids.contains( id )){

					continue;
				}

				used_ids.add( id );

				result.add( contact );

				if ( result.size() == max ){

					break;
				}

			}
		}

		if ( TRACE ){
			System.out.println( network + ": sending " + result.size() + " contacts" );
		}

		return( result );
	}

	private void
	trim()
	{
		Iterator<DHTTransportAlternativeContact> it = contacts.iterator();

		int	pos = 0;

		while( it.hasNext()){

			it.next();

			pos++;

			if(  pos > max_contacts ){

				it.remove();
			}
		}
	}

	protected void
	addContactsForSend(
		List<DHTTransportAlternativeContact>	new_contacts )
	{
		synchronized( contacts ){

			for ( DHTTransportAlternativeContact new_contact: new_contacts ){

				if ( TRACE ) System.out.println( network + ": send: add contact: " + getString(new_contact));

				contacts.add( new_contact );
			}

			if ( contacts.size() > max_contacts ){

				trim();
			}

			if ( TRACE ) System.out.println( network + ":     contacts=" + contacts.size());
		}
	}

	protected void
	addContactFromReply(
		DHTTransportAlternativeContact		new_contact )
	{
		synchronized( contacts ){

			if ( TRACE ) System.out.println( network + ": recv: add contact: " +  getString(new_contact));

			contacts.add( new_contact );

			if ( contacts.size() > max_contacts ){

				trim();
			}

			if ( TRACE ) System.out.println( network + ":     contacts=" + contacts.size());
		}
	}

	protected int
	getRequiredContactCount()
	{
		synchronized( contacts ){

			int	num_contacts = contacts.size();

			int	result = 0;

			if ( num_contacts < max_contacts ){

				result =  max_contacts - num_contacts;

			}else{

				Iterator<DHTTransportAlternativeContact> it = contacts.iterator();

				int	pos = 0;

				while( it.hasNext()){

					DHTTransportAlternativeContact contact = it.next();

					if ( contact.getAge() > LIVE_AGE_SECS ){

						result = max_contacts - pos;

						break;

					}else{

						pos++;
					}
				}
			}

			if ( TRACE ) System.out.println( network + ": required=" + result );

			return( result );
		}
	}

	private String
	getString(
		DHTTransportAlternativeContact		contact )
	{
		return( contact.getProperties() + ", age=" + contact.getAge());
	}
}
