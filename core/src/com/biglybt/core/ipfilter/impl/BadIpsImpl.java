/*
 * File    : BadIpsImpl.java
 * Created : 10 nov. 2003}
 * By      : Olivier
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
package com.biglybt.core.ipfilter.impl;

import java.util.HashMap;
import java.util.Map;

import com.biglybt.core.ipfilter.BadIp;
import com.biglybt.core.ipfilter.BadIps;
import com.biglybt.core.util.AEMonitor;
import com.biglybt.core.util.ByteFormatter;

/**
 * @author Olivier
 *
 */
public class BadIpsImpl implements BadIps {

  private static BadIps 	instance;
  private static final AEMonitor	class_mon	= new AEMonitor( "BadIps:class" );

  private final Map<String,BadIpImpl> 		bad_ip_map;
  private final AEMonitor	bad_ip_map_mon		= new AEMonitor( "BadIps:Map");

  public static BadIps
  getInstance()
  {
  	try{
  		class_mon.enter();

  		if( instance == null ){

  			instance = new BadIpsImpl();
  		}

  		return( instance );

  	}finally{

  		class_mon.exit();
  	}
  }

  public BadIpsImpl()
  {
    bad_ip_map = new HashMap<>();
  }

  @Override
  public int
  addWarningForIp(
  	String	ip,
  	byte[]	specific_hash )
  {
	if ( specific_hash != null ){

	  ip += " [" + ByteFormatter.encodeString( specific_hash ) + "]";
	}

    try{
    	bad_ip_map_mon.enter();

    	BadIpImpl	bad_ip = bad_ip_map.get( ip );

    	if ( bad_ip == null ){

    		bad_ip = new BadIpImpl(ip);

    		bad_ip_map.put( ip, bad_ip );
    	}

    	return( bad_ip.incrementWarnings());

    }finally{

    	bad_ip_map_mon.exit();
    }
  }

  @Override
  public BadIp[]
  getBadIps()
  {
    try{
    	bad_ip_map_mon.enter();

  		BadIp[]	res = new BadIp[bad_ip_map.size()];

  		bad_ip_map.values().toArray( res );

  		return( res );
    }finally{

       	bad_ip_map_mon.exit();
    }
  }

  @Override
  public boolean 
  removeBadIp(
	String ip )
  {
	  try{
		  bad_ip_map_mon.enter();

		  return( bad_ip_map.remove(ip) != null );
		  
	  }finally{

		  bad_ip_map_mon.exit();
	  }
  }
  
  @Override
  public void
  clearBadIps()
  {
    try{
    	bad_ip_map_mon.enter();

    	bad_ip_map.clear();

    }finally{

        bad_ip_map_mon.exit();
    }
  }

  @Override
  public int
  getNbBadIps()
  {
  	return( bad_ip_map.size());
  }
}
