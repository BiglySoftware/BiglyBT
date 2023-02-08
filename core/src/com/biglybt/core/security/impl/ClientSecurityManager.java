/*
 * Copyright (C) Bigly Software, Inc, All Rights Reserved.
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA 02111-1307  USA
 */

package com.biglybt.core.security.impl;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.InterfaceAddress;
import java.net.NetPermission;
import java.net.NetworkInterface;
import java.security.Permission;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.biglybt.core.config.COConfigurationManager;
import com.biglybt.core.config.ConfigKeys;
import com.biglybt.core.security.SESecurityManager.MySecurityManager;
import com.biglybt.core.util.SystemProperties;

public final class
ClientSecurityManager
	extends SecurityManager
	implements MySecurityManager
{
	final ThreadLocal<Boolean>	tls_ni = new ThreadLocal<>();
	
	private final SESecurityManagerImpl	se_sec_man;
	private final SecurityManager		old_sec_man;

	private volatile boolean	filter_v4;
	private volatile boolean	filter_v6;
	
	{
		COConfigurationManager.addAndFireParameterListeners(
			new String[]{
				ConfigKeys.Connection.BCFG_IPV_4_IGNORE_NI_ADDRESSES,
				ConfigKeys.Connection.BCFG_IPV_6_IGNORE_NI_ADDRESSES },
			(n)->{
				filter_v4 = COConfigurationManager.getBooleanParameter( ConfigKeys.Connection.BCFG_IPV_4_IGNORE_NI_ADDRESSES );
				filter_v6 = COConfigurationManager.getBooleanParameter( ConfigKeys.Connection.BCFG_IPV_6_IGNORE_NI_ADDRESSES );
			});
	}
	
	private volatile Set<String>	filtered_addresses = new HashSet<>();
	
	public 
	ClientSecurityManager(
		SESecurityManagerImpl		_se_sec_man )
	{
		se_sec_man	= _se_sec_man;
		old_sec_man	= System.getSecurityManager();

		System.setSecurityManager( this );
	}

	@Override
	public void checkAccept(String host, int port) {
		// do nothing
	}

	@Override
	public void checkRead(String file) {
		// do nothing
	}

	@Override
	public void checkWrite(String file) {
		// do nothing
	}

	@Override
	public void checkConnect(String host, int port) {
		
		if ( port == -1 ){
			
			if ( tls_ni.get() == null ){
									
				if ( filtered_addresses.contains( host )){
					
					throw( new SecurityException( "Access denied"));
				}
			}
		}
	}

	@Override
	public void
	checkExit(int status)
	{
		if ( old_sec_man != null ){

			old_sec_man.checkExit( status );
		}

		if ( !se_sec_man.canExitVM()){

			String	prop = System.getProperty(SystemProperties.SYSPROP_SECURITY_MANAGER_PERMITEXIT, "0" );

			if ( prop.equals( "0" )){

				throw( new SecurityException( "VM exit operation prohibited"));
			}
		}
	}

	@Override
	public void
	checkPermission(
		Permission perm )
	{
		checkPermission( perm, null );
	}

	@Override
	public void
	checkPermission(
		Permission 	perm,
		Object 		context)
	{
		if ( perm instanceof RuntimePermission ){

			String name = perm.getName();

			if ( name.equals( "stopThread")){

				if ( se_sec_man.isStoppableThread()){
					
					return;
				}

				throw( new SecurityException( "Thread.stop operation prohibited"));

			}else if ( name.equals( "setSecurityManager" )){

				throw( new SecurityException( "Permission Denied"));
			}
		}else if ( perm instanceof NetPermission ){
			
				// we have to fail this permission in order to cause the NetworkInterface code
				// to revert to calling checkConnect 
			
			if ( tls_ni.get() == null && !filtered_addresses.isEmpty()){
			
				if ( perm.getName().equals( "getNetworkInformation" )){
					
					throw( new SecurityException( "Permission Denied"));
				}
			}
		}

		if ( old_sec_man != null ){

			if ( context == null ){

				old_sec_man.checkPermission( perm );

			}else{

				old_sec_man.checkPermission( perm, context );
			}
		}
	}

	@Override
	public boolean
	filterNetworkInterfaces( 
		List<NetworkInterface>		interfaces )
	{
		/* 
		 * We filter addresses out of network interfaces via the 'checkConnect' permissions check done by 
		 * the NetworkInterface implementation. We cache the filtered addresses so we can fail them from calls outside of
		 * this code but obviously we need to disable that when figuring out what to filter
		 */
		
		boolean changed = false;

		try{
			tls_ni.set( true );
			
			Set<String>	filtered = new HashSet<>();
			
			for ( NetworkInterface ni: interfaces ){
				
				try{
					for ( InterfaceAddress ia: ni.getInterfaceAddresses()){
						
						InetAddress address = ia.getAddress();
						
						if ( 	( filter_v6 && address instanceof Inet6Address ) ||
								( filter_v4 && address instanceof Inet4Address )){
							
							filtered.add( address.getHostAddress());
						}
					}
				}catch( Throwable e ){
					
					// seen NPE here from ni.getInterfaceAddresses()
				}
			}
			
			if ( !filtered.equals( filtered_addresses )){
				
				filtered_addresses = filtered;
				
				changed = true;
			}
		}finally{
			
			tls_ni.set( null );
		}
		
		return( changed );
	}
	
	@Override
	public Class[]
	getClassContext()
	{
		Class[] res = super.getClassContext();

		if ( res.length <= 3 ){

			return( new Class[0] );
		}

		Class[] trimmed = new Class[res.length-3];

		System.arraycopy( res, 3, trimmed, 0, trimmed.length );

		return( trimmed );
	}
}