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

package com.biglybt.ui.swt.views.skin;


import java.util.*;
import java.util.regex.Pattern;

import com.biglybt.ui.UIFunctionsManager;
import com.biglybt.ui.common.table.*;
import com.biglybt.ui.common.table.impl.TableColumnManager;
import com.biglybt.ui.swt.columns.alltrackers.*;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Listener;
import org.eclipse.swt.widgets.Menu;
import org.eclipse.swt.widgets.MenuItem;
import org.eclipse.swt.widgets.Text;

import com.biglybt.core.CoreFactory;
import com.biglybt.core.download.DownloadManager;
import com.biglybt.core.tag.Tag;
import com.biglybt.core.tag.TagFeatureProperties;
import com.biglybt.core.tag.TagManagerFactory;
import com.biglybt.core.tag.TagType;
import com.biglybt.core.tag.TagFeatureProperties.TagProperty;
import com.biglybt.core.tracker.AllTrackersManager;
import com.biglybt.core.tracker.AllTrackersManager.AllTrackersEvent;
import com.biglybt.core.tracker.AllTrackersManager.AllTrackersListener;
import com.biglybt.core.tracker.AllTrackersManager.AllTrackersTracker;
import com.biglybt.pif.ui.UIInputReceiver;
import com.biglybt.pif.ui.UIInputReceiverListener;
import com.biglybt.pif.ui.UIPluginViewToolBarListener;
import com.biglybt.pif.ui.tables.TableColumn;
import com.biglybt.pif.ui.tables.TableColumnCreationListener;
import com.biglybt.pif.ui.toolbar.UIToolBarItem;
import com.biglybt.ui.swt.Utils;
import com.biglybt.ui.swt.views.table.TableViewSWT;
import com.biglybt.ui.swt.views.table.TableViewSWTMenuFillListener;
import com.biglybt.ui.swt.views.table.impl.TableViewFactory;
import com.biglybt.ui.swt.views.table.utils.TableColumnCreator;
import com.biglybt.ui.swt.views.tableitems.ColumnDateSizer;
import com.biglybt.ui.swt.views.utils.TagUIUtils;
import com.biglybt.core.util.Debug;
import com.biglybt.core.util.RegExUtil;
import com.biglybt.core.util.TorrentUtils;
import com.biglybt.core.util.TrackersUtil;
import com.biglybt.ui.UIFunctions;
import com.biglybt.ui.common.ToolBarItem;
import com.biglybt.ui.common.updater.UIUpdatable;
import com.biglybt.ui.swt.Messages;
import com.biglybt.ui.swt.SimpleTextEntryWindow;
import com.biglybt.ui.swt.UIFunctionsManagerSWT;
import com.biglybt.ui.swt.UIFunctionsSWT;
import com.biglybt.ui.swt.skin.SWTSkinObject;
import com.biglybt.ui.swt.skin.SWTSkinObjectTextbox;


public class SBC_AllTrackersView
	extends SkinView
	implements 	UIUpdatable, UIPluginViewToolBarListener, TableViewFilterCheck<AllTrackersTracker>,
	TableViewSWTMenuFillListener, TableSelectionListener, AllTrackersListener
{

	private static final String TABLE_NAME = "AllTrackersView";

	TableViewSWT<AllTrackersTracker> tv;

	private Text txtFilter;

	private Composite table_parent;

	private boolean columnsAdded = false;

	private boolean listener_added;

	private Object datasource;

	@Override
	public Object
	skinObjectInitialShow(
		SWTSkinObject 	skinObject,
		Object 			params)
	{
		initColumns();

		new InfoBarUtil(
			skinObject,
			"alltrackersview.infobar",
			false,
			"alltrackers.infobar",
			"alltrackers.view.infobar" )
			{
				@Override
				public boolean
				allowShow()
				{
					return( true );
				}
			};

		return( null );
	}

	protected void
	initColumns()
	{
		synchronized (SBC_AllTrackersView.class) {

			if ( columnsAdded ){

				return;
			}

			columnsAdded = true;
		}

		TableColumnManager tableManager = TableColumnManager.getInstance();

		tableManager.registerColumn(AllTrackersTracker.class, ColumnAllTrackersTracker.COLUMN_ID,
				new TableColumnCreationListener() {
					@Override
					public void tableColumnCreated(TableColumn column) {
						new ColumnAllTrackersTracker(column);
					}
				});

		tableManager.registerColumn(AllTrackersTracker.class, ColumnAllTrackersStatus.COLUMN_ID,
				new TableColumnCreationListener() {
					@Override
					public void tableColumnCreated(TableColumn column) {
						new ColumnAllTrackersStatus(column);
					}
				});

		tableManager.registerColumn(AllTrackersTracker.class,
				ColumnAllTrackersLastGoodDate.COLUMN_ID,
				new TableColumnCoreCreationListener() {
					@Override
					public TableColumnCore createTableColumnCore(
							Class<?> forDataSourceType, String tableID, String columnID) {
						return new ColumnDateSizer(AllTrackersTracker.class, columnID,
								TableColumnCreator.DATE_COLUMN_WIDTH, tableID) {
						};
					}

					@Override
					public void tableColumnCreated(TableColumn column) {
						new ColumnAllTrackersLastGoodDate(column);
					}
				});

		tableManager.registerColumn(AllTrackersTracker.class,
				ColumnAllTrackersLastBadDate.COLUMN_ID,
				new TableColumnCoreCreationListener() {
					@Override
					public TableColumnCore createTableColumnCore(
							Class<?> forDataSourceType, String tableID, String columnID) {
						return new ColumnDateSizer(AllTrackersTracker.class, columnID,
								TableColumnCreator.DATE_COLUMN_WIDTH, tableID) {
						};
					}

					@Override
					public void tableColumnCreated(TableColumn column) {
						new ColumnAllTrackersLastBadDate(column);
					}
				});
		
		tableManager.registerColumn(AllTrackersTracker.class,
				ColumnAllTrackersBadSinceDate.COLUMN_ID,
				new TableColumnCoreCreationListener() {
					@Override
					public TableColumnCore createTableColumnCore(
							Class<?> forDataSourceType, String tableID, String columnID) {
						return new ColumnDateSizer(AllTrackersTracker.class, columnID,
								TableColumnCreator.DATE_COLUMN_WIDTH, tableID) {
						};
					}

					@Override
					public void tableColumnCreated(TableColumn column) {
						new ColumnAllTrackersBadSinceDate(column);
					}
				});
		
		tableManager.registerColumn(AllTrackersTracker.class, ColumnAllTrackersConsecutiveFails.COLUMN_ID,
				new TableColumnCreationListener() {
					@Override
					public void tableColumnCreated(TableColumn column) {
						new ColumnAllTrackersConsecutiveFails(column);
					}
				});

		tableManager.setDefaultColumnNames(TABLE_NAME,
				new String[] {
					ColumnAllTrackersTracker.COLUMN_ID,
					ColumnAllTrackersStatus.COLUMN_ID,
					ColumnAllTrackersLastGoodDate.COLUMN_ID,
					ColumnAllTrackersLastBadDate.COLUMN_ID,
					ColumnAllTrackersBadSinceDate.COLUMN_ID,
					ColumnAllTrackersConsecutiveFails.COLUMN_ID,
				});

		tableManager.setDefaultSortColumnName(TABLE_NAME, ColumnAllTrackersTracker.COLUMN_ID);
	}

	@Override
	public Object
	skinObjectHidden(
		SWTSkinObject skinObject,
		Object params)
	{
		if ( tv != null ){

			tv.delete();

			tv = null;
		}

		Utils.disposeSWTObjects(new Object[] {
			table_parent,
		});

		if ( listener_added ){

			AllTrackersManager.getAllTrackers().removeListener( this );

			listener_added = false;
		}

		return super.skinObjectHidden(skinObject, params);
	}

	@Override
	public Object
	skinObjectShown(
		SWTSkinObject 	skinObject,
		Object 			params)
	{
		super.skinObjectShown( skinObject, params );

		SWTSkinObjectTextbox soFilter = (SWTSkinObjectTextbox)getSkinObject( "filterbox" );

		if ( soFilter != null ){

			txtFilter = soFilter.getTextControl();
		}

		SWTSkinObject so_list = getSkinObject( "all-trackers-list" );

		if ( so_list != null ){

			initTable((Composite)so_list.getControl());

		}else{

			System.out.println("NO all-trackers-list");

			return( null );
		}

		if ( tv == null ){

			return( null );
		}

		AllTrackersManager.getAllTrackers().addListener( this, true );

		listener_added = true;

		return( null );
	}

	@Override
	public Object
	skinObjectDestroyed(
		SWTSkinObject 	skinObject,
		Object 			params)
	{
		if ( listener_added ){

			AllTrackersManager.getAllTrackers().removeListener( this );

			listener_added = false;
		}

		return super.skinObjectDestroyed(skinObject, params);
	}


	private void
	initTable(
		Composite control )
	{
		UIFunctionsSWT uiFunctions = UIFunctionsManagerSWT.getUIFunctionsSWT();

		if ( tv == null ){

			tv = TableViewFactory.createTableViewSWT(
					AllTrackersTracker.class, TABLE_NAME, TABLE_NAME,
					new TableColumnCore[0],
					ColumnAllTrackersTracker.COLUMN_ID,
					SWT.MULTI | SWT.FULL_SELECTION | SWT.VIRTUAL);

			if ( txtFilter != null){

				tv.enableFilterCheck( txtFilter, this );
			}

			tv.setRowDefaultHeightEM(1);

			tv.setEnableTabViews(true, true, null);

			table_parent = new Composite(control, SWT.BORDER);

			table_parent.setLayoutData(Utils.getFilledFormData());

			GridLayout layout = new GridLayout();

			layout.marginHeight = layout.marginWidth = layout.verticalSpacing = layout.horizontalSpacing = 0;

			table_parent.setLayout(layout);

			tv.addMenuFillListener(this);
			tv.addSelectionListener( this, false );

			tv.initialize( table_parent );

			tv.addCountChangeListener(
				new TableCountChangeListener()
				{
					@Override
					public void
					rowRemoved(
						TableRowCore row)
					{
					}

					@Override
					public void
					rowAdded(
						TableRowCore row)
					{
						if ( datasource == row.getDataSource()){

							tv.setSelectedRows(new TableRowCore[] { row });
						}
					}
				});
		}

		control.layout( true );
	}

	@Override
	public boolean
	toolBarItemActivated(
		ToolBarItem item,
		long activationType,
		Object datasource)
	{
		if ( tv == null || !tv.isVisible()){

			return( false );
		}

		List<Object> datasources = tv.getSelectedDataSources();

		if ( datasources.size() > 0 ){

			List<AllTrackersTracker>	dms = new ArrayList<>(datasources.size());

			for ( Object o: datasources ){

				dms.add((AllTrackersTracker)o);
			}

			String id = item.getID();

			if ( id.equals("remove")) {

			}


			return true;
		}

		return false;
	}

	@Override
	public void
	refreshToolBarItems(
		Map<String, Long> list)

	{
		if ( tv == null || !tv.isVisible()){

			return;
		}

		boolean canEnable = false;

		Object[] datasources = tv.getSelectedDataSources().toArray();

		if ( datasources.length > 0 ){

			canEnable = true;
		}

		//list.put( "start", canEnable ? UIToolBarItem.STATE_ENABLED : 0);
		//list.put( "startstop", canEnable ? UIToolBarItem.STATE_ENABLED : 0);

		//list.put( "remove", canEnable ? UIToolBarItem.STATE_ENABLED : 0);
	}

	@Override
	public void
	updateUI()
	{
		if (tv != null) {

			tv.refreshTable(false);
		}
	}

	@Override
	public String
	getUpdateUIName()
	{
		return( TABLE_NAME );
	}

	@Override
	public void
	addThisColumnSubMenu(
		String 		columnName,
		Menu 		menuThisColumn )
	{
	}

	@Override
	public void
	fillMenu(
		String 	sColumnName,
		Menu 	menu )
	{
		List<Object>	ds = tv.getSelectedDataSources();

		final List<AllTrackersTracker>	trackers = new ArrayList<>(ds.size());

		for ( Object o: ds ){

			trackers.add((AllTrackersTracker)o);
		}

		boolean	hasSelection = trackers.size() > 0;

		List<Tag> all_tags = TagManagerFactory.getTagManager().getTagType( TagType.TT_DOWNLOAD_MANUAL ).getTags();
		
		List<Tag> tracker_prop_tags = new ArrayList<Tag>();
		
		Map<Tag,String>	existing_removal_templates = new TreeMap<>( TagUIUtils.getTagComparator());
		
		for ( Tag t: all_tags ){
			
			TagFeatureProperties tfp = (TagFeatureProperties)t;

			TagProperty[] props = tfp.getSupportedProperties();

			for ( TagProperty prop: props ){

				String tp_name = prop.getName( false );
				
				if ( tp_name.equals( TagFeatureProperties.PR_TRACKERS )){
					
					String[] hosts = prop.getStringList();
					
					if ( hosts != null && hosts.length > 0 ){
						
						tracker_prop_tags.add( t );
					}
				}else if ( tp_name.equals( TagFeatureProperties.PR_TRACKER_TEMPLATES )){
					
					String[] val = prop.getStringList();
					
					if ( val.length == 1 ){
						
						String[] bits = val[0].split( ":", 2 );
					
						if ( bits[0].equals( "x" )){
							
							existing_removal_templates.put( t, bits[1] );
						}
					}
				}
			}
		}
		
		MenuItem itemAddToTag = 
			TagUIUtils.createTagSelectionMenu(
				menu,
				"alltorrents.add.torrents.to.tag",
				tracker_prop_tags,
				new TagUIUtils.TagSelectionListener(){
					
					@Override
					public void selected(Tag tag){
					
						TagFeatureProperties tfp = (TagFeatureProperties)tag;

						TagProperty[] props = tfp.getSupportedProperties();

						for ( TagProperty prop: props ){

							if ( prop.getName( false ).equals( TagFeatureProperties.PR_TRACKERS )){
								
								String[] existing_hosts = prop.getStringList();
								
								Set<String>	new_hosts = new HashSet<>();
								
								if ( existing_hosts != null && existing_hosts.length > 0 ){
									
									new_hosts.addAll( Arrays.asList( existing_hosts ));
								}
								
								for ( AllTrackersTracker tracker: trackers ){
									
									String name = tracker.getTrackerName();
									
									int pos = name.indexOf( "//" );
									
									if ( pos != -1 ){
										
										name = name.substring( pos+2 );
									}
									
									new_hosts.add( name );
								}
								
								prop.setStringList( new_hosts.toArray( new String[0]));
							}
						}
					}
				});
		
		itemAddToTag.setEnabled( hasSelection );
		
			// removal menu
		
		Menu tt_menu = new Menu( menu.getShell(), SWT.DROP_DOWN);

		MenuItem tt_item = new MenuItem( menu, SWT.CASCADE);

		Messages.setLanguageText( tt_item, "alltorrents.remove.from.torrents" );

		tt_item.setMenu( tt_menu );
			
			// merge into existing
		
		Menu tt_merge_menu = new Menu( tt_menu.getShell(), SWT.DROP_DOWN);

		MenuItem tt_merge_item = new MenuItem( tt_menu, SWT.CASCADE);

		Messages.setLanguageText( tt_merge_item, "alltorrents.merge.into.existing" );

		tt_merge_item.setMenu( tt_merge_menu );
			
		if ( existing_removal_templates.isEmpty()){
			
			tt_merge_item.setEnabled( false );
			
		}else{
			
			for ( Tag tag: existing_removal_templates.keySet()){
				
				MenuItem item = new MenuItem( tt_merge_menu, SWT.PUSH);

				item.setText( tag.getTagName( true ));

				item.addListener(SWT.Selection, new Listener() {
					@Override
					public void
					handleEvent(
						Event event)
					{
						String name = existing_removal_templates.get( tag );
						
						TrackersUtil tut = TrackersUtil.getInstance();

						List<List<String>> existing_trackers = tut.getMultiTrackers().get( name );
						
						List<List<String>> new_trackers = new ArrayList<>();
						
						for ( AllTrackersTracker tracker: trackers ){
						
							List<String> l = new ArrayList<>();
							
							l.add( tracker.getTrackerName());
							
							new_trackers.add( l );
						}
						
						if ( existing_trackers != null ){							
							
							new_trackers = TorrentUtils.mergeAnnounceURLs( existing_trackers, new_trackers );
						}
						
						tut.addMultiTracker( name, new_trackers );
						
						TagFeatureProperties tfp = (TagFeatureProperties)tag;

						TagProperty[] props = tfp.getSupportedProperties();

						for ( TagProperty prop: props ){

							String tp_name = prop.getName( false );
							
							if ( tp_name.equals( TagFeatureProperties.PR_TRACKER_TEMPLATES )){
								
								prop.setStringList( new String[]{ "x:" + name });
							}
						}
						
						List<DownloadManager> dms = CoreFactory.getSingleton().getGlobalManager().getDownloadManagers();
						
						for ( DownloadManager dm: dms ){
							
							tag.addTaggable( dm );
						}
					}});
			}
		}
		
		new MenuItem( tt_menu, SWT.SEPARATOR );
		
		MenuItem tt_create_item = new MenuItem( tt_menu, SWT.PUSH);

		Messages.setLanguageText( tt_create_item, "alltorrents.merge.new" );

		tt_create_item.addListener(SWT.Selection, new Listener() {
			@Override
			public void
			handleEvent(
				Event event)
			{
				SimpleTextEntryWindow entryWindow = 
					new SimpleTextEntryWindow( "alltorrents.new.action.title", "alltorrents.new.action.msg" );
			
				entryWindow.prompt(
					new UIInputReceiverListener() {
						@Override
						public void 
						UIInputReceiverClosed(
							UIInputReceiver entryWindow ) 
						{
							if (!entryWindow.hasSubmittedInput()) {
								return;
							}
		
							String new_name = entryWindow.getSubmittedInput().trim();
						
							TrackersUtil tut = TrackersUtil.getInstance();

							Set<String> existing_names = new HashSet<>( tut.getMultiTrackers().keySet());

							for ( Tag t: all_tags ){
								
								existing_names.add( t.getTagName( true ));
							}
							
							if ( existing_names.contains( new_name )){
								
								int	num = 1;
								
								while( true ){
									
									String test = new_name + "_" + num++;
									
									if ( !existing_names.contains( test )){
										
										new_name = test;
										
										break;
									}
								}
							}
							
							TagType tt = TagManagerFactory.getTagManager().getTagType( TagType.TT_DOWNLOAD_MANUAL );
							
							try{
								Tag new_tag = tt.createTag( new_name , true);
	
								new_tag.setPublic( false );
								
								List<List<String>>	urls = new ArrayList<>();
								
								for ( AllTrackersTracker tracker: trackers ){
								
									List<String> l = new ArrayList<>();
									
									l.add( tracker.getTrackerName());
									
									urls.add( l );
								}
								
								tut.addMultiTracker( new_name, urls );
								
								TagFeatureProperties tfp = (TagFeatureProperties)new_tag;

								TagProperty[] props = tfp.getSupportedProperties();

								for ( TagProperty prop: props ){

									String tp_name = prop.getName( false );
									
									if ( tp_name.equals( TagFeatureProperties.PR_TRACKER_TEMPLATES )){
										
										prop.setStringList( new String[]{ "x:" + new_name });
									}
								}
								
								List<DownloadManager> dms = CoreFactory.getSingleton().getGlobalManager().getDownloadManagers();
								
								for ( DownloadManager dm: dms ){
									
									new_tag.addTaggable( dm );
								}
								
							}catch( Throwable e ){
								
								Debug.out( e );
							}
						}
					});
				
			}});
		
		
		
		tt_menu.setEnabled( hasSelection );
		
		new MenuItem( menu, SWT.SEPARATOR );
	}

	@Override
	public void
	selected(
		TableRowCore[] row )
	{
		UIFunctions uiFunctions = UIFunctionsManager.getUIFunctions();

		if ( uiFunctions != null ){

			uiFunctions.refreshIconBar();
		}
	}

	@Override
	public void
	deselected(
		TableRowCore[] rows )
	{
	  	UIFunctions uiFunctions = UIFunctionsManager.getUIFunctions();

	  	if ( uiFunctions != null ){

	  		uiFunctions.refreshIconBar();
	  	}
	}

	@Override
	public void
	focusChanged(
		TableRowCore focus )
	{
	  	UIFunctions uiFunctions = UIFunctionsManager.getUIFunctions();

	  	if ( uiFunctions != null ){

	  		uiFunctions.refreshIconBar();
	  	}
	}

	@Override
	public void
	defaultSelected(
		TableRowCore[] 	rows,
		int 			stateMask )
	{
	}

	@Override
	public void 
	trackerEventOccurred(
		AllTrackersEvent event)
	{
		int	type = event.getEventType();
		
		List<AllTrackersTracker>	trackers = event.getTrackers();
		
		if ( type == AllTrackersEvent.ET_TRACKER_ADDED ){
			
			tv.addDataSources( trackers.toArray( new AllTrackersTracker[0] ));
			
		}else if ( type == AllTrackersEvent.ET_TRACKER_UPDATED ){
			
			for ( AllTrackersTracker tracker: trackers ){
			
				TableRowCore row = tv.getRow( tracker  );
				
				if ( row != null ){
					
					row.invalidate( true );
					
					row.refresh( true );
					
						// need this crap to allow the sort column to pick up invisible changes and resort appropriately :(
					
					TableCellCore cell = row.getSortColumnCell( null );
					
					if ( cell != null ){
						
						cell.invalidate( true );
						
						cell.refresh( true );
					}
				}
			}
		}else{
			
			tv.removeDataSources( trackers.toArray( new AllTrackersTracker[0] ));
		}
	}

	@Override
	public void
	mouseEnter(
		TableRowCore row )
	{
	}

	@Override
	public void
	mouseExit(
		TableRowCore row)
	{
	}

	@Override
	public void
	filterSet(
		String filter)
	{
	}

	@Override
	public boolean
	filterCheck(
		AllTrackersTracker 	ds,
		String 			filter,
		boolean 		regex)
	{
		String name = ds.getTrackerName();

		String s = regex ? filter : "\\Q" + filter.replaceAll("\\s*[|;]\\s*", "\\\\E|\\\\Q") + "\\E";

		boolean	match_result = true;

		if ( regex && s.startsWith( "!" )){

			s = s.substring(1);

			match_result = false;
		}

		Pattern pattern = RegExUtil.getCachedPattern( "alltrackersview:search", s, Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE );

		return( pattern.matcher(name).find() == match_result );
	}

	@Override
	public Object
	dataSourceChanged(
		SWTSkinObject 	skinObject,
		Object 			params)
	{
		if ( params instanceof AllTrackersTracker ){

			if (tv != null) {

				TableRowCore row = tv.getRow((AllTrackersTracker) params);

				if ( row != null ){

					tv.setSelectedRows(new TableRowCore[] { row });
				}
			}
		}

		datasource = params;

		return( null );
	}
}
