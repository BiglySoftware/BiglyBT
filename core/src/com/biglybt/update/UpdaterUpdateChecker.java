/*
 * Created on 07-May-2004
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

package com.biglybt.update;

/**
 * @author parg
 *
 */

import java.util.Properties;

import com.biglybt.pif.PluginInterface;
import com.biglybt.pif.UnloadablePlugin;

// Note this is unloadable because it shouldn't be loaded in the first place
	// Hence, on upgrade, a restart isn't required

public class
UpdaterUpdateChecker
	implements UnloadablePlugin
{
	public static String
	getPluginID()
	{
		return( UpdaterUtils.AZUPDATER_PLUGIN_ID );
	}

	@Override
	public void
	initialize(
		PluginInterface pi )
	{
		Properties	props = pi.getPluginProperties();

		props.setProperty( "plugin.mandatory", "true" );

		if ( pi.getPluginVersion() == null ){

			props.setProperty( "plugin.version", "1.0" );
		}

		props.setProperty( "plugin.id", "azupdater" );
	}

	@Override
	public void
	unload()
	{
	}
}