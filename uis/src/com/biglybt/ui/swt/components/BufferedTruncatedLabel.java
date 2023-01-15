/*
 * Created on 04-Jan-2005
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

package com.biglybt.ui.swt.components;

import org.eclipse.swt.events.MouseListener;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.Cursor;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Label;
import com.biglybt.core.util.DisplayFormatters;
import com.biglybt.ui.swt.Utils;

/**
 * @author parg
 *
 */

public class
BufferedTruncatedLabel
	extends BufferedWidget
{
	protected Label	label;
	protected int	width;

	protected String	value = "";

	public
	BufferedTruncatedLabel(
		Composite		composite,
		int				attrs,
		int				_width)
	{
		super( new Label( composite, attrs ));

		label 	= (Label)getWidget();
		width	= _width;
	}

	public boolean
	isDisposed()
	{
		return( label.isDisposed());
	}

	public Control
	getControl()
	{
		return( label );
	}
	
	public void
	setLayoutData(
		GridData	gd )
	{
  	if (isDisposed()) {
  		return;
  	}
		label.setLayoutData( gd );
	}

	public void
	setText(
		String	new_value )
	{
		if ( label.isDisposed()){
			return;
		}

		if ( new_value == value ){

			return;
		}

		if (	new_value != null &&
				value != null &&
				new_value.equals( value )){

			return;
		}

		value = new_value;

			// '&' chars that occur in the text are treated as accelerators and, for example,
			// cause the nect character to be underlined on Windows. This is generally NOT
			// the desired behaviour of a label so by default we escape them

		label.setText( value==null?"":DisplayFormatters.truncateString( value.replaceAll("&", "&&" ), width ));
	}

  public String getText() {
    return value==null?"":value;
  }

  public void addMouseListener(MouseListener listener) {
    label.addMouseListener(listener);
  }

  public void setForeground(Color color) {
  	if (isDisposed()) {
  		return;
  	}
    label.setForeground(color);
  }

  public void setCursor(Cursor cursor) {
  	if (isDisposed() || cursor == null || cursor.isDisposed()) {
  		return;
  	}
    label.setCursor(cursor);
  }

  public void setToolTipText(String toolTipText) {
  	if (isDisposed()) {
  		return;
  	}
    Utils.setTT(label,toolTipText);
  }

}