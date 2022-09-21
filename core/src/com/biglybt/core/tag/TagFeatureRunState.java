/*
 * Created on Mar 20, 2013
 * Created by Paul Gardner
 *
 * Copyright 2013 Azureus Software, Inc.  All rights reserved.
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
 */


package com.biglybt.core.tag;

import java.util.function.Predicate;

public interface
TagFeatureRunState
	extends TagFeatureRateLimit
{
	public static final int	RSC_STOP	= 0x00000001;
	public static final int	RSC_PAUSE	= 0x00000002;
	public static final int	RSC_RESUME	= 0x00000004;
	public static final int	RSC_START	= 0x00000008;

		// these are not fully supported yet so use at your own risk...
	
	public static final int	RSC_ARCHIVE					= 0x00000010;
	public static final int	RSC_REMOVE_FROM_LIBRARY		= 0x00000020;
	public static final int	RSC_REMOVE_FROM_COMPUTER	= 0x00000040;

		// fully supported
	
	public static final int	RSC_FORCE_START	= 0x00000080;

	public static final int	RSC_NONE	= 0x00000000;
	public static final int	RSC_ALL		= 0xffffffff;

	public static final int	RSC_STOP_PAUSE			= RSC_STOP | RSC_PAUSE;
	public static final int	RSC_START_STOP_PAUSE	= RSC_START | RSC_FORCE_START | RSC_STOP | RSC_PAUSE;

	public int
	getRunStateCapabilities();

	public boolean
	hasRunStateCapability(
		int		capability );

	public boolean[]
	getPerformableOperations(
		int[]	ops );

	public default boolean[]
	getPerformableOperations(
		int[]					ops,
		Predicate<Taggable>		filter )
	{
		return( getPerformableOperations( ops ));
	}

	public void
	performOperation(
		int		op );
	
	public default void
	performOperation(
		int						op,
		Predicate<Taggable>		filter )
	{
		performOperation( op );
	}
		
}
