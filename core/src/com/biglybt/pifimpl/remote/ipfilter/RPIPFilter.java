/*
 * File    : RPIPFilter.java
 * Created : 15-Apr-2004
 * By      : parg
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details ( see the LICENSE file ).
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 */

package com.biglybt.pifimpl.remote.ipfilter;


/**
 * @author parg
 *
 */

import java.io.File;

import com.biglybt.pif.ipfilter.*;
import com.biglybt.pifimpl.remote.RPException;
import com.biglybt.pifimpl.remote.RPObject;
import com.biglybt.pifimpl.remote.RPReply;
import com.biglybt.pifimpl.remote.RPRequest;


public class
RPIPFilter
	extends		RPObject
	implements 	IPFilter
{
	protected transient IPFilter		delegate;

		// don't change these field names as they are visible on XML serialisation

	public long				last_update_time;
	public int				number_of_ranges;
	public int				number_of_blocked_ips;

	public static IPFilter
	create(
		IPFilter		_delegate )
	{
		RPIPFilter	res =(RPIPFilter)_lookupLocal( _delegate );

		if ( res == null ){

			res = new RPIPFilter( _delegate );
		}

		return( res );
	}

	protected
	RPIPFilter(
		IPFilter		_delegate )
	{
		super( _delegate );
	}

	@Override
	protected void
	_setDelegate(
		Object		_delegate )
	{
		delegate = (IPFilter)_delegate;

		last_update_time				= delegate.getLastUpdateTime();
		number_of_ranges				= delegate.getNumberOfRanges();
		number_of_blocked_ips			= delegate.getNumberOfBlockedIPs();
	}

	@Override
	public Object
	_setLocal()

		throws RPException
	{
		return( _fixupLocal());
	}

	@Override
	public RPReply
	_process(
		RPRequest	request	)
	{
		String	method = request.getMethod();

		Object[]	params = request.getParams();

		if ( method.equals( "createAndAddRange[String,String,String,boolean]")){

			IPRange range = delegate.createAndAddRange(
								(String)params[0],
								(String)params[1],
								(String)params[2],
								((Boolean)params[3]).booleanValue());

			if ( range == null ){

				return( new RPReply(null));

			}else{

				RPIPRange rp_range = RPIPRange.create( range );

				return( new RPReply( rp_range ));
			}
		}else if ( method.equals( "getRanges")){

				IPRange[] ranges = delegate.getRanges();

				RPIPRange[] rp_ranges = new RPIPRange[ranges.length];

				for (int i=0;i<ranges.length;i++){

					rp_ranges[i] = RPIPRange.create( ranges[i] );
				}

				return( new RPReply( rp_ranges ));

		}else if ( method.equals( "save" )){

			try{
				delegate.save();

				return(null );

			}catch( IPFilterException e ){

				return( new RPReply( e ));
			}
		}else if ( method.equals( "getInRangeAddressesAreAllowed")){

			return( new RPReply(Boolean.valueOf(delegate.getInRangeAddressesAreAllowed())));

		}else if ( method.equals( "setInRangeAddressesAreAllowed[boolean]")){

			delegate.setInRangeAddressesAreAllowed(((Boolean)params[0]).booleanValue());

			return( null );

		}else if ( method.equals( "isEnabled")){

			return( new RPReply(Boolean.valueOf(delegate.isEnabled())));

		}else if ( method.equals( "setEnabled[boolean]")){

			delegate.setEnabled(((Boolean)params[0]).booleanValue());

			return( null );

		}else if ( method.equals( "isInRange[String]")){

			return( new RPReply(Boolean.valueOf(delegate.isInRange((String) params[0]))));
		}

		throw( new RPException( "Unknown method: " + method ));
	}


		// ***************************************************

	@Override
	public File
	getFile()
	{
		notSupported();

		return( null );
	}


	@Override
	public IPRange
	createRange(
		int		address_type,
		boolean this_session_only )
	{
		notSupported();

		return( null );
	}

	@Override
	public void
	addRange(
		IPRange		range )
	{
		notSupported();
	}

	@Override
	public IPRange
	createAndAddRange(
		String		description,
		String		start_ip,
		String		end_ip,
		boolean		this_session_only )
	{
		RPIPRange resp = (RPIPRange)_dispatcher.dispatch(
							new RPRequest(
									this,
									"createAndAddRange[String,String,String,boolean]",
									new Object[]{description,start_ip,end_ip, Boolean.valueOf(this_session_only)})).getResponse();

		resp._setRemote( _dispatcher );

		return( resp );

	}

	@Override
	public void
	removeRange(
		IPRange		range )
	{
		notSupported();
	}

	@Override
	public void
	reload()

		throws IPFilterException
	{
		notSupported();

	}

	@Override
	public IPRange[]
	getRanges()
	{
		RPIPRange[] resp = (RPIPRange[])_dispatcher.dispatch(
				new RPRequest(
						this,
						"getRanges",
						null)).getResponse();

		for (int i=0;i<resp.length;i++){

			resp[i]._setRemote( _dispatcher );
		}

		return( resp );
	}

	@Override
	public boolean
	isInRange(
		String IPAddress )
	{
		Boolean res = (Boolean)_dispatcher.dispatch( new RPRequest( this, "isInRange[String]", new Object[]{IPAddress})).getResponse();

		return( res.booleanValue());
	}

	@Override
	public IPBlocked[]
	getBlockedIPs()
	{
		notSupported();

		return( null );
	}

	@Override
	public void
	block(
		String IPAddress)
	{
		notSupported();

	}

	@Override
	public IPBanned[]
 	getBannedIPs()
 	{
 		notSupported();

 		return( null );
 	}

 	@Override
  public void
 	ban(
 		String IPAddress,
 		String text)
 	{
 		notSupported();
 	}

	@Override
	public void
	unban(
		String IPAddress)
	{
		notSupported();
	}

	@Override
	public boolean
	getInRangeAddressesAreAllowed()
	{
		Boolean res = (Boolean)_dispatcher.dispatch( new RPRequest( this, "getInRangeAddressesAreAllowed", null )).getResponse();

		return( res.booleanValue());
	}

	@Override
	public void
	setInRangeAddressesAreAllowed(
		boolean	value )
	{
		_dispatcher.dispatch( new RPRequest( this, "setInRangeAddressesAreAllowed[boolean]", new Object[]{Boolean.valueOf(value)} )).getResponse();
	}

	@Override
	public boolean
	isEnabled()
	{
		Boolean res = (Boolean)_dispatcher.dispatch( new RPRequest( this, "isEnabled", null )).getResponse();

		return( res.booleanValue());
	}

	@Override
	public void
	setEnabled(
		boolean	value )
	{
		_dispatcher.dispatch( new RPRequest( this, "setEnabled[boolean]", new Object[]{Boolean.valueOf(value)} )).getResponse();
	}

	@Override
	public void
	save()

		throws IPFilterException
	{
		try{
			_dispatcher.dispatch( new RPRequest( this, "save", null )).getResponse();

		}catch( RPException e ){

			if ( e.getCause() instanceof IPFilterException ){

				throw((IPFilterException)e.getCause());
			}

			throw( e );
		}
	}

	@Override
	public long
	getLastUpdateTime()
	{
		return( last_update_time );
	}

	@Override
	public int
	getNumberOfRanges()
	{
		return( number_of_ranges );
	}

	@Override
	public int
	getNumberOfBlockedIPs()
	{
		return( number_of_blocked_ips );
	}

	@Override
	public int
	getNumberOfBannedIPs()
	{
		notSupported();

		return -1;
	}
}