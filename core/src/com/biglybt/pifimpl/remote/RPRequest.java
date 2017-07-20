/*
 * File    : RPRequest.java
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

import java.io.Serializable;

import com.biglybt.pif.PluginInterface;
import com.biglybt.pif.logging.LoggerChannel;

/**
 * @author parg
 *
 */
public class
RPRequest
    implements Serializable
{
        // don't change these names as they end up in XML serialised data

    public RPObject object;
    public String   method;
    public Object[] params;
    protected transient PluginInterface plugin_interface;
    protected transient LoggerChannel channel;

    public long     connection_id;
    public long     request_id;

    protected transient String      client_ip;

        // public constructor for XML deserialiser

    public
    RPRequest()
    {
    }

    public
    RPRequest(
        RPObject            _object,
        String              _method,
        Object[]            _params )
    {
        object      = _object;
        method      = _method;
        params      = _params;

        if ( object != null ){

            RPPluginInterface   pi = object.getDispatcher().getPlugin();

            connection_id   = pi._getConectionId();
            request_id      = pi._getNextRequestId();
            plugin_interface = (PluginInterface)pi._getDelegate();
        }
    }

    public void
    setClientIP(
        String      str )
    {
        client_ip       = str;
    }

    public String
    getClientIP()
    {
        return( client_ip );
    }

    public long
    getConnectionId()
    {
        return( connection_id );
    }

    public long
    getRequestId()
    {
        return( request_id );
    }

    public String
    getString()
    {
        return( "object=" + object + ", method=" + method + ", params=" + params );
    }

    public RPObject
    getObject()
    {
        return( object );
    }

    public String
    getMethod()
    {
        return( method );
    }

    public Object[]
    getParams()
    {
        return( params );
    }

    public PluginInterface getPluginInterface() {
        return this.plugin_interface;
    }

    public void setPluginInterface(PluginInterface pi) {
        this.plugin_interface = pi;
    }

    public LoggerChannel getRPLoggerChannel() {
        return this.channel;
    }

    public void setRPLoggerChannel(LoggerChannel channel) {
        this.channel = channel;
    }

    // Can be overridden by subclasses.
    public RPPluginInterface createRemotePluginInterface(PluginInterface pi) {
    	return RPPluginInterface.create(pi);
    }

}
