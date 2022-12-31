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
	private String actionID;
	private int		style	= STYLE_BUTTON;
	private String	image_id;

	public ActionParameterImpl(String labelKey, String actionTextKey) {
		super(null, labelKey);

		action_resource	= actionTextKey;
	}

	@Override
	public String
	getActionResource()
	{
		return( action_resource );
	}

	@Override
	public void setActionResource(String action_resource) {
		this.action_resource = action_resource;
		this.refreshControl();
	}

	@Override
	public void
	setStyle(
		int		_style )
	{
		style	= _style;
	}


	@Override
	public Object getValueObject() {
		return null;
	}

	@Override
	public int
	getStyle()
	{
		return( style );
	}

	@Override
	public String getActionID() {
		return actionID == null ? action_resource : actionID;
	}

	@Override
	public void setActionID(String actionID) {
		this.actionID = actionID;
	}
	
	@Override
	public void
	setImageID(
		String	id )
	{
		image_id = id;
	}
	
	@Override
	public String
	getImageID()
	{
		return( image_id );
	}
}
