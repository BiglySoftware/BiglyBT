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

import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.ImageData;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.widgets.Composite;

import com.biglybt.core.config.COConfigurationManager;
import com.biglybt.core.util.Debug;
import com.biglybt.ui.swt.Utils;
import com.biglybt.ui.swt.components.CompositeMinSize;
import com.biglybt.ui.swt.shells.GCStringPrinter;

public class SWTSkinObjectExpandItem2
	extends SWTSkinObjectContainer
	implements SWTSkinObjectExpandItem
{
	static Boolean	canUseExpanderUnicode = null;
	
	private SWTSkinObjectExpandBar2		bar;
	private Composite					composite;
	
	private boolean	expanded;
	private boolean	fillHeight;
	
	private int		bodyHeightCache = -1;
	
	public 
	SWTSkinObjectExpandItem2(
		SWTSkin				skin, 
		SWTSkinProperties	properties,
		String				sID, 
		String				sConfigID, 
		SWTSkinObject		parent) 
	{
		super(skin, properties, sID, sConfigID, parent);
		
		type = "expanditem2";
				
		composite = getComposite();
		
		bar = (SWTSkinObjectExpandBar2)parent;
		
		fillHeight = properties.getBooleanValue(sConfigID + ".fillheight", false);
				
		bar.addItem( this );
		
		addListener((obj,event,params)->{
			
			if ( event == SWTSkinObjectListener.EVENT_CREATED ){
				
				String lastExpandStateID = "ui.skin." + sConfigID + ".expanded";

				if ( COConfigurationManager.hasParameter(lastExpandStateID, true )){
					
					boolean lastExpandState = COConfigurationManager.getBooleanParameter( lastExpandStateID, false);
					
					setExpanded( lastExpandState );
					
				}else{
					
					setExpanded( properties.getBooleanValue( sConfigID + ".expanded", false ));
				}

				getHeader().getControl().addListener(
					SWT.MouseUp,(ev)->{
				
						setExpanded( !getExpanded());
					});
			}
			return(null);
		});		
	}
	
	private SWTSkinObjectContainer
	getHeaderContainer()
	{
		SWTSkinObject[] kids = getChildren();
		
		return((SWTSkinObjectContainer)kids[0]);
	}
	
	private SWTSkinObjectText
	getHeader()
	{
		return(((SWTSkinObjectText)getHeaderContainer().getChildren()[0]));
	}
	
	private SWTSkinObjectContainer
	getBodyContainer()
	{
		SWTSkinObject[] kids = getChildren();
		
		return((SWTSkinObjectContainer)kids[1]);
	}
	
	private String
	fixupExpander(
		String	text )
	{
		String arrowRight	= "\u2B9E"; // " \uD83D\uDF82";
		String arrowDown	= "\u2B9F"; // "\uD83D\uDF83";
			
		if ( canUseExpanderUnicode == null ){
			
			SWTSkinObjectText header = getHeader();
			
			canUseExpanderUnicode = Utils.canDisplayCharacter( header.getControl().getFont(), arrowRight.charAt(0));
		}
		
		if ( canUseExpanderUnicode ){
			
			if ( text.startsWith( arrowRight )){
				
				text = text.substring(arrowRight.length());
				
			}else if ( text.startsWith( arrowDown )){
				
				text = text.substring(arrowDown.length());
			}
	
			return( (expanded?arrowDown:arrowRight) + " " + text.trim());
			
		}else{
			
			return( text );
		}
	}
	
	public void
	setText(
		String		text )
	{
		text = fixupExpander( text );
		
		SWTSkinObjectText header = getHeader();
		
		header.setText(text);
		
		header.relayout();
	}
	
	public Composite
	getParentComposite()
	{
		return((Composite)getParent().getControl());
	}
	
	public void
	setExpanded(
		boolean	b )
	{	
		expanded = b;
			
		SWTSkinObjectText header = getHeader();
		
		String text = header.getText();
		
		text = fixupExpander( text );
				
		header.setText(text);
		
		header.relayout();
		
		String lastExpandStateID = "ui.skin." + sConfigID + ".expanded";
		
		COConfigurationManager.setParameter( lastExpandStateID, expanded);
		
		getBodyContainer().setVisible( b );
					
		bar.handleResize( this );

		relayout();
		
		Utils.relayoutUp( getComposite() );
	}
	
	private boolean
	getExpanded()
	{
		return( expanded );
	}

	public void
	setHeight(
		int		height )
	{
			// this is a hack called by opentorrentoptions to deal with things not working for tags
		
		bar.handleResize( this );

		relayout();
		
		Utils.relayoutUp( getComposite() );
	}
	
	private void
	setBodyHeight(
		int		h )
	{			
		((CompositeMinSize)getBodyContainer().getControl()).setMinSize( new Point( SWT.DEFAULT, h ));
	}
	
	private int
	getHeaderHeight()
	{
		return(getHeaderContainer().getComposite().getClientArea().height);
	}
	
	private int
	getBodyHeight()
	{
		if ( !expanded ){
			
			return( 0 );
		}
		
		Composite bodyComposite = getBodyContainer().getComposite();
		
		int height = bodyComposite.getBounds().height;
		
		if ( height <= 0 && bodyHeightCache > 0 ){
						
			return( bodyHeightCache );
		}
		
		return( height );
	}
			
	protected boolean 
	fillsHeight() 
	{
		return( fillHeight );
	}
	
	@Override
	public String 
	switchSuffix(
		String suffix, 
		int level, 
		boolean walkUp,
	    boolean walkDown) 
	{
		suffix = super.switchSuffix(suffix, level, walkUp, walkDown);

		if ( suffix == null ){
			
			return null;
		}

		String sPrefix = sConfigID + ".text";
		
		String text = properties.getStringValue(sPrefix + suffix);
		
		if ( text != null ){
			
			setText(text);
		}

		fillHeight = properties.getBooleanValue(sConfigID + ".fillheight", false);

		return suffix;
	}
	
	protected boolean 
	resizeComposite() 
	{
		if ( composite.isDisposed() || !getExpanded()){
			
			return( false );
		}

		Rectangle clientArea = bar.getComposite().getParent().getClientArea();
				
		if ( properties.getBooleanValue(sConfigID + ".fillheight", false)){
						
			SWTSkinObjectExpandItem2[] items = bar.getChildren();
			
			int h = bar.getSpacing();
			
			for ( SWTSkinObjectExpandItem2 item : items ){
				
				h += bar.getSpacing();
				
				int hh	= item.getHeaderHeight();
				int ih	= item.getBodyHeight();
								
				h += hh;
				
				if ( this != item ){
					
					if (item.getExpanded()){
						
						h += ih;
					}
				}
			}
			
			int newHeight = clientArea.height - h;
			
			int min = properties.getIntValue(sConfigID + ".fillheightmin", 0 );

			if ( min > 0 ){
				
				if ( newHeight < min ){
					
					newHeight = min;
				}
			}
			
			if ( getBodyHeight() != newHeight){
				
				setBodyHeight( newHeight );
				
					// we only care about the return value for fillheight component
				
				return( true );
			}
		}else{
			
			if ( expanded ){
				
				Composite bodyComposite = getBodyContainer().getComposite();
				
				int height = bodyComposite.computeSize(clientArea.width, SWT.DEFAULT, true).y;
	
				if ( height > 0 ){
					
					bodyHeightCache = height;
				}
			}
		}

		return( false );
	}
}