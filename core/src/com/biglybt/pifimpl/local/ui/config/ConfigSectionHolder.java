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

import com.biglybt.ui.config.BaseConfigSection;
import com.biglybt.ui.config.ConfigSectionImpl;

import com.biglybt.pif.PluginInterface;
import com.biglybt.pif.ui.config.ConfigSection;

public class
ConfigSectionHolder extends ConfigSectionImpl {
	private BaseConfigSection section;
	private WeakReference<PluginInterface>	pi;
	protected
	ConfigSectionHolder(
		BaseConfigSection _section,
		PluginInterface		_pi )
	{
		super(_section.getConfigSectionID(), ConfigSection.SECTION_ROOT);

		section		= _section;

		if ( _pi != null ){

			pi = new WeakReference<>(_pi);
		}
	}

	@Override
	public void build() {
	}

	@Override
	public void
	saveConfigSection()
	{
		section.saveConfigSection();
	}

	@Override
	public void
	deleteConfigSection()
	{
		section.deleteConfigSection();
	}

	@Override
	public void requestRebuild() {
		section.requestRebuild();
	}

	public PluginInterface
	getPluginInterface()
	{
		return( pi==null?null:pi.get());
	}
}
