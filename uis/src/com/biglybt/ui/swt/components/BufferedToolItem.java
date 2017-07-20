/*
 * File    : BufferedToolItem.java
 * Created : 08-Dec-2003
 * By      : parg
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details ( see the LICENSE file ).
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 */

package com.biglybt.ui.swt.components;

/**
 * @author parg
 *
 */

import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.widgets.Listener;
import org.eclipse.swt.widgets.ToolBar;
import org.eclipse.swt.widgets.ToolItem;

public class
BufferedToolItem
	extends BufferedWidget
{
	protected ToolItem		item;

	public
	BufferedToolItem(
		ToolBar		tool_bar,
		int			attributes )
	{
		super( new ToolItem( tool_bar, attributes ));

		item = (ToolItem)getWidget();
	}

	public void
	setEnabled(
			boolean	b )
	{
		if ( item.isDisposed() || item.getEnabled() == b ){

			return;
		}

		item.setEnabled( b );
	}

	public void
	setSelection(
			boolean	b )
	{
		if ( item.isDisposed() || item.getSelection() == b ){

			return;
		}

		item.setSelection( b );
	}

	public void
	setImage(
		Image	i )
	{
		if (i != null)
			i.setBackground(item.getParent().getBackground());
		item.setImage(i);
	}

	public Object
	getData(
		String	key )
	{
		return( item.getData(key));
	}

	public void
	setData(
		String	key,
		Object	d )
	{
		item.setData(key,d);
	}

	public void
	addListener(
		int			type,
		Listener	l )
	{
		item.addListener( type, l );
	}
}
