/*
 * Copyright (C) Bigly Software, Inc, All Rights Reserved.
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
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA 02111-1307  USA
 */

package com.biglybt.ui.swt.skin;

import org.eclipse.swt.widgets.Composite;

public interface 
SWTSkinObjectExpandItem
{
	public void
	setText(
		String		text );
	
	public Composite
	getComposite();
	
	public Composite
	getParentComposite();
	
	public SWTSkinObject 
	getParent();
	
	public void
	relayout();
	
	public void
	setExpanded(
		boolean	b );
	
	public void
	setHeight(
		int		h );
}
