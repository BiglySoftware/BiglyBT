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

package com.biglybt.ui.swt.views;

import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.ToolBar;
import org.eclipse.swt.widgets.ToolItem;

import com.biglybt.ui.UIFunctions;
import com.biglybt.ui.UIFunctionsManager;
import com.biglybt.ui.mdi.MultipleDocumentInterface;
import com.biglybt.ui.swt.Messages;
import com.biglybt.ui.swt.imageloader.ImageLoader;
import com.biglybt.ui.swt.skin.SWTSkinObject;
import com.biglybt.ui.swt.views.stats.StatsView;

public class 
QuickLinksView
{
	public static void
	init(
		SWTSkinObject		so )
	{
		Composite parent = (Composite) so.getControl();

		GridLayout layout = new GridLayout();

		layout.marginWidth = layout.marginHeight = layout.marginBottom = layout.marginTop = 1;
		
		parent.setLayout(layout);

		ToolBar toolBar = new ToolBar( parent, SWT.FLAT );

		GridData gridData = new GridData(GridData.FILL_BOTH);
		
		toolBar.setLayoutData( gridData );

		toolBar.addListener( SWT.MenuDetect, ev->{
			toolBar.setMenu( null );
		});
		
		addItem( toolBar, "image.sidebar.library", "library.name", MultipleDocumentInterface.SIDEBAR_SECTION_LIBRARY );
		
		addItem( toolBar, "image.sidebar.stats", "Stats.title.full", StatsView.VIEW_ID );

		addItem( toolBar, "image.sidebar.config2", "ConfigView.title.full", MultipleDocumentInterface.SIDEBAR_SECTION_CONFIG );

	}
	
	private static void
	addItem(
		ToolBar		toolBar,
		String		image_id,
		String		tt_id,
		String		mdi_id )
	{
		ToolItem item = new ToolItem( toolBar, SWT.PUSH );
		
		Messages.setLanguageTooltip( item, tt_id );
		
		ImageLoader imageLoader = ImageLoader.getInstance();
		
		Image image = imageLoader.getImage( image_id );
		
		Image resized = imageLoader.resizeImageIfLarger(image, new Point( 15, 15 ));
		
		if ( resized == null ){
		
			item.setImage( image);
			
		}else{
			
			item.setImage( resized );
			
			item.addListener( SWT.Dispose, (ev)->{
				
				resized.dispose();
			});
		}
		
	
		item.addListener( SWT.Selection, ev->{
			
			UIFunctions uif = UIFunctionsManager.getUIFunctions();

			if ( uif != null ){

				uif.getMDI().showEntryByID(
						mdi_id );
						
			}
		});
	}
}
