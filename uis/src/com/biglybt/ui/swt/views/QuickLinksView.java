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

import java.util.*;

import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Listener;
import org.eclipse.swt.widgets.Menu;
import org.eclipse.swt.widgets.MenuItem;
import org.eclipse.swt.widgets.ToolBar;
import org.eclipse.swt.widgets.ToolItem;

import com.biglybt.core.config.COConfigurationManager;
import com.biglybt.core.internat.MessageText;
import com.biglybt.core.util.BDecoder;
import com.biglybt.core.util.BEncoder;
import com.biglybt.core.util.DataSourceResolver;
import com.biglybt.core.util.Debug;
import com.biglybt.core.util.SystemTime;
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
	private static final String CONFIG_KEY	= "quicklinks.config";
		
	private static SWTSkinObject	skinObject;
	
	private static ToolBar toolBar;
	
	private static List<QuickLinkItem>		qlItems = new ArrayList<>();
	
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

		toolBar.addListener( 
			SWT.Dispose, ev->{ 
				toolBar = null;
				qlItems.clear();
			});
		
		GridData gridData = new GridData(GridData.FILL_BOTH);
		
		toolBar.setLayoutData( gridData );

		toolBar.addListener( SWT.MenuDetect, ev->{			
				
			Menu	menu = new Menu( toolBar );
			
			toolBar.setMenu( menu );
			
			if ( toolBar.getItemCount() > 0 ){

				menu.addListener( SWT.Show, (e)->{
					Point loc = toolBar.toControl( toolBar.getDisplay().getCursorLocation());
					
					toolBar.setData( "current_qli", toolBar.getItem( loc ));
				});

				Menu popMenu = new Menu( menu.getShell(), SWT.DROP_DOWN);

				MenuItem popItem = new MenuItem( menu, SWT.CASCADE);

				Messages.setLanguageText( popItem, "label.pop.out" );

				popItem.setMenu( popMenu );
				
				MenuItem itemPopIndependent = new MenuItem( popMenu, SWT.PUSH );
				
				Messages.setLanguageText(itemPopIndependent, "menu.independent");

				MenuItem itemPopOnTop = new MenuItem( popMenu, SWT.PUSH );
				
				Messages.setLanguageText( itemPopOnTop, "menu.on.top");

				Listener popListener = (e)->{
					
					ToolItem ti = (ToolItem)toolBar.getData( "current_qli" );

					if ( ti != null ){
						
						MenuItem mi = (MenuItem)e.widget;
						
						boolean onTop = mi == itemPopOnTop;
										
						QuickLinkItem qli = (QuickLinkItem)ti.getData( "qli" );
						
						if ( qli != null ){
						
							qli.popOut( mdi, onTop );
						}
					}
				};
				
				itemPopIndependent.addListener( SWT.Selection, popListener);
				itemPopOnTop.addListener( SWT.Selection, popListener);				
				
				MenuItem itemRemove = new MenuItem( menu, SWT.PUSH );
				
				Messages.setLanguageText(itemRemove, "Button.remove");
				
				Utils.setMenuItemImage(itemRemove, "delete");
					
				itemRemove.addListener( SWT.Selection, (e)->{
									
					ToolItem ti = (ToolItem)toolBar.getData( "current_qli" );
					
					if ( ti != null ){
							
						QuickLinkItem qli = (QuickLinkItem)ti.getData( "qli" );
						
						if ( qli != null ){
							
							synchronized( qlItems ){
										
								qlItems.remove( qli );
							
								saveConfig();
							}
						}
						
						ti.dispose();
						
						if ( toolBar.getItemCount() == 0 ){
						
							setVisible( false );
							
						}else{
						
							relayout();
						}
					}
				});
				
				new MenuItem( menu, SWT.SEPARATOR );
			}
			
			MenuItem itemReset = new org.eclipse.swt.widgets.MenuItem( menu, SWT.PUSH );
			
			Messages.setLanguageText(itemReset, "Button.reset");
							
			itemReset.addListener( SWT.Selection, (e)->{
				
				synchronized( qlItems ){

					qlItems.clear();
					
					COConfigurationManager.removeParameter( CONFIG_KEY );
					
					for ( ToolItem ti: toolBar.getItems()){
						
						ti.dispose();
					}
					
					addDefaults( mdi );
				}
				
				relayout();
			});
			
			MenuItem itemHide = new org.eclipse.swt.widgets.MenuItem( menu, SWT.PUSH );
			
			Messages.setLanguageText(itemHide, "Button.bar.hide");
							
			itemHide.addListener( SWT.Selection, (e)->{
				
				setVisible( false );
			});
		});
		
		toolBar.addListener( SWT.MouseDoubleClick, ev->{	
						
			if ( toolBar.getItemCount() > 0 ){

				Point loc = toolBar.toControl( toolBar.getDisplay().getCursorLocation());
					
				ToolItem ti = toolBar.getItem( loc );
				
				if ( ti != null ){
					
					QuickLinkItem qli = (QuickLinkItem)ti.getData( "qli" );
					
					if ( qli != null ){
					
						qli.popOut( mdi, true );
					}
				}
			}
		});
		
		synchronized( qlItems ){
			
			Map<String,Object> config = COConfigurationManager.getMapParameter( CONFIG_KEY, null );
			
			if ( config == null ){
				
				addDefaults( mdi );
				
			}else{
				
				config = BDecoder.decodeStrings( BEncoder.cloneMap( config ));
				
				try{
					List<Map<String,Object>>	items = (List<Map<String,Object>>)config.get( "items" );
					
					if ( items != null ){
						
						for (Map<String,Object> item: items ){
							
							addItem( mdi, toolBar, item );
						}
					}
				}catch( Throwable e ){
					
					Debug.out( e );
				}
			}
		}
		
		if ( !COConfigurationManager.getBooleanParameter( SkinConstants.VIEWID_QUICK_LINKS + ".visible", true )){
			
			setVisible( false );
		}
	}
	
	private static void
	addDefaults(
		BaseMDI		mdi )
	{
		addDefaultItem( mdi, toolBar, MultipleDocumentInterface.SIDEBAR_SECTION_LIBRARY, "image.sidebar.library", "library.name" );
		
		addDefaultItem( mdi, toolBar, StatsView.VIEW_ID,"image.sidebar.stats", "Stats.title.full" );

		addDefaultItem( mdi, toolBar, MultipleDocumentInterface.SIDEBAR_SECTION_CONFIG, "image.sidebar.cog", "ConfigView.title.full" );
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
				
				QuickLinkItem qli = new QuickLinkItem( entry.getViewID(), entry.getImageLeftID(), titleID, ds_map );	 

				synchronized( qlItems ){
					
					qlItems.add( qli );
				
					saveConfig();
				}
				
				addItem( mdi, toolBar, qli );
										
				setVisible( true );
								
				relayout();
			}
		});
	}
	
	private static void
	addDefaultItem(
		BaseMDI					mdi,
		ToolBar					toolBar,
		String					mdi_id,
		String					image_id,
		String					tt_id )
	{
		QuickLinkItem qli = new QuickLinkItem( mdi_id, image_id, tt_id, null );	 

		synchronized( qlItems ){
			
			qlItems.add( qli );
		}

		addItem(mdi, toolBar, qli  );
	}
	
	private static void
	addItem(
		BaseMDI					mdi,
		ToolBar					toolBar,
		Map<String,Object>		map )
	{
		QuickLinkItem qli = new QuickLinkItem( map );
		
		if ( qli != null ){
			
			synchronized( qlItems ){
				
				qlItems.add( qli );
			}
	
			addItem(mdi, toolBar, qli  );
		}
	}
	
	private static void
	addItem(
		BaseMDI					mdi,
		ToolBar					toolBar,
		QuickLinkItem			qli )
	{
		String tt_id = qli.tt_id;
		
		ToolItem item = new ToolItem( toolBar, SWT.PUSH );
		
		if ( MessageText.keyExists( tt_id )){
		
			Messages.setLanguageTooltip( item, tt_id );
			
		}else{
			
			Utils.setTT( item,  tt_id );
		}
		
		ImageLoader imageLoader = ImageLoader.getInstance();
		
		Image image = imageLoader.getImage( qli.image_id );
		
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
			
			qli.show( mdi );
		});
		
		item.setData( "qli", qli );
	}
	
	private static void
	relayout()
	{
		skinObject.relayout();
		
		Utils.relayoutUp( toolBar );
	}
	
	private static void
	saveConfig()
	{
		synchronized( qlItems ){
			
			Map<String,Object> config = new HashMap<>();
			
			List<Map<String,Object>>	items = new ArrayList<>();
			
			config.put( "items", items );
			
			for ( QuickLinkItem item: qlItems ){
				
				items.add( item.export());
			}
					
			COConfigurationManager.setParameter( CONFIG_KEY, config );
			
			COConfigurationManager.save();
		}
	}
	
	private static class
	QuickLinkItem
	{
		final String					mdi_id;
		final String					image_id;
		final String					tt_id;
		final Map<String,Object>		ds_map;
		
		long	last_show		= -1;
		long	last_pop		= -1;
		
		QuickLinkItem(
			String					_mdi_id,
			String					_image_id,
			String					_tt_id,
			Map<String,Object>		_ds_map )
		{
			mdi_id		= _mdi_id;
			image_id	= _image_id;
			tt_id		= _tt_id;
			ds_map		= _ds_map;
	
		}
		
		QuickLinkItem(
			Map<String,Object>		map )
		{
			mdi_id		= (String)map.get( "mdi" );
			image_id	= (String)map.get( "image" );
			tt_id		= (String)map.get( "tt" );
			
			ds_map = (Map<String,Object>)map.get( "ds" );
		}
		
		Map<String,Object>
		export()
		{
			Map<String,Object> map = new HashMap<>();
			
			map.put( "mdi", mdi_id );
			map.put( "image", image_id );
			map.put( "tt", tt_id );
			
			if ( ds_map != null ){
				
				map.put( "ds", ds_map );
			}
			
			return( map );
		}
		
		void 
		show(
			BaseMDI		mdi )
		{
				// problem is when we double-click to pop-out we end up with a sequence of
				// 1) select
				// 2) double click
				// 3) select
			
			long	now = SystemTime.getMonotonousTime();
			
			if ( last_pop >= 0 && now - last_pop < 1000 ){
				
				return;
			}
			
			last_show = now;
			
			Utils.execSWTThreadLater( 500, ()->{
				
				long	nownow = SystemTime.getMonotonousTime();
				
				if ( last_pop > 0 && nownow - last_pop < 1000 ){
					
					return;
				}
				
				last_show = nownow;
				
				if ( ds_map == null ){
					
					mdi.showEntryByID( mdi_id, null );
						
				}else{
						
					Object ds = DataSourceResolver.importDataSource( ds_map );
						
					mdi.showEntryByID( mdi_id, ds );
				}
			});
		}
		
		void 
		popOut(
			BaseMDI		mdi,
			boolean		onTop )
		{
			long	now = SystemTime.getMonotonousTime();
			
			if ( last_pop >= 0 && now - last_pop < 500 ){
				
				return;
			}
			
			last_pop = now;
			
			if ( ds_map == null ){
				
				mdi.popoutEntryByID( mdi_id, null, onTop );
					
			}else{
					
				Object ds = DataSourceResolver.importDataSource( ds_map );
					
				mdi.popoutEntryByID( mdi_id, ds, onTop );
			}
		}
	}
}
