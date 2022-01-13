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

package com.biglybt.ui.swt.views.table.painted;

import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.Device;
import org.eclipse.swt.graphics.GC;

import com.biglybt.core.config.COConfigurationManager;
import com.biglybt.core.config.ParameterListener;
import com.biglybt.core.util.Debug;
import com.biglybt.ui.swt.mainwindow.Colors;

public class 
TablePaintedUtils
{
	private static boolean	is_dark = false;
	
	static{
		COConfigurationManager.addAndFireParameterListener(
			"Dark Table Colors",
			new ParameterListener(){
				boolean	first = true;
				
				@Override
				public void parameterChanged(String parameterName){

					is_dark = COConfigurationManager.getBooleanParameter("Dark Table Colors" );
					
					if ( first ){
						first = false;
					}else{
						Colors.getInstance().allocateColorAltRow();
					}
				}
			});
	}
	
	public static boolean
	isDark()
	{
		return( is_dark );
	}
	
	protected static Color
	getColour(
		GC		gc,
		int		type )
	{
		return( getColour( gc.getDevice(), type ));
	}
	
	public static Color
	getColour(
		Device		device,
		int			type )
	{
		if ( is_dark ){
			
			switch( type ){
				case SWT.COLOR_LIST_BACKGROUND:{
					return( Colors.black );
				}
				case SWT.COLOR_LIST_FOREGROUND:{
					return( Colors.white );
				}
				case SWT.COLOR_LIST_SELECTION:{
					return( Colors.dark_grey );
				}
				case SWT.COLOR_LIST_SELECTION_TEXT:{
					return( Colors.white );
				}
				case SWT.COLOR_WIDGET_NORMAL_SHADOW:{
					return( Colors.grey );
				}
				case SWT.COLOR_WIDGET_LIGHT_SHADOW:{
					return( Colors.black );
				}
				case SWT.COLOR_WIDGET_BACKGROUND:{
					return( Colors.dark_grey );
				}
				default:{
					
					Debug.out( "Unhandled colour: " + type );
				}
			}
		}
		
		return( device.getSystemColor( type ));
	}
}
