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

import java.util.ArrayList;
import java.util.List;

import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Listener;

import com.biglybt.ui.swt.Utils;

public class SWTSkinObjectExpandBar2
	extends SWTSkinObjectContainer
	implements SWTSkinObjectExpandBar
{
	private List<SWTSkinObjectExpandItem2>	items = new ArrayList<>();
	
	private List<SWTSkinObjectExpandItem2>	fillHeightItems = new ArrayList<>();

	public 
	SWTSkinObjectExpandBar2(
		SWTSkin				skin, 
		SWTSkinProperties	properties,
		String				sID, 
		String				sConfigID, 
		SWTSkinObject		parent) 
	{
		super(skin, properties, sID, sConfigID, parent);
		
		type = "expandbar2";
		
		Composite pcomp = getComposite().getParent();
		
		pcomp.addListener(
			SWT.Resize, 
			new Listener() 
			{
				int	last_height = -1;
				
				@Override
				public void
				handleEvent(
					Event event) 
				{
					int height = pcomp.getClientArea().height;
					
					if ( handleResize( null )){
						
							// to get the scrollbar to behave when reducing the height of the window we need to force a relayout-up
						
						if ( height < last_height ){
							
							Utils.relayoutUp(getComposite());
						}
					}
					
					last_height = height;
				}
			});
	}
	
	@Override
	public boolean 
	isNative()
	{
		return( false );
	}
	
	protected boolean 
	handleResize(
		SWTSkinObjectExpandItem2 itemResizing ) 
	{
		if ( itemResizing == null ){
			
			boolean changed = false;
			
			for ( SWTSkinObjectExpandItem2 fh : fillHeightItems ){
				
				if ( fh != itemResizing){
					
					if ( fh.resizeComposite()){
						
						changed = true;
					}
				}
			}
			
			return( changed );
			
		}else{

		
			itemResizing.resizeComposite();
			
			for ( SWTSkinObjectExpandItem2 fh : fillHeightItems ){
				
				if ( fh != itemResizing){
					
					fh.resizeComposite();
				}
			}
			
			return( true );
		}
	}

	@Override
	public void
	relayout()
	{
		super.relayout();
		
		handleResize( null );
	}
	
	protected void
	addItem(
		SWTSkinObjectExpandItem2		item )
	{
		items.add( item );
		
		if ( item.fillsHeight()){
			
			fillHeightItems.add(item);
		}
	}
	
	public SWTSkinObjectExpandItem2[]
	getChildren()
	{
		return( items.toArray( new SWTSkinObjectExpandItem2[items.size()] ));
	}
	
	protected int
	getSpacing()
	{
		return( 2 );		// header.padding.spacing
	}
}
