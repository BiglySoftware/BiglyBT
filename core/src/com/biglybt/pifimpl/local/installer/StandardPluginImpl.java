/*
 * Created on 28-Nov-2004
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

package com.biglybt.pifimpl.local.installer;

/**
 * @author parg
 *
 */

import java.util.List;

import com.biglybt.core.html.HTMLUtils;
import com.biglybt.core.util.Debug;
import com.biglybt.pif.Plugin;
import com.biglybt.pif.PluginInterface;
import com.biglybt.pif.installer.StandardPlugin;
import com.biglybt.pif.update.UpdateCheckInstance;
import com.biglybt.pifimpl.update.PluginUpdatePlugin;
import com.biglybt.pifimpl.update.sf.SFPluginDetails;

public class
StandardPluginImpl
	extends InstallablePluginImpl
	implements StandardPlugin
{
	private SFPluginDetails		details;
	private String				version;

	protected
	StandardPluginImpl(
		PluginInstallerImpl	_installer,
		SFPluginDetails		_details,
		String				_version )
	{
		super( _installer );

		details		= _details;
		version		= _version==null?"":_version;
	}

	@Override
	public String
	getId()
	{
		return( details.getId());
	}

	@Override
	public String
	getVersion()
	{
		return( version );
	}

	@Override
	public String
	getName()
	{
		return( details.getName());
	}

	@Override
	public String
	getDescription()
	{
		try{
			List lines = HTMLUtils.convertHTMLToText("", details.getDescription());

			String	res = "";

			for (int i=0;i<lines.size();i++){
				res += (i==0?"":"\n") + lines.get(i);
			}

			return( res );

		}catch( Throwable e ){

			return( Debug.getNestedExceptionMessage( e ));
		}
	}

	@Override
	public String
	getRelativeURLBase()
	{
		return( details.getRelativeURLBase());
	}

	@Override
	public void
	addUpdate(
			UpdateCheckInstance	inst,
			PluginUpdatePlugin	plugin_update_plugin,
			Plugin plugin,
			PluginInterface plugin_interface )
	{
		inst.addUpdatableComponent(
				plugin_update_plugin.getCustomUpdateableComponent( getId(), false), false );
	}
}
