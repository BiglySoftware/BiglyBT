/*
 * File    : RPObject.java
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
import java.util.Map;
import java.util.Random;
import java.util.WeakHashMap;

import com.biglybt.pifimpl.remote.rpexceptions.RPObjectNoLongerExistsException;


public class
RPObject
    implements Serializable
{
        // as long as the key is referenced by the rest of core
        //      key refers to RPObject
        //      RPObject refers to object_id
        //      object_id refers to RPObject
        // so neither weak map is cleared down

    protected transient static Map  	object_registry     	= new WeakHashMap();

    protected transient static Map    	object_registry_reverse = new WeakHashMap();

    protected transient static long   	next_key        		= new Random().nextLong();

    public Long _object_id;

    protected transient Object              __delegate;
    protected transient RPRequestDispatcher _dispatcher;



    // **** Don't try using AEMOnitor for synchronisations here as this object is serialised

    protected static RPObject
    _lookupLocal(
        Object      key )
    {
        synchronized( object_registry ){

            RPObject    res = (RPObject)object_registry.get(key);

            if ( res != null ){

                res._setLocal();
            }

            return( res );
        }
    }

    public static RPObject
    _lookupLocal(
        long        object_id )
    {
        synchronized( object_registry ){

            Object  res = object_registry_reverse.get( new Long(object_id ));

            if ( res == null ){
                throw new RPObjectNoLongerExistsException();
            }

            RPObject obj = (RPObject)object_registry.get( res );
            if (obj == null){
                throw new RPObjectNoLongerExistsException();
            }

            return( obj );
        }
    }

        // public constructor for XML deserialiser
    public
    RPObject()
    {
    }

    protected
    RPObject(
        Object      key )
    {
        synchronized( object_registry ){

            RPObject    existing = (RPObject)object_registry.get(key);

            if ( existing != null ){

                _object_id  = existing._object_id;

            }else{

                _object_id  = new Long(next_key++);

                object_registry.put( key, this );

                object_registry_reverse.put( _object_id, key );
            }
        }

        __delegate  = key;

        _setDelegate( __delegate );
    }

    public long
    _getOID()
    {
        return( _object_id.longValue());
    }

    protected void
    _setDelegate(
        Object      _delegate )
    {
        throw( new RuntimeException( "you've got to implement this - " + _delegate ));
    }

    public Object
    _getDelegate()
    {
        return( __delegate );
    }

    protected Object
    _fixupLocal()

        throws RPException
    {
        Object  res;

        synchronized( object_registry ){

        	res = object_registry_reverse.get( _object_id );
        }

        if ( res == null ){

            throw new RPObjectNoLongerExistsException();
        }

        _setDelegate( res );

        return( res );
    }

    public void
    _setRemote(
        RPRequestDispatcher     __dispatcher )
    {
        _dispatcher = __dispatcher;
    }

    protected RPRequestDispatcher
    getDispatcher()
    {
        return( _dispatcher );
    }

    public RPReply
    _process(
        RPRequest   request )
    {
        throw( new RuntimeException( "you've got to implement this - " + request ));
    }

    public Object
    _setLocal()
    {
        throw( new RuntimeException( "you've got to implement this"));
    }

    public void
    _refresh()
    {
        RPObject    res = (RPObject)_dispatcher.dispatch( new RPRequest( this, "_refresh", null )).getResponse();

        _setDelegate( res );
    }

    public String
    _getName()
    {
        String  str = this.getClass().getName();

        int dp = str.lastIndexOf('.');

        if ( dp != -1 ){

            str = str.substring(dp+1);
        }

        if ( str.startsWith("RP")){

            str = str.substring(2);
        }

        return( str );
    }

    public void
    notSupported()
    {
        throw( new RuntimeException( "RPObject:: method not supported"));
    }

    public void
    notSupported(
        Object  o )
    {
        throw( new RuntimeException( "RPObject:: method not supported - " + o ));
    }

}
