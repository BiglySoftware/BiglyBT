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

package com.biglybt.plugin.dht;

import java.util.List;

import com.biglybt.core.util.Debug;
import com.biglybt.plugin.dht.DHTPluginInterface.DHTInterface;

public interface
DHTPluginBasicInterface
{
	public String
	getAENetwork();
	
	public boolean
	isEnabled();
	
	public boolean
	isInitialising();

	public boolean
	isSleeping();
	
	public DHTInterface[]
	getDHTInterfaces();

	public List<DHTPluginValue>
	getValues();

	public void
	get(
		byte[]								original_key,
		String								description,
		byte								flags,
		int									max_values,
		long								timeout,
		boolean								exhaustive,
		boolean								high_priority,
		DHTPluginOperationListener			original_listener );
	
	public void
	put(
		byte[]						key,
		String						description,
		byte[]						value,
		byte						flags,
		DHTPluginOperationListener	listener);

	public default void
	put(
		byte[]						key,
		String						description,
		byte[]						value,
		short						flags,
		boolean						high_priority,
		DHTPluginOperationListener	listener)
	{
		if (( flags & 0xff00 ) != 0 ){
			
			Debug.out( "Flag loss!" );
		}
		
		put( key, description, value, (byte)flags, listener );
	}
}