/*
 * Copyright (C) Azureus Software, Inc, All Rights Reserved.
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

package com.biglybt.core.util;

import java.io.File;

public class ShellUtilityFinder {


	public static String getChMod() {
		return findCommand("chmod");
	}

	public static String getNice() {
		return findCommand("nice");
	}

	public static String
	findCommand(
	  String name )
	{
	  final String[] locations = { "/bin", "/usr/bin" };
	  for ( String s: locations ){
	      File f = FileUtil.newFile( s, name );
	      if ( f.exists() && f.canRead()){
	          return( f.getAbsolutePath());
	      }
	  }
	  return( name );
	}

}
