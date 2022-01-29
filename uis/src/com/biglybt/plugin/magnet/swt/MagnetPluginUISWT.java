/*
 * Created on Jan 15, 2013
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


package com.biglybt.plugin.magnet.swt;

import org.eclipse.swt.graphics.Image;
import com.biglybt.pif.ui.UIInstance;
import com.biglybt.pif.ui.tables.TableContextMenuItem;
import com.biglybt.ui.swt.pif.UISWTInstance;


public class
MagnetPluginUISWT
{
	public
	MagnetPluginUISWT(
		UIInstance					instance,
		TableContextMenuItem[]		menus )
	{
		UISWTInstance	swt = (UISWTInstance)instance;

		Image	image = swt.loadImage("com/biglybt/plugin/magnet/icons/magnet.png");

		for ( TableContextMenuItem menu: menus ){

			menu.setGraphic( swt.createGraphic( image ));
		}
	}
}
