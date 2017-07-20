/*
 * Created on 12 Jun 2006
 * Created by Marc Colosimo
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

package com.biglybt.net.natpmp.upnp.impl;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import com.biglybt.net.natpmp.NatPMPDevice;
import com.biglybt.net.upnp.UPnPException;
import com.biglybt.net.upnp.services.UPnPWANConnectionPortMapping;

public class NatPMPImpl {
    /* NatPMP stuff */
    private NatPMPDevice natDevice;

    /* Client stuff */
    private List    mappings = new ArrayList(); // not synchronized!



    public NatPMPImpl(NatPMPDevice device ) throws UPnPException {
        try {
            natDevice = device;
        } catch( Exception e) {
            throw( new UPnPException( "Error in getting NatPMP Service!" ));
        }
    }

    /**
     * Client framework methods
     *
     * @see: ...service/UPnPSSWANConnectionImpl.java
     **/
    public void addPortMapping(
                    boolean tcp,        // false -> UDP
                    int port,
                    String description )
                    throws UPnPException
    {
        try{
            /* use public port for internal port */
            natDevice.addPortMapping( tcp, port, port );
        } catch( Exception e) {
            throw( new UPnPException( "addPortMapping failed", e ));
        }

        synchronized( this ){
	        /* Find and remove old mapping, if any */
	        Iterator it = mappings.iterator();
	        while( it.hasNext() ) {
	            portMapping m = (portMapping)it.next();

	            if (m.getExternalPort() == port && m.isTCP() == tcp)
	                it.remove() ;
	        }
	        /* add new port to list */
	        mappings.add(   new portMapping( port, tcp,
	                        natDevice.getLocalAddress().getHostAddress(),
	                        description) );
        }
    }

    public void deletePortMapping( boolean tcp, int port)
                throws UPnPException
    {
        try {
            natDevice.deletePortMapping( tcp, port, port );
        } catch ( Exception e) {
            throw( new UPnPException("deletePortMapping failed", e) );
        }

        synchronized( this ){
	        /* Delete from the mappings */
	        Iterator it = mappings.iterator();
	        while( it.hasNext() ) {
	            portMapping m = (portMapping)it.next();

	            if (m.getExternalPort() == port && m.isTCP() == tcp)
	                it.remove() ;
	        }
        }
    }

    public UPnPWANConnectionPortMapping[] getPortMappings()
                throws UPnPException
    {
    	  synchronized( this ){
	        /* Check UPnPSSWANConnectionImpl.java for hints */
	        UPnPWANConnectionPortMapping[] res2 = new UPnPWANConnectionPortMapping[mappings.size()];
	        mappings.toArray(res2);
	        return res2;
    	  }
    }

    public String[] getStatusInfo()
                throws UPnPException
    {
        String connection_status    = null;
        String connection_error     = null;
        String uptime               = null;

        /* can we ping the NAT for this info? */
        uptime = "" + natDevice.getEpoch();
        return( new String[] { connection_status, connection_error, uptime } );
    }

	public String
	getExternalIPAddress()
	{
		return(natDevice.getExternalIPAddress());
	}

    private static class portMapping
        implements UPnPWANConnectionPortMapping
    {
        protected int           external_port;
        protected boolean       tcp;
        protected String        internal_host;
        protected String        description;

        protected portMapping(
                int         _external_port,
                boolean     _tcp,
                String      _internal_host,
                String      _description )
        {
            external_port   = _external_port;
            tcp             = _tcp;
            internal_host   = _internal_host;
            description     = _description;
        }

        @Override
        public boolean isTCP()
        {
            return( tcp );
        }

        @Override
        public int getExternalPort()
        {
            return( external_port );
        }

        @Override
        public String getInternalHost()
        {
            return( internal_host );
        }

        @Override
        public String getDescription()
        {
            return( description );
        }

        protected String getString()
        {
            return( getDescription() + " [" + getExternalPort() + ":" + (isTCP()?"TCP":"UDP") + "]");
        }
    } /* end protected class portMapping */
} /* end public class NatPMPImpl */