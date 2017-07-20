/*
 * File    : IPRange.java
 * Created : 05-Mar-2004
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

package com.biglybt.pifimpl.local.ipfilter;

/**
 * @author parg
 *
 */

import com.biglybt.core.ipfilter.IpRange;
import com.biglybt.pif.ipfilter.IPFilter;
import com.biglybt.pif.ipfilter.IPRange;

public class
IPRangeImpl
	implements IPRange
{
	private IPFilter		filter;
	private IpRange		range;

	protected
	IPRangeImpl(
		IPFilter	_filter,
		IpRange		_range )
	{
		filter	= _filter;
		range	= _range;
	}

	protected IpRange
	getRange()
	{
		return( range );
	}

	@Override
	public String
	getDescription()
	{
		return( range.getDescription());
	}

	@Override
	public void
	setDescription(
		String	str )
	{
		range.setDescription(str);
	}

	@Override
	public boolean
	isValid()
	{
		return( range.isValid());
	}

	@Override
	public void
	checkValid()
	{
		range.checkValid();
	}

	@Override
	public boolean
	isSessionOnly()
	{
		return( range.isSessionOnly());
	}

	@Override
	public String
	getStartIP()
	{
		return( range.getStartIp());
	}

	@Override
	public void
	setStartIP(
		String	str )
	{
		range.setStartIp(str);
	}

	@Override
	public String
	getEndIP()
	{
		return( range.getEndIp());
	}

	@Override
	public void
	setEndIP(
		String	str )
	{
		range.setEndIp(str);
	}

	@Override
	public void
	setSessionOnly(
		boolean sessionOnly )
	{
		range.setSessionOnly(sessionOnly);
	}

	@Override
	public boolean
	isInRange(
		String ipAddress )
	{
		return( range.isInRange(ipAddress));
	}

	@Override
	public void
	delete()
	{
		filter.removeRange( this );
	}

	public boolean
	equals(
		Object		other )
	{
		if ( !(other instanceof IPRangeImpl )){

			return( false );
		}

		return( compareTo( other ) == 0 );
	}

	public int
	hashCode()
	{
		int hash = getStartIP().hashCode();

		String ip = getEndIP();

		if ( ip != null ){

			hash += ip.hashCode();
		}

		return( hash );
	}

	@Override
	public int
	compareTo(
		Object		other )
	{
		if ( !(other instanceof IPRangeImpl )){

			throw( new RuntimeException( "other object must be IPRange" ));

		}

		IPRangeImpl	o = (IPRangeImpl)other;

		String	ip1 = getStartIP();
		String	ip2 = o.getStartIP();

		int	res = ip1.compareTo(ip2);

		if ( res != 0 ){
			return( res );
		}

		ip1	= getEndIP();
		ip2 = o.getEndIP();

		if ( ip1 == null && ip2 == null ){
			return(0);
		}

		if ( ip1 == null ){
			return( -1 );
		}

		if ( ip2 == null ){
			return( 1 );
		}

		return( ip1.compareTo(ip2));
	}
}
