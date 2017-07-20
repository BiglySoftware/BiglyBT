/*
 * Created on 22-Mar-2005
 * Created by Paul Gardner
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

package com.biglybt.core.dht.control;

import java.util.List;

import com.biglybt.core.dht.transport.DHTTransportContact;

/**
 * @author parg
 *
 */

public interface
DHTControlActivity
{
	public static final int	AT_INTERNAL_GET		= 1;
	public static final int	AT_EXTERNAL_GET		= 2;
	public static final int	AT_INTERNAL_PUT		= 3;
	public static final int	AT_EXTERNAL_PUT		= 4;

	public byte[]
	getTarget();

	public String
	getDescription();

	public int
	getType();

	public boolean
	isQueued();

	public ActivityState
	getCurrentState();

	public String
	getString();


	public interface
	ActivityState
	{
		public ActivityNode
		getRootNode();

		public int
		getDepth();

		public String
		getResult();

		public String
		getString();
	}

	public interface
	ActivityNode
	{
		public DHTTransportContact
		getContact();

		public List<ActivityNode>
		getChildren();
	}
}
