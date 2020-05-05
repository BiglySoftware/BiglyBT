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

import java.util.Map;

import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Menu;
import org.eclipse.swt.widgets.MenuItem;
import org.eclipse.swt.widgets.ToolBar;
import org.eclipse.swt.widgets.ToolItem;

import com.biglybt.core.config.COConfigurationManager;
import com.biglybt.core.internat.MessageText;
import com.biglybt.core.util.DataSourceResolver;
import com.biglybt.ui.mdi.MultipleDocumentInterface;
import com.biglybt.ui.skin.SkinConstants;
import com.biglybt.ui.swt.Messages;
import com.biglybt.ui.swt.UIFunctionsManagerSWT;
import com.biglybt.ui.swt.UIFunctionsSWT;
import com.biglybt.ui.swt.Utils;
import com.biglybt.ui.swt.imageloader.ImageLoader;
import com.biglybt.ui.swt.mainwindow.IMainWindow;
import com.biglybt.ui.swt.mdi.BaseMDI;
import com.biglybt.ui.swt.mdi.BaseMdiEntry;
import com.biglybt.ui.swt.shells.main.MainWindow;
import com.biglybt.ui.swt.skin.SWTSkinObject;
import com.biglybt.ui.swt.views.stats.StatsView;

public class 
QuickLinksView
{
	private static SWTSkinObject	skinObject;
	
	private static ToolBar toolBar;
	
	public static void
	init(
		BaseMDI				mdi,
		SWTSkinObject		so )
	{
		skinObject = so;
		
		Composite parent = (Composite) so.getControl();

		GridLayout layout = new GridLayout();

		layout.marginWidth = layout.marginHeight = layout.marginBottom = layout.marginTop = 1;
		
		parent.setLayout(layout);

		toolBar = new ToolBar( parent, SWT.FLAT );

		toolBar.addListener( SWT.Dispose, ev->{ toolBar = null; });
		
		GridData gridData = new GridData(GridData.FILL_BOTH);
		
		toolBar.setLayoutData( gridData );

		toolBar.addListener( SWT.MenuDetect, ev->{
			Menu	menu = new Menu( toolBar );
			
			toolBar.setMenu( menu );

			MenuItem itemRemove = new org.eclipse.swt.widgets.MenuItem( menu, SWT.PUSH );
			
			Messages.setLanguageText(itemRemove, "Button.remove");
			
			Utils.setMenuItemImage(itemRemove, "delete");

			menu.addListener( SWT.Show, (e)->{
				Point loc = toolBar.toControl( toolBar.getDisplay().getCursorLocation());
				
				itemRemove.setData( toolBar.getItem( loc ));
			});
			
			itemRemove.addListener( SWT.Selection, (e)->{
								
				ToolItem ti = (ToolItem)itemRemove.getData();
				
				if ( ti != null ){
										
					ti.dispose();
					
					if ( toolBar.getItemCount() == 0 ){
					
						setVisible( false );
						
					}else{
					
						skinObject.relayout();
					
						Utils.relayoutUp( toolBar );
					}
				}
			});

		});
		
		addItem( mdi, toolBar, "image.sidebar.library", "library.name", MultipleDocumentInterface.SIDEBAR_SECTION_LIBRARY, null );
		
		addItem( mdi, toolBar, "image.sidebar.stats", "Stats.title.full", StatsView.VIEW_ID, null );

		addItem( mdi, toolBar, "image.sidebar.config2", "ConfigView.title.full", MultipleDocumentInterface.SIDEBAR_SECTION_CONFIG, null );

	}
	
	public static void
	setVisible(
		boolean		visible )
	{
		if ( visible ){
			
			COConfigurationManager.setParameter( "IconBar.enabled", true );
		}

		UIFunctionsSWT uiFunctions = UIFunctionsManagerSWT.getUIFunctionsSWT();
		
		if ( uiFunctions != null ){
			
			IMainWindow mainWindow = uiFunctions.getMainWindow();
			
			mainWindow.setVisible( MainWindow.WINDOW_ELEMENT_QUICK_LINKS, visible );
		}
	}
	
	public static boolean
	canAddItem(
		BaseMDI			mdi,
		BaseMdiEntry	entry )
	{
		if ( toolBar == null || skinObject == null ||  skinObject.isDisposed()){
			
			return( false );
		}
	
		String id = entry.getViewID();

		return( mdi.canShowEntryByID( id ));
	}
	
	public static void
	addItem(
		BaseMDI			mdi,
		BaseMdiEntry	entry )
	{
		Utils.execSWTThread(()->{
			
			if ( toolBar == null || skinObject == null ||  skinObject.isDisposed()){
				
				return;
			}
			
			String id = entry.getViewID();

			String titleID = entry.getTitleID();
			
			if ( titleID == null || titleID.isEmpty() || titleID.startsWith( "!" )){
				
				titleID = entry.getTitle();
			}
			
			if ( mdi.canShowEntryByID( id )){

				Map<String,Object> ds_map = null;
				
				Object ds = entry.getDataSource();
				
				if ( ds != null && ds != DataSourceResolver.DEFAULT_DATASOURCE ){
				
					ds_map = DataSourceResolver.exportDataSource( ds );
				}
				
				addItem( mdi, toolBar, entry.getImageLeftID(), titleID, entry.getViewID(), ds_map );
					
				if ( toolBar.getItemCount() == 1 ){
					
					setVisible( true );
				}
				
				skinObject.relayout();
				
				Utils.relayoutUp( toolBar );
			}
		});
	}
	
	private static void
	addItem(
		BaseMDI					mdi,
		ToolBar					toolBar,
		String					image_id,
		String					tt_id,
		String					mdi_id,
		Map<String,Object>		ds_map )
	{
		ToolItem item = new ToolItem( toolBar, SWT.PUSH );
		
		if ( MessageText.keyExists( tt_id )){
		
			Messages.setLanguageTooltip( item, tt_id );
			
		}else{
			
			Utils.setTT( item,  tt_id );
		}
		
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
			
			if ( ds_map == null ){
				
				mdi.showEntryByID(	mdi_id );
					
			}else{
					
				Object ds = DataSourceResolver.importDataSource( ds_map );
					
				mdi.showEntryByID(	mdi_id, ds );
			}
		});
	}
}
