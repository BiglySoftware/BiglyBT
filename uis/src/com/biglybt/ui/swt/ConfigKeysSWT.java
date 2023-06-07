/*
 * Copyright (C) Bigly Software.  All Rights Reserved.
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 */

package com.biglybt.ui.swt;

import com.biglybt.core.config.COConfigurationManager;

public interface ConfigKeysSWT {
	String ICFG_TABLE_HEADER_HEIGHT = "Table.headerHeight";
	String BCFG_FORCE_GRAYSCALE = "ForceNoColor";
	
	
	
	public static final String	ICFG_LAUNCH_HELPERS_ENTRY_COUNT			= "Table.lh.line.count";
	public static final int		ICFG_LAUNCH_HELPERS_ENTRY_COUNT_DEFAULT	= 4;
	
	public static final String SCFG_PREFIX_LAUNCH_HELPERS_EXTS	= "Table.lh";
	public static final String SCFG_SUFFIX_LAUNCH_HELPERS_EXTS	= ".exts";
	public static final String SCFG_PREFIX_LAUNCH_HELPERS_PROG	= "Table.lh";
	public static final String SCFG_SUFFIX_LAUNCH_HELPERS_PROG	= ".prog";

	public static int
	getLaunchHelperEntryCount()
	{
		return( COConfigurationManager.getIntParameter( ICFG_LAUNCH_HELPERS_ENTRY_COUNT, ICFG_LAUNCH_HELPERS_ENTRY_COUNT_DEFAULT ));
	}
	
	public static void
	setLaunchHelperEntryCount(
		int		num )
	{
		COConfigurationManager.setParameter( ICFG_LAUNCH_HELPERS_ENTRY_COUNT, num );
	}
	
	public static String
	getLaunchHelperExtConfig(
		int		index )
	{
		return( SCFG_PREFIX_LAUNCH_HELPERS_EXTS + index + SCFG_SUFFIX_LAUNCH_HELPERS_EXTS );
	}
	
	public static String
	getLaunchHelperProgConfig(
		int		index )
	{
		return( SCFG_PREFIX_LAUNCH_HELPERS_PROG + index + SCFG_SUFFIX_LAUNCH_HELPERS_PROG );
	}
	
	public static String
	getLaunchHelpersExts(
		int			index )
	{
		return( COConfigurationManager.getStringParameter( getLaunchHelperExtConfig( index ), "" ));
	}
	
	public static void
	setLaunchHelpersExts(
		int			index,
		String		exts )
	{
		COConfigurationManager.setParameter( getLaunchHelperExtConfig( index ), exts );
	}
	
	public static String
	getLaunchHelpersProg(
		int			index )
	{
		return( COConfigurationManager.getStringParameter( getLaunchHelperProgConfig( index ), "" ));
	}
	
	public static void
	setLaunchHelpersProg(
		int			index,
		String		prog )
	{
		COConfigurationManager.setParameter( getLaunchHelperProgConfig( index ), prog );
	}
}
