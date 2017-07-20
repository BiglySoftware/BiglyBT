/*
 * Created on Oct 19, 2010
 * Created by Paul Gardner
 *
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
 */


package com.biglybt.pifimpl.local.ui.config;

import java.lang.ref.WeakReference;

import com.biglybt.pif.PluginInterface;
import com.biglybt.pif.ui.config.ConfigSection;

public class
ConfigSectionHolder
	implements ConfigSection
{
	private ConfigSection					section;
	private WeakReference<PluginInterface>	pi;
	protected
	ConfigSectionHolder(
		ConfigSection		_section,
		PluginInterface		_pi )
	{
		section		= _section;

		if ( _pi != null ){

			pi = new WeakReference<>(_pi);
		}
	}

	@Override
	public String
	configSectionGetParentSection()
	{
		return( section.configSectionGetParentSection());
	}

	@Override
	public String
	configSectionGetName()
	{
		return( section.configSectionGetName());
	}

	@Override
	public void
	configSectionSave()
	{
		section.configSectionSave();
	}

	@Override
	public void
	configSectionDelete()
	{
		section.configSectionDelete();
	}

	public PluginInterface
	getPluginInterface()
	{
		return( pi==null?null:pi.get());
	}
}
