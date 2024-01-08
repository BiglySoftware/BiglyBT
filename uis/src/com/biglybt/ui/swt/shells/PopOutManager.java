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
import org.eclipse.swt.widgets.Shell;

import com.biglybt.core.Core;
import com.biglybt.core.download.DownloadManager;
import com.biglybt.core.tag.Tag;
import com.biglybt.core.util.BDecoder;
import com.biglybt.core.util.DataSourceResolver.ExportableDataSource;
import com.biglybt.core.util.Debug;
import com.biglybt.core.util.FileUtil;
import com.biglybt.pif.PluginAdapter;
import com.biglybt.pif.download.Download;
import com.biglybt.ui.UIFunctions;
import com.biglybt.ui.UIFunctionsManager;
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
	public static final String	OPT_ON_TOP			= "on-top";
	public static final String	OPT_CAN_MINIMIZE	= "can-min";
	
	
	public static final Map<String,Object>	OPT_MAP_NONE	= new HashMap<>();
	public static final Map<String,Object>	OPT_MAP_ON_TOP	= new HashMap<>();
	
	static{
		OPT_MAP_ON_TOP.put( OPT_ON_TOP, true );
	}
	
	private static final String CONFIG_FILE = "popouts.config";

	private static final int STYLE_DEFAULT = SWT.RESIZE | SWT.MAX | SWT.DIALOG_TRIM;
	
	private static final int TYPE_DIALOG	= 0;
	private static final int TYPE_MDI_ID	= 1;
	
	private static long	next_id	= 0;
	
	private static class
	PopOutDetails
	{
		int					type;
		long				id;
		String				title;
		int					style;
		boolean				on_top;
		String				config_prefix;
		Map<String,Object>	state;
	}
	
	private static List<PopOutDetails>	popout_details = new ArrayList<>();
	
	public static void
	initialise(
		Core		core )
	{
			// wait until all plugins are initialised as they may register views and this must
			// be done before recovering any pop-outs
		
		core.getPluginManager().getDefaultPluginInterface().addListener(
			new PluginAdapter()
			{
				@Override
				public void initializationComplete()
				{
					Utils.execSWTThread( PopOutManager::loadConfig );
				}		
			});
		
		
	}
	
	private static synchronized void
	loadConfig()
	{
		boolean save_config = false;
		
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
								Number type = (Number)map.get( "type" );
								
								details.type			= type==null?TYPE_DIALOG:type.intValue();
								details.id				= ((Number)map.get( "id" )).longValue();
								details.title			= MapUtils.getMapString( map, "title", "?" );
								
								Number style = (Number)map.get( "style" );
								
								details.style			= style==null?STYLE_DEFAULT:style.intValue();
								details.on_top			= ((Number)map.get( "on_top" )).longValue()==1;
								details.config_prefix	= MapUtils.getMapString( map, "config_prefix", "?" );
								details.state			= (Map<String,Object>)map.get( "state" );
								
								next_id = Math.max( details.id+1, next_id );
								
								if ( recoverPopOut( details )){
								
									popout_details.add( details );
									
								}else{
									
									save_config = true;
								}
								
							}catch( Throwable e ){
								
								save_config = true;
								
								Debug.out( e );
							}
						}
					}
				}
			}
		}catch( Throwable e ){				
		
			save_config = true;
			
			Debug.out( e );
		}
		
		if ( save_config ){
			
			saveConfig();
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
					
					map.put( "type", details.type );
					map.put( "id", details.id );
					map.put( "title", details.title );
					map.put( "style", details.style );
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
		int						style,
		boolean					on_top,
		String					config_prefix,		
		BaseMdiEntry			entry )
	{
		Map<String,Object>	state = entry.exportStandAlone();

		registerPopOut( dialog, title, style, on_top, config_prefix, state );
	}
	
	private static synchronized void
	registerPopOut(
		SkinnedDialog			dialog,
		String					title,
		int						style,
		boolean					on_top,
		String					config_prefix,
		Map<String,Object>		state )
	{		
		long id = next_id++;
		
		PopOutDetails details = new PopOutDetails();
		
		details.type			= TYPE_DIALOG;
		details.id				= id;
		details.title			= title;
		details.style			= style;
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
	
	/**
	 * 
	 * @param details
	 * @return true if the details need to be retained
	 */
	private static boolean
	recoverPopOut(
		PopOutDetails	details )
	{
		if ( details.type == TYPE_DIALOG ){
			
			SkinnedDialog skinnedDialog =
					new SkinnedDialog(
							"skin3_dlg_sidebar_popout",
							"shell",
							details.on_top?UIFunctionsManagerSWT.getUIFunctionsSWT().getMainShell():null,
							details.style );
	
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
	
				return( true );
				
			}else{
	
				skinnedDialog.close();
			}
		}else{
			
			String mdi_id = (String)details.state.get( "mdi_id" );
			
			if ( mdi_id != null ){
				
				UIFunctions uif = UIFunctionsManager.getUIFunctions();

				if ( uif != null ){

					uif.getMDI().showEntryByID( mdi_id );
				}
			}
		}
		
		return( false );
	}
	
	public static void
	registerSideBarSection(
		Shell 		shell,
		String		mdi_id )
	{
		long id = next_id++;
		
		PopOutDetails details = new PopOutDetails();
		
		Map<String,Object>	state = new HashMap<>();
		
		state.put( "mdi_id", mdi_id );
		
		details.type			= TYPE_MDI_ID;
		details.id				= id;
		details.title			= "";
		details.on_top			= true;
		details.config_prefix	= "";
		details.state			= state;
		
		popout_details.add( details );
		
		shell.addDisposeListener((ev)->{
			if ( !Utils.isTerminated()){
			
				unregisterPopOut( id );
			}
		});
		
		saveConfig();
		
		
	}
	
	private static boolean
	getOption(
		Map<String,Object>	map,
		String				opt,
		boolean				def )
	{
		if ( map != null ){
			
			Object obj = map.get( opt );
			
			if ( obj instanceof Boolean ){
				
				return((Boolean)obj);
			}
		}
		
		return( def );
	}
	
	public static boolean
	popOut(
		SideBarEntrySWT		entry,
		Map<String,Object>	default_options )
	{
		Boolean onTop 	= null;
		Boolean canMin	= null;
		
		Object oeds = entry.getExportableDatasource();
		
		ExportableDataSource	eds = null;
		
		if ( oeds instanceof ExportableDataSource ){
			
			eds = (ExportableDataSource)oeds;
			
		}else{
			
			Object ds = entry.getDatasource();
			
			if ( ds instanceof ExportableDataSource ){
				
				eds = (ExportableDataSource)ds;
			}
		}
		
		if ( eds != null ){
			
			onTop	= eds.getBooleanOption( ExportableDataSource.OPT_ON_TOP );
			
			canMin	= eds.getBooleanOption( ExportableDataSource.OPT_CAN_MINIMIZE );
		}
		
		if ( onTop == null ){
			
			onTop = getOption( default_options, OPT_ON_TOP, false );
		}
		
		if ( canMin == null ){
			
			canMin	= getOption( default_options, OPT_CAN_MINIMIZE, false );
		}
		
		int style = STYLE_DEFAULT;
		
		if ( canMin ){
			
			style = style | SWT.MIN;
		}
		
		SkinnedDialog skinnedDialog =
				new SkinnedDialog(
						"skin3_dlg_sidebar_popout",
						"shell",
						onTop?UIFunctionsManagerSWT.getUIFunctionsSWT().getMainShell():null,
						style );
	
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
			
			registerPopOut( skinnedDialog, title, style, onTop, configPrefix, entry );

			skinnedDialog.open( configPrefix, true );
				
			return( true );
			
		}else{
	
			skinnedDialog.close();
			
			return( false );
		}
	}
	
	public static boolean
	popOut(
		TabbedEntry				entry,
		Map<String,Object>		default_options )
	{
		Boolean onTop 	= null;
		Boolean canMin	= null;
		
		Object oeds = entry.getExportableDatasource();
		
		ExportableDataSource	eds = null;
		
		if ( oeds instanceof ExportableDataSource ){
			
			eds = (ExportableDataSource)oeds;
			
		}else{
			
			Object ds = entry.getDatasource();
			
			if ( ds instanceof ExportableDataSource ){
				
				eds = (ExportableDataSource)ds;
			}
		}
		
		if ( eds != null ){
			
			onTop	= eds.getBooleanOption( ExportableDataSource.OPT_ON_TOP );
			
			canMin	= eds.getBooleanOption( ExportableDataSource.OPT_CAN_MINIMIZE );
		}
		
		if ( onTop == null ){
			
			onTop = getOption( default_options, OPT_ON_TOP, false );
		}
		
		if ( canMin == null ){
			
			canMin	= getOption( default_options, OPT_CAN_MINIMIZE, false );
		}
		
		int style = STYLE_DEFAULT;
		
		if ( canMin ){
			
			style = style | SWT.MIN;
		}
		
		SkinnedDialog skinnedDialog =
				new SkinnedDialog(
						"skin3_dlg_sidebar_popout",
						"shell",
						onTop?UIFunctionsManagerSWT.getUIFunctionsSWT().getMainShell():null,
						style );

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
			
			registerPopOut( skinnedDialog, title, style, onTop, configPrefix, entry );

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
		int style = STYLE_DEFAULT;
		
		SkinnedDialog skinnedDialog = new SkinnedDialog(
				"skin3_dlg_sidebar_popout", "shell", null, // standalone
				style );

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
		int style = STYLE_DEFAULT;
		
		SkinnedDialog skinnedDialog = new SkinnedDialog(
				"skin3_dlg_sidebar_popout", "shell", null, // standalone
				style );

		SWTSkin skin = skinnedDialog.getSkin();

		SWTSkinObjectContainer cont = entry.buildStandAlone(
				(SWTSkinObjectContainer) skin.getSkinObject("content-area"));

		if (cont != null) {

			String ds_str = "";
			Object ds = entry.getDataSource();
			
				// first try Tags (e.g for TagSettingsView)
			
			Tag[] tags = DataSourceUtils.getTags(ds);
			
			if ( tags.length > 0 ){
				
				for ( Tag tag: tags ){
					
					ds_str += (ds_str.isEmpty()?"":", ") + tag.getTagName();
					
					if ( ds_str.length() > 200 ){
						
						ds_str = ds_str.substring( 0, 200 ) + "...";
						
						break;
					}
				}
			}else{
				
				DownloadManager[] dms = DataSourceUtils.getDMs(ds);
	
				for ( DownloadManager dm: dms ){
					
					ds_str += (ds_str.isEmpty()?"":", ") + dm.getDisplayName();
					
					if ( ds_str.length() > 200 ){
						
						ds_str = ds_str.substring( 0, 200 ) + "...";
						
						break;
					}
				}
			}

			String title = entry.getTitle() + (ds_str.length() == 0 ? "" : (" - " + ds_str));
			
			skinnedDialog.setTitle(	title );

			String configPrefix = "mdi.popout:" + entry.getId();
			
			registerPopOut( skinnedDialog, title, style, false, configPrefix, entry );

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
			STYLE_DEFAULT );

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
		int style = STYLE_DEFAULT;
		
		SkinnedDialog skinnedDialog =
				new SkinnedDialog(
						"skin3_dlg_sidebar_popout",
						"shell",
						null,	// standalone
						style  );

		SWTSkin skin = skinnedDialog.getSkin();

		SWTSkinObjectContainer cont = 
			BaseMdiEntry.importStandAlone(
				(SWTSkinObjectContainer)skin.getSkinObject( "content-area" ), 
				state,
				null );

		if ( cont != null ){

			skinnedDialog.setTitle( title );

			registerPopOut( skinnedDialog, title, style, false, configPrefix, state );

			skinnedDialog.open( configPrefix, true );

		}else{

			skinnedDialog.close();
		}
	}
}
