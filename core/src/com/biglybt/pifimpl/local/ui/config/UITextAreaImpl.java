/*
 * Created on Sep 17, 2014
 * Created by Paul Gardner
 *
 * Copyright 2014 Azureus Software, Inc.  All rights reserved.
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
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA 02111-1307, USA.
 */


package com.biglybt.pifimpl.local.ui.config;

import com.biglybt.pif.ui.components.UIPropertyChangeListener;
import com.biglybt.pif.ui.components.UITextArea;
import com.biglybt.pif.ui.components.UIComponent.RefreshListener;


public class
UITextAreaImpl
	extends 	ParameterImpl
	implements 	UITextArea
{
	private com.biglybt.pifimpl.local.ui.components.UITextAreaImpl	text_area;

	public
	UITextAreaImpl(
		String					resource_name )
	{
		super(resource_name, resource_name);

		text_area = new com.biglybt.pifimpl.local.ui.components.UITextAreaImpl();
	}

	@Override
	public void
	setText(
		String		text )
	{
		text_area.setText( text );
	}

	@Override
	public void
	appendText(
		String		text )
	{
		text_area.appendText(text);
	}

	@Override
	public String
	getText()
	{
		return( text_area.getText());
	}

	@Override
	public void
	setMaximumSize(
		int	max_size )
	{
		text_area.setMaximumSize(max_size);
	}

	@Override
	public void
	setEnabled(
		boolean		enabled )
	{
		super.setEnabled( enabled );

		text_area.setEnabled(enabled);
	}

	@Override
	public boolean
	getEnabled()
	{
		return( super.isEnabled());
	}

	@Override
	public void
	setVisible(
		boolean		visible )
	{
		// TODO: visible was never implemented..
		super.setEnabled( visible );

		text_area.setEnabled(visible );
	}

	@Override
	public boolean
	getVisible()
	{
		return( super.isVisible());
	}

	@Override
	public void
	setProperty(
		String	property_type,
		Object	property_value )
	{
		text_area.setProperty(property_type, property_value);
	}

	@Override
	public Object
	getProperty(
		String		property_type )
	{
		return( text_area.getProperty(property_type));
	}

	@Override
	public void
	addPropertyChangeListener(
		UIPropertyChangeListener	l )
	{
		text_area.addPropertyChangeListener(l);
	}

	@Override
	public void
	removePropertyChangeListener(
		UIPropertyChangeListener	l )
	{
		text_area.removePropertyChangeListener(l);
	}

	@Override
	public Object getValueObject() {
		return getText();
	}
	
	public void
	refresh()
	{
		text_area.refresh();
	}
	
	public void
	addRefreshListener(
		RefreshListener	l )
	{
		text_area.addRefreshListener(l);
	}
	
	public void
	removeRefreshListener(
		RefreshListener	l )
	{
		text_area.removeRefreshListener(l);
	}
}
