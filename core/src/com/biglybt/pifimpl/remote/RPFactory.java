/*
 * File    : RPFactory.java
 * Created : 28-Jan-2004
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

package com.biglybt.pifimpl.remote;

/**
 * @author parg
 *
 */
public class
RPFactory
{
	public static RPPluginInterface
	getPlugin(
		RPRequestDispatcher		dispatcher )

		throws RPException
	{
		RPRequest	request = new RPRequest( null, "getSingleton", null );

		RPReply		reply	= dispatcher.dispatch( request );

		RPPluginInterface	pi = (RPPluginInterface)reply.getResponse();

		pi._setRemote( dispatcher );

		return( pi );
	}
}
