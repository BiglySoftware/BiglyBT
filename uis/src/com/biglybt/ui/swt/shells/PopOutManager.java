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

package com.biglybt.ui.swt.shells;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.eclipse.swt.SWT;

import com.biglybt.core.download.DownloadManager;
import com.biglybt.core.util.BDecoder;
import com.biglybt.core.util.Debug;
import com.biglybt.core.util.FileUtil;
import com.biglybt.pif.download.Download;
import com.biglybt.ui.mdi.MultipleDocumentInterface;
import com.biglybt.ui.swt.UIFunctionsManagerSWT;
import com.biglybt.ui.swt.Utils;
import com.biglybt.ui.swt.mdi.BaseMdiEntry;
import com.biglybt.ui.swt.mdi.TabbedEntry;
import com.biglybt.ui.swt.pif.UISWTView;
import com.biglybt.ui.swt.pifimpl.UISWTViewBuilderCore;
import com.biglybt.ui.swt.pifimpl.UISWTViewCore;
import com.biglybt.ui.swt.skin.SWTSkin;
import com.biglybt.ui.swt.skin.SWTSkinFactory;
import com.biglybt.ui.swt.skin.SWTSkinObjectContainer;
import com.biglybt.ui.swt.views.skin.SkinnedDialog;
import com.biglybt.ui.swt.views.skin.sidebar.SideBarEntrySWT;
import com.biglybt.util.DataSourceUtils;
import com.biglybt.util.MapUtils;

public class 
PopOutManager
{
	private static final String CONFIG_FILE = "popouts.config";

	private static long	next_id	= 0;
	
	private static class
	PopOutDetails
	{
		long				id;
		String				title;
		boolean				on_top;
		String				config_prefix;
		Map<String,Object>	state;
	}
	
	private static List<PopOutDetails>	popout_details = new ArrayList<>();
	
	public static void
	initialise()
	{
		Utils.execSWTThread( PopOutManager::loadConfig );
	}
	
	private static synchronized void
	loadConfig()
	{
		try{
			if ( FileUtil.resilientConfigFileExists( CONFIG_FILE )){
	
				Map<String,Object>	popouts_map = FileUtil.readResilientConfigFile( CONFIG_FILE );
				
				popouts_map = BDecoder.decodeStrings( popouts_map );
				
				if ( popouts_map != null ){
					
					List<Map<String,Object>>	popout_list = (List<Map<String,Object>>)popouts_map.get( "popouts" );
					
					if ( popout_list != null ){
						
						for ( Map<String,Object> map: popout_list ){
							
							PopOutDetails details = new PopOutDetails();
							
							try{
								details.id				= ((Number)map.get( "id" )).longValue();
								details.title			= MapUtils.getMapString( map, "title", "?" );
								details.on_top			= ((Number)map.get( "on_top" )).longValue()==1;
								details.config_prefix	= MapUtils.getMapString( map, "config_prefix", "?" );
								details.state			= (Map<String,Object>)map.get( "state" );
								
								next_id = Math.max( details.id+1, next_id );
								
								recoverPopOut( details );
								
								popout_details.add( details );
								
							}catch( Throwable e ){
								
								Debug.out( e );
							}
						}
					}
				}
			}
		}catch( Throwable e ){				
		
			Debug.out( e );
		}		
	}
	
	private static void
	saveConfig()
	{
		if ( popout_details.isEmpty()){
			
			FileUtil.deleteResilientConfigFile( CONFIG_FILE );
				
		}else{
			
			Map<String,Object>	popouts_map = new HashMap<>();
			
			List<Map<String,Object>> popout_list = new ArrayList<>();
			
			popouts_map.put( "popouts", popout_list);

			for ( PopOutDetails details: popout_details ){
				
				try{
					Map<String,Object> map = new HashMap<>();
					
					map.put( "id", details.id );
					map.put( "title", details.title );
					map.put( "on_top", details.on_top?1:0 );
					map.put( "config_prefix", details.config_prefix );
					map.put( "state", details.state );
					
					popout_list.add( map );
					
				}catch( Throwable e ){
					
					Debug.out( e );
				}
			}
			
			FileUtil.writeResilientConfigFile( CONFIG_FILE, popouts_map );
		}
	}
	
	private static void
	registerPopOut(
		SkinnedDialog			dialog,
		String					title,
		boolean					on_top,
		String					config_prefix,		
		BaseMdiEntry			entry )
	{
		Map<String,Object>	state = entry.exportStandAlone();

		registerPopOut( dialog, title, on_top, config_prefix, state );
	}
	
	private static synchronized void
	registerPopOut(
		SkinnedDialog			dialog,
		String					title,
		boolean					on_top,
		String					config_prefix,
		Map<String,Object>		state )
	{		
		long id = next_id++;
		
		PopOutDetails details = new PopOutDetails();
		
		details.id				= id;
		details.title			= title;
		details.on_top			= on_top;
		details.config_prefix	= config_prefix;
		details.state			= state;
		
		popout_details.add( details );
		
		dialog.addCloseListener((d)->{
		
			unregisterPopOut( id );
		});
		
		saveConfig();
	}
	
	private static synchronized void
	unregisterPopOut(
		long	id )
	{
		Iterator<PopOutDetails> it = popout_details.iterator();
		
		while( it.hasNext()){
			
			PopOutDetails details = it.next();
			
			if ( details.id == id ){
				
				it.remove();
				
				break;
			}
		}
		
		saveConfig();
	}
	
	private static void
	recoverPopOut(
		PopOutDetails	details )
	{
		SkinnedDialog skinnedDialog =
				new SkinnedDialog(
						"skin3_dlg_sidebar_popout",
						"shell",
						details.on_top?UIFunctionsManagerSWT.getUIFunctionsSWT().getMainShell():null,
						SWT.RESIZE | SWT.MAX | SWT.DIALOG_TRIM);

		SWTSkin skin = skinnedDialog.getSkin();

		SWTSkinObjectContainer cont = 
			BaseMdiEntry.importStandAlone(
				(SWTSkinObjectContainer)skin.getSkinObject( "content-area" ), 
				details.state,
				null );

		if ( cont != null ){

			skinnedDialog.setTitle( details.title );

			skinnedDialog.addCloseListener((d)->{
				
				unregisterPopOut( details.id );
			});
			
			skinnedDialog.open( details.config_prefix, true );

		}else{

			skinnedDialog.close();
		}
	}
	
	public static boolean
	popOut(
		SideBarEntrySWT		entry,
		boolean				onTop )
	{
		SkinnedDialog skinnedDialog =
				new SkinnedDialog(
						"skin3_dlg_sidebar_popout",
						"shell",
						onTop?UIFunctionsManagerSWT.getUIFunctionsSWT().getMainShell():null,
						SWT.RESIZE | SWT.MAX | SWT.DIALOG_TRIM);
	
		SWTSkin skin = skinnedDialog.getSkin();
	
		SWTSkinObjectContainer cont = entry.buildStandAlone((SWTSkinObjectContainer)skin.getSkinObject( "content-area" ));
	
		if ( cont != null ){
	
			String title = entry.getTitle();
			
			skinnedDialog.setTitle( title );
	
			String metrics_id;
			
				// hack - we don't want to remember shell metrics on a per-download basis
			
			if ( entry.getDatasource() instanceof Download ){
				
				metrics_id = MultipleDocumentInterface.SIDEBAR_SECTION_TORRENT_DETAILS;
				
			}else{
				
				metrics_id = entry.getId();
			}
	
			String configPrefix = "mdi.popout:" + metrics_id;
			
			registerPopOut( skinnedDialog, title, onTop, configPrefix, entry );

			skinnedDialog.open( configPrefix, true );
				
			return( true );
			
		}else{
	
			skinnedDialog.close();
			
			return( false );
		}
	}
	
	public static boolean
	popOut(
		TabbedEntry		entry,
		boolean			onTop )
	{
		SkinnedDialog skinnedDialog =
				new SkinnedDialog(
						"skin3_dlg_sidebar_popout",
						"shell",
						onTop?UIFunctionsManagerSWT.getUIFunctionsSWT().getMainShell():null,
						SWT.RESIZE | SWT.MAX | SWT.DIALOG_TRIM);

		SWTSkin skin = skinnedDialog.getSkin();

		SWTSkinObjectContainer cont = entry.buildStandAlone((SWTSkinObjectContainer)skin.getSkinObject( "content-area" ));

		if ( cont != null ){

			String ds_str = "";
			
			Object ds = entry.getDatasourceCore();
			
			DownloadManager dm = DataSourceUtils.getDM(ds);

			if (dm != null) {
				
				ds_str = dm.getDisplayName();
			}

			String title = entry.getTitle() + (ds_str.length()==0?"":(" - " + ds_str ));
			
			skinnedDialog.setTitle( title );

			String metrics_id;
			
				// hack - we don't want to remember shell metrics on a per-download basis
			
			if ( entry.getDatasource() instanceof Download ){
				
				metrics_id = MultipleDocumentInterface.SIDEBAR_SECTION_TORRENT_DETAILS;
				
			}else{
				
				metrics_id = entry.getId();
			}
		
			String configPrefix = "mdi.popout:" + metrics_id;
			
			registerPopOut( skinnedDialog, title, onTop, configPrefix, entry );

			skinnedDialog.open( configPrefix, true );

			return( true );
			
		}else{

			skinnedDialog.close();
			
			return( false );
		}
	}
	
	public static void
	popOut(
		UISWTView		parentView,
		TabbedEntry		entry )
	{
		SkinnedDialog skinnedDialog = new SkinnedDialog(
				"skin3_dlg_sidebar_popout", "shell", null, // standalone
				SWT.RESIZE | SWT.MAX | SWT.DIALOG_TRIM);

		SWTSkin skin = skinnedDialog.getSkin();

		SWTSkinObjectContainer cont = ((UISWTViewCore) parentView).buildStandAlone(
				(SWTSkinObjectContainer) skin.getSkinObject("content-area"));

		if (cont != null) {

			String ds_str = "";
			
			Object ds = parentView.getDataSource();
			
			DownloadManager dm = DataSourceUtils.getDM(ds);

			if (dm != null) {
				
				ds_str = dm.getDisplayName();
			}

			skinnedDialog.setTitle(((UISWTViewCore) parentView).getFullTitle()
					+ (ds_str.length() == 0 ? "" : (" - " + ds_str)));

			// we don't have an export for this yet - this is a whole tabbed mdi pop-out such 
			// as the download details view

			skinnedDialog.open( "mdi.popout:" + entry.getId(), true );

		} else {

			skinnedDialog.close();
		}
	}
	
	public static void
	popOut(
		TabbedEntry	entry )
	{
		SkinnedDialog skinnedDialog = new SkinnedDialog(
				"skin3_dlg_sidebar_popout", "shell", null, // standalone
				SWT.RESIZE | SWT.MAX | SWT.DIALOG_TRIM);

		SWTSkin skin = skinnedDialog.getSkin();

		SWTSkinObjectContainer cont = entry.buildStandAlone(
				(SWTSkinObjectContainer) skin.getSkinObject("content-area"));

		if (cont != null) {

			String ds_str = "";
			Object ds = entry.getDataSource();
			DownloadManager[] dms = DataSourceUtils.getDMs(ds);

			for ( DownloadManager dm: dms ){
				
				ds_str += (ds_str.isEmpty()?"":", ") + dm.getDisplayName();
				
				if ( ds_str.length() > 200 ){
					
					ds_str = ds_str.substring( 0, 200 ) + "...";
					
					break;
				}
			}

			String title = entry.getTitle() + (ds_str.length() == 0 ? "" : (" - " + ds_str));
			
			skinnedDialog.setTitle(	title );

			String configPrefix = "mdi.popout:" + entry.getId();
			
			registerPopOut( skinnedDialog, title, false, configPrefix, entry );

			skinnedDialog.open( configPrefix, true );

		} else {

			skinnedDialog.close();
		}
	}
	
	public static SkinnedDialog 
	buildSkinnedDialog(
		String id, Object ds,
		UISWTViewBuilderCore builder) 
	{
		SkinnedDialog skinnedDialog = new SkinnedDialog("skin3_dlg_sidebar_popout",
			"shell", null, // standalone
			SWT.RESIZE | SWT.MAX | SWT.DIALOG_TRIM);

		SWTSkin skin = skinnedDialog.getSkin();

		SWTSkinObjectContainer so = (SWTSkinObjectContainer) skin.getSkinObject(
			"content-area");
		SWTSkinObjectContainer cont = BaseMdiEntry.buildStandAlone(so, null,
			SWTSkinFactory.getInstance(), id, ds, 0, builder);

		if (cont == null) {
			skinnedDialog.close();
			return null;
		}

		return skinnedDialog;
	}

	public static void
	popOutStandAlone(
		String						title,
		Map<String,Object>			state,
		String						configPrefix )
	{
		SkinnedDialog skinnedDialog =
				new SkinnedDialog(
						"skin3_dlg_sidebar_popout",
						"shell",
						null,	// standalone
						SWT.RESIZE | SWT.MAX | SWT.DIALOG_TRIM);

		SWTSkin skin = skinnedDialog.getSkin();

		SWTSkinObjectContainer cont = 
			BaseMdiEntry.importStandAlone(
				(SWTSkinObjectContainer)skin.getSkinObject( "content-area" ), 
				state,
				null );

		if ( cont != null ){

			skinnedDialog.setTitle( title );

			registerPopOut( skinnedDialog, title, false, configPrefix, state );

			skinnedDialog.open( configPrefix, true );

		}else{

			skinnedDialog.close();
		}
	}
}
