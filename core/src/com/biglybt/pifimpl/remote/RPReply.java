/*
 * File    : RPReply.java
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
import java.util.HashMap;
import java.util.Map;

import com.biglybt.pifimpl.remote.rpexceptions.RPThrowableAsReplyException;

/**
 * @author parg
 */

public class
RPReply
    implements Serializable
{
    public Object   response;

    transient protected Map     properties  = new HashMap();

    public
    RPReply(
        Object      _response )
    {
        response    = _response;
    }

    public Object getResponse() throws RPException {
        if (response instanceof RPException){
            throw((RPException)response);
        }
        else if (response instanceof Throwable) {
            throw new RPThrowableAsReplyException((Throwable)response);
        }
        return response;
    }

    public void
    setProperty(
        String      name,
        String      value )
    {
        properties.put( name, value );
    }

    public Map
    getProperties()
    {
        return( properties );
    }

    private Class response_class = null;
	public Class getResponseClass() {return response_class;}
	public void setResponseClass(Class c) {this.response_class = c;}
}
