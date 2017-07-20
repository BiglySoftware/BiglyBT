/*
 * Created on 17-Jun-2004
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

package com.biglybt.pifimpl.local.ui.config;

import com.biglybt.pif.ui.config.ActionParameter;
import com.biglybt.pifimpl.local.PluginConfigImpl;

/**
 * @author parg
 *
 */

public class
ActionParameterImpl
	extends ParameterImpl
	implements ActionParameter
{
	private String	action_resource;
	private int		style	= STYLE_BUTTON;

	public
	ActionParameterImpl(
		PluginConfigImpl 	config,
		String 			label_resource_name,
		String			action_resource_name )
	{
		super( config, label_resource_name, label_resource_name);

		action_resource	= action_resource_name;
	}

	public String
	getActionResource()
	{
		return( action_resource );
	}

	@Override
	public void
	setStyle(
		int		_style )
	{
		style	= _style;
	}

	@Override
	public int
	getStyle()
	{
		return( style );
	}
}
