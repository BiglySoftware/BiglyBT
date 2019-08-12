/*
 * Created on Feb 24, 2009
 *
 * Copyright 2008 Vuze, Inc.  All rights reserved.
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; version 2 of the License only.
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

package com.biglybt.ui.swt.subscriptions;


import java.util.*;
import java.util.List;

import org.eclipse.swt.SWT;
import org.eclipse.swt.events.*;
import org.eclipse.swt.layout.*;
import org.eclipse.swt.widgets.*;

import com.biglybt.core.Core;
import com.biglybt.core.CoreFactory;
import com.biglybt.core.CoreRunningListener;
import com.biglybt.core.config.COConfigurationManager;
import com.biglybt.core.internat.MessageText;
import com.biglybt.core.subs.Subscription;
import com.biglybt.core.subs.SubscriptionDownloadListener;
import com.biglybt.core.subs.SubscriptionException;
import com.biglybt.core.subs.SubscriptionListener;
import com.biglybt.core.subs.SubscriptionResult;
import com.biglybt.core.subs.SubscriptionResultFilter;
import com.biglybt.core.util.*;
import com.biglybt.pif.ui.UIPluginViewToolBarListener;
import com.biglybt.pif.ui.tables.TableColumn;
import com.biglybt.pif.ui.tables.TableColumnCreationListener;
import com.biglybt.pif.ui.toolbar.UIToolBarItem;
import com.biglybt.ui.UIFunctions;
import com.biglybt.ui.UIFunctionsManager;
import com.biglybt.ui.common.ToolBarItem;
import com.biglybt.ui.common.table.*;
import com.biglybt.ui.common.table.impl.TableColumnManager;
import com.biglybt.ui.common.updater.UIUpdatable;
import com.biglybt.ui.common.util.MenuItemManager;
import com.biglybt.ui.mdi.MdiEntry;
import com.biglybt.ui.selectedcontent.DownloadUrlInfo;
import com.biglybt.ui.selectedcontent.ISelectedContent;
import com.biglybt.ui.selectedcontent.SelectedContent;
import com.biglybt.ui.selectedcontent.SelectedContentManager;
import com.biglybt.ui.swt.MenuBuildUtils;
import com.biglybt.ui.swt.UIFunctionsManagerSWT;
import com.biglybt.ui.swt.Utils;
import com.biglybt.ui.swt.columns.searchsubs.*;
import com.biglybt.ui.swt.columns.subscriptions.ColumnSubResultNew;
import com.biglybt.ui.swt.imageloader.ImageLoader;
import com.biglybt.ui.swt.mainwindow.ClipboardCopy;
import com.biglybt.ui.swt.mdi.MultipleDocumentInterfaceSWT;
import com.biglybt.ui.swt.search.SBC_SearchResultsView;
import com.biglybt.ui.swt.skin.*;
import com.biglybt.ui.swt.utils.SearchSubsUtils;
import com.biglybt.ui.swt.views.skin.SkinView;
import com.biglybt.ui.swt.views.table.TableViewSWT;
import com.biglybt.ui.swt.views.table.TableViewSWTMenuFillListener;
import com.biglybt.ui.swt.views.table.impl.TableViewFactory;

public class
SBC_SubscriptionResultsView
	extends SkinView
	implements UIUpdatable, UIPluginViewToolBarListener, TableViewFilterCheck<SBC_SubscriptionResult>, SubscriptionListener
{
	public static final String TABLE_SR = "SubscriptionResults";

	private static boolean columnsAdded = false;

	private TableViewSWT<SBC_SubscriptionResult> tv_subs_results;

	private MdiEntry mdi_entry;
	private Composite			table_parent;


	private Text txtFilter;

	private final Object filter_lock = new Object();

	private int minSize;
	private int maxSize;
	private int minSeeds;
	
	private String[]	with_keywords 		= {};
	private String[]	without_keywords 	= {};

	private FrequencyLimitedDispatcher	refilter_dispatcher =
			new FrequencyLimitedDispatcher(
				new AERunnable() {

					@Override
					public void runSupport()
					{
						refilter();
					}
				}, 250 );

	private Subscription	 ds;

	private List<SBC_SubscriptionResult>	last_selected_content = new ArrayList<>();

	public
	SBC_SubscriptionResultsView()
	{
	}

	@Override
	public Object
	skinObjectInitialShow(
            SWTSkinObject skinObject, Object params )
	{
		CoreFactory.addCoreRunningListener(
			new CoreRunningListener()
			{
				@Override
				public void
				coreRunning(
					Core core )
				{
					initColumns( core );
				}
			});

		MultipleDocumentInterfaceSWT mdi = UIFunctionsManagerSWT.getUIFunctionsSWT().getMDISWT();

		if ( mdi != null && ds != null ){

			String mdi_key = "Subscription_" + ByteFormatter.encodeString(ds.getPublicKey());

			mdi_entry = mdi.getEntry( mdi_key );

			if ( mdi_entry != null ){

				mdi_entry.addToolbarEnabler(this);
			}
		}
				
		if ( ds != null ){

			if (( ds.getViewOptions() & Subscription.VO_HIDE_HEADER) != 0 ){
				
				SWTSkinObject top_area = getSkinObject( "toparea" );

				top_area.setVisible( false );
			}
			
			SWTSkinObjectText title = (SWTSkinObjectText) getSkinObject("title");

			if ( title != null ){

				title.setText( MessageText.getString( "subs.results.view.title", new String[]{ ds.getName() }));

				Control control = title.getControl();

				final Menu menu = new Menu( control );

				control.setMenu( menu );

				final String menu_key = SubscriptionMDIEntry.setupMenus( ds, null );

				menu.addMenuListener(
					new MenuListener() {

						@Override
						public void menuShown(MenuEvent e) {
							for ( MenuItem mi: menu.getItems()){
								mi.dispose();
							}

							com.biglybt.pif.ui.menus.MenuItem[] menu_items = MenuItemManager.getInstance().getAllAsArray( menu_key );

							MenuBuildUtils.addPluginMenuItems(menu_items, menu, true, true,
									new MenuBuildUtils.MenuItemPluginMenuControllerImpl(new Object[]{ ds }));
						}

						@Override
						public void menuHidden(MenuEvent e) {
							// TODO Auto-generated method stub

						}
					});
			}
		}

		SWTSkinObjectTextbox soFilterBox = (SWTSkinObjectTextbox) getSkinObject("filterbox");
		if (soFilterBox != null) {
			txtFilter = soFilterBox.getTextControl();
		}

		final SWTSkinObject soFilterArea = getSkinObject("filterarea");
		if (soFilterArea != null) {

			SWTSkinObjectToggle soFilterButton = (SWTSkinObjectToggle) getSkinObject("filter-button");
			if (soFilterButton != null) {
				boolean toggled = COConfigurationManager.getBooleanParameter( "Subscription View Filter Options Expanded", false );

				if ( toggled ){

					soFilterButton.setToggled( true );

					soFilterArea.setVisible( true );
				}

				soFilterButton.addSelectionListener(new SWTSkinToggleListener() {
					@Override
					public void toggleChanged(SWTSkinObjectToggle so, boolean toggled) {

						COConfigurationManager.setParameter( "Subscription View Filter Options Expanded", toggled );

						soFilterArea.setVisible(toggled);
						Utils.relayout(soFilterArea.getControl().getParent());
					}
				});			}

			Composite parent = (Composite) soFilterArea.getControl();

			Label label;
			FormData fd;
			GridLayout layout;
			int sepHeight = 20;

			Composite cFilters = new Composite(parent, SWT.NONE);
			fd = Utils.getFilledFormData();
			cFilters.setLayoutData(fd);

			layout = new GridLayout( 1, true );
			layout.marginBottom = layout.marginTop = layout.marginLeft = layout.marginRight = 0;
			layout.marginWidth = layout.marginHeight = 0;

			cFilters.setLayout( layout );

			SubscriptionResultFilter filters = null;

			Runnable pFilterUpdater = null;

			if ( ds != null && ds.isUpdateable()){

				try{
					filters = ds.getFilters();

					Composite pFilters = new Composite(cFilters, SWT.NONE);
					pFilters.setLayoutData(new GridData( GridData.FILL_HORIZONTAL ));

					layout = new GridLayout( 1, false );
					layout.marginBottom = layout.marginTop = layout.marginLeft = layout.marginRight = 0;
					layout.marginWidth = layout.marginHeight = 0;

					pFilters.setLayout( layout );

					final Label pflabel = new Label( pFilters, SWT.NONE );
					pflabel.setLayoutData(new GridData( GridData.FILL_HORIZONTAL ));

					final SubscriptionResultFilter f_filters = filters;

					with_keywords 		= filters.getWithWords();
					without_keywords	= filters.getWithoutWords();
										
					pFilterUpdater = new Runnable()
					{
						boolean first = true;
					
						@Override
						public void
						run()
						{
							long kInB = DisplayFormatters.getKinB();
							long mInB = kInB*kInB;

							long	min_size = Math.max( 0,  f_filters.getMinSze()/mInB );
							long	max_size = Math.max( 0,  f_filters.getMaxSize()/mInB );
							long	min_seeds = Math.max( 0,  f_filters.getMinSeeds());

							if ( first ){
								
								minSize = (int)min_size;
								maxSize	= (int)max_size;
								minSeeds = (int)min_seeds;
							}
							
							pflabel.setText(
								MessageText.getString(
									"subs.persistent.filters",
									new String[]{
										getString( f_filters.getWithWords()),
										getString( f_filters.getWithoutWords()),
										String.valueOf( min_size ),
										String.valueOf( max_size )
									
									}) +
								", " + MessageText.getString( "label.min.seeds") + " = " + ( min_seeds ));
						}
					};

					pFilterUpdater.run();

				}catch( Throwable e ){

					Debug.out( e );
				}
			}

			Composite vFilters = new Composite(cFilters, SWT.NONE);
			vFilters.setLayoutData(new GridData( GridData.FILL_HORIZONTAL ));


			RowLayout rowLayout = new RowLayout(SWT.HORIZONTAL);
			rowLayout.spacing = 5;
			rowLayout.marginBottom = rowLayout.marginTop = rowLayout.marginLeft = rowLayout.marginRight = 0;
			rowLayout.center = true;
			vFilters.setLayout(rowLayout);


			// with/without keywords

			ImageLoader imageLoader = ImageLoader.getInstance();

			for ( int i=0;i<2;i++){

				final boolean with = i == 0;

				if ( !with ){

					label = new Label(vFilters, SWT.VERTICAL | SWT.SEPARATOR);
					label.setLayoutData(new RowData(-1, sepHeight));
				}

				Composite cWithKW = new Composite(vFilters, SWT.NONE);
				layout = new GridLayout(2, false);
				layout.marginWidth = 0;
				layout.marginBottom = layout.marginTop = layout.marginLeft = layout.marginRight = 0;
				cWithKW.setLayout(layout);
				//Label lblWithKW = new Label(cWithKW, SWT.NONE);
				//lblWithKW.setText(MessageText.getString(with?"SubscriptionResults.filter.with.words":"SubscriptionResults.filter.without.words"));
				Label lblWithKWImg = new Label(cWithKW, SWT.NONE);
				lblWithKWImg.setImage( imageLoader.getImage( with?"icon_filter_plus":"icon_filter_minus"));

				final Text textWithKW = new Text(cWithKW, SWT.BORDER);
				textWithKW.setMessage(MessageText.getString(with?"SubscriptionResults.filter.with.words":"SubscriptionResults.filter.without.words"));
				GridData gd = new GridData();
				gd.widthHint = 100;
				textWithKW.setLayoutData( gd );
				textWithKW.addModifyListener(
					new ModifyListener() {

						@Override
						public void modifyText(ModifyEvent e) {
							String text = textWithKW.getText().toLowerCase( Locale.US );
							String[] bits = text.split( "\\s+");

							Set<String>	temp = new HashSet<>();

							for ( String bit: bits ){

								bit = bit.trim();
								if ( bit.length() > 0 ){
									temp.add( bit );
								}
							}

							String[] words = temp.toArray( new String[temp.size()] );
							synchronized( filter_lock ){
								if ( with ){
									with_keywords = words;
								}else{
									without_keywords = words;
								}
							}
							refilter_dispatcher.dispatch();
						}
					});
			}

			int kinb = DisplayFormatters.getKinB();
			
				// min size

			label = new Label(vFilters, SWT.VERTICAL | SWT.SEPARATOR);
			label.setLayoutData(new RowData(-1, sepHeight));

			Composite cMinSize = new Composite(vFilters, SWT.NONE);
			layout = new GridLayout(2, false);
			layout.marginWidth = 0;
			layout.marginBottom = layout.marginTop = layout.marginLeft = layout.marginRight = 0;
			cMinSize.setLayout(layout);
			Label lblMinSize = new Label(cMinSize, SWT.NONE);
			lblMinSize.setText(MessageText.getString("SubscriptionResults.filter.min_size"));
			Spinner spinMinSize = new Spinner(cMinSize, SWT.BORDER);
			spinMinSize.setMinimum(0);
			spinMinSize.setMaximum(100*kinb*kinb);	// 100 TB should do...
			spinMinSize.setSelection(minSize);
			spinMinSize.addListener(SWT.Selection, new Listener() {
				@Override
				public void handleEvent(Event event) {
					minSize = ((Spinner) event.widget).getSelection();
					refilter_dispatcher.dispatch();
				}
			});

			// max size

			label = new Label(vFilters, SWT.VERTICAL | SWT.SEPARATOR);
			label.setLayoutData(new RowData(-1, sepHeight));

			Composite cMaxSize = new Composite(vFilters, SWT.NONE);
			layout = new GridLayout(2, false);
			layout.marginWidth = 0;
			layout.marginBottom = layout.marginTop = layout.marginLeft = layout.marginRight = 0;
			cMaxSize.setLayout(layout);
			Label lblMaxSize = new Label(cMaxSize, SWT.NONE);
			lblMaxSize.setText(MessageText.getString("SubscriptionResults.filter.max_size"));
			Spinner spinMaxSize = new Spinner(cMaxSize, SWT.BORDER);
			spinMaxSize.setMinimum(0);
			spinMaxSize.setMaximum(100*kinb*kinb);	// 100 TB should do...
			spinMaxSize.setSelection(maxSize);
			spinMaxSize.addListener(SWT.Selection, new Listener() {
				@Override
				public void handleEvent(Event event) {
					maxSize = ((Spinner) event.widget).getSelection();
					refilter_dispatcher.dispatch();
				}
			});

				// min seeds
			
			label = new Label(vFilters, SWT.VERTICAL | SWT.SEPARATOR);
			label.setLayoutData(new RowData(-1, sepHeight));

			Composite cMinSeeds = new Composite(vFilters, SWT.NONE);
			layout = new GridLayout(2, false);
			layout.marginWidth = 0;
			layout.marginBottom = layout.marginTop = layout.marginLeft = layout.marginRight = 0;
			cMinSeeds.setLayout(layout);
			Label lblMinSeeds = new Label(cMinSeeds, SWT.NONE);
			lblMinSeeds.setText(MessageText.getString("label.min.seeds"));
			Spinner spinMinSeeds = new Spinner(cMinSeeds, SWT.BORDER);
			spinMinSeeds.setMinimum(0);
			spinMinSeeds.setSelection(minSeeds);
			spinMinSeeds.addListener(SWT.Selection, new Listener() {
				@Override
				public void handleEvent(Event event) {
					minSeeds = ((Spinner) event.widget).getSelection();
					refilter_dispatcher.dispatch();
				}
			});

			
			if ( filters != null ){

				label = new Label(vFilters, SWT.VERTICAL | SWT.SEPARATOR);
				label.setLayoutData(new RowData(-1, sepHeight));

				final SubscriptionResultFilter 	f_filters 			= filters;
				final Runnable					f_pFilterUpdater 	= pFilterUpdater;

				Button save = new Button( vFilters,SWT.PUSH );
				save.setText( MessageText.getString( "ConfigView.button.save" ));
				save.addListener(SWT.Selection, new Listener() {
					@Override
					public void handleEvent(Event event) {

						try{
							long kInB = DisplayFormatters.getKinB();
							long mInB = kInB*kInB;

							f_filters.update(
								with_keywords, without_keywords,
								minSize*mInB, maxSize*mInB,
								minSeeds );

							f_pFilterUpdater.run();

						}catch( Throwable e ){

							Debug.out( e );
						}
					}
				});
				
				label = new Label(vFilters, SWT.VERTICAL | SWT.SEPARATOR);
				label.setLayoutData(new RowData(-1, sepHeight));
				
				Button more = new Button( vFilters,SWT.PUSH );
				more.setText( MessageText.getString( "Subscription.menu.forcecheck" ));
				more.addListener(SWT.Selection, new Listener() {
					@Override
					public void handleEvent(Event event) {

						try{
							more.setEnabled( false );
							
							ds.getManager().getScheduler().download( 
								ds, 
								true,
								new SubscriptionDownloadListener(){
									
									public void
									complete(
										Subscription		subs )
									{
										done();
									}

									public void
									failed(
										Subscription			subs,
										SubscriptionException	error )
									{
										done();
									}
									
									private void
									done()
									{
										Utils.execSWTThread(
											new Runnable(){
												
												@Override
												public void run(){
													if ( !more.isDisposed()){
														
														more.setEnabled( true );
													}
												}
											});
									}
								});

						}catch( Throwable e ){

							Debug.out( e );
						}
					}
				});
				
			}

			parent.layout(true);
		}

		return null;
	}

	private String
	getString(
		String[]	strs )
	{
		String result = "";

		if ( strs != null ){

			for ( String str: strs ){

				result += (result==""?"":", ") + str;
			}
		}

		return( result );
	}

	private boolean
	isOurContent(
		SBC_SubscriptionResult result)
	{
		long	size = result.getSize();

		long kInB = DisplayFormatters.getKinB();
		long mInB = kInB*kInB;

		int seeds = result.getSeedCount();
		
		if ( minSeeds > 0 && seeds < minSeeds ){
			
			return( false );
		}
		
		boolean size_ok =

			(size==-1||(size >= mInB*minSize)) &&
			(size==-1||(maxSize ==0 || size <= mInB*maxSize));

		if ( !size_ok ){

			return( false );
		}

		if ( with_keywords.length > 0 || without_keywords.length > 0 ){

			synchronized( filter_lock ){

				String name = result.getName().toLowerCase( Locale.US );

				for ( int i=0;i<with_keywords.length;i++){

					if ( !name.contains( with_keywords[i] )){

						return( false );
					}
				}

				for ( int i=0;i<without_keywords.length;i++){

					if ( name.contains( without_keywords[i] )){

						return( false );
					}
				}
			}
		}

		return( true );
	}


	protected void refilter() {
		if (tv_subs_results != null) {
			tv_subs_results.refilter();
		}
	}


	private void
	initColumns(
		Core core )
	{
		synchronized( SBC_SubscriptionResultsView.class ){

			if ( columnsAdded ){

				return;
			}

			columnsAdded = true;
		}

		TableColumnManager tableManager = TableColumnManager.getInstance();

		tableManager.registerColumn(
			SBC_SubscriptionResult.class,
			ColumnSubResultNew.COLUMN_ID,
				new TableColumnCreationListener() {

					@Override
					public void tableColumnCreated(TableColumn column) {
						new ColumnSubResultNew(column);
					}
				});

		tableManager.registerColumn(
			SBC_SubscriptionResult.class,
			ColumnSearchSubResultType.COLUMN_ID,
				new TableColumnCreationListener() {

					@Override
					public void tableColumnCreated(TableColumn column) {
						new ColumnSearchSubResultType(column);
					}
				});

		tableManager.registerColumn(
			SBC_SubscriptionResult.class,
			ColumnSearchSubResultName.COLUMN_ID,
				new TableColumnCreationListener() {

					@Override
					public void tableColumnCreated(TableColumn column) {
						new ColumnSearchSubResultName(column);
					}
				});

		tableManager.registerColumn(
			SBC_SubscriptionResult.class,
			ColumnSearchSubResultActions.COLUMN_ID,
				new TableColumnCreationListener() {

					@Override
					public void tableColumnCreated(TableColumn column) {
						new ColumnSearchSubResultActions(column);
					}
				});

		tableManager.registerColumn(
			SBC_SubscriptionResult.class,
			ColumnSearchSubResultSize.COLUMN_ID,
				new TableColumnCreationListener() {

					@Override
					public void tableColumnCreated(TableColumn column) {
						new ColumnSearchSubResultSize(column);
					}
				});

		tableManager.registerColumn(
			SBC_SubscriptionResult.class,
			ColumnSearchSubResultSeedsPeers.COLUMN_ID,
				new TableColumnCreationListener() {

					@Override
					public void tableColumnCreated(TableColumn column) {
						new ColumnSearchSubResultSeedsPeers(column);
					}
				});

		tableManager.registerColumn(
			SBC_SubscriptionResult.class,
			ColumnSearchSubResultRatings.COLUMN_ID,
				new TableColumnCreationListener() {

					@Override
					public void tableColumnCreated(TableColumn column) {
						new ColumnSearchSubResultRatings(column);
					}
				});

		tableManager.registerColumn(
			SBC_SubscriptionResult.class,
			ColumnSearchSubResultAge.COLUMN_ID,
				new TableColumnCreationListener() {

					@Override
					public void tableColumnCreated(TableColumn column) {
						new ColumnSearchSubResultAge(column);
					}
				});

		tableManager.registerColumn(
			SBC_SubscriptionResult.class,
			ColumnSearchSubResultRank.COLUMN_ID,
				new TableColumnCreationListener() {

					@Override
					public void tableColumnCreated(TableColumn column) {
						new ColumnSearchSubResultRank(column);
					}
				});

		tableManager.registerColumn(
			SBC_SubscriptionResult.class,
			ColumnSearchSubResultCategory.COLUMN_ID,
				new TableColumnCreationListener() {

					@Override
					public void tableColumnCreated(TableColumn column) {
						new ColumnSearchSubResultCategory(column);
					}
				});

		tableManager.registerColumn(
			SBC_SubscriptionResult.class,
			ColumnSearchSubResultHash.COLUMN_ID,
				new TableColumnCreationListener() {

					@Override
					public void tableColumnCreated(TableColumn column) {
						new ColumnSearchSubResultHash(column);
					}
				});

		tableManager.registerColumn(
			SBC_SubscriptionResult.class,
			ColumnSearchSubResultExisting.COLUMN_ID,
				new TableColumnCreationListener() {

					@Override
					public void tableColumnCreated(TableColumn column) {
						new ColumnSearchSubResultExisting(column);
					}
				});
	}

	@Override
	public Object
	dataSourceChanged(
		SWTSkinObject skinObject, Object params)
	{
		synchronized( this ){

			Subscription new_ds = null;

			if ( params instanceof Subscription ){

				new_ds = (Subscription)params;

			}else if ( params instanceof Object[] ){

				Object[] objs = (Object[])params;

				if ( objs.length == 1 && objs[0] instanceof Subscription ){

					new_ds = (Subscription)objs[0];
				}
			}

			if ( ds != null ){

				ds.removeListener( this );
			}

			ds = new_ds;

			if ( new_ds != null ){

				ds.addListener( this );
			}
		}

		return( super.dataSourceChanged(skinObject, params));
	}

	@Override
	public void
	subscriptionChanged(
		Subscription		subs,
		int					reason )
	{
		if ( reason == CR_RESULTS){

			if ( tv_subs_results != null ) {

				reconcileResults( subs );

				tv_subs_results.runForAllRows(
					new TableGroupRowRunner() {
						@Override
						public void run(TableRowCore row) {
							row.invalidate( true );
						}
					});
			}
		}
	}

	private void
	reconcileResults(
		Subscription		subs )
	{
		synchronized( this ){

			if ( subs != ds || ds == null || subs == null || tv_subs_results == null ){

				return;
			}

			tv_subs_results.processDataSourceQueueSync();

			List<SBC_SubscriptionResult> existing_results = tv_subs_results.getDataSources( true );

			Map<String,SBC_SubscriptionResult>	existing_map = new HashMap<>();

			for ( SBC_SubscriptionResult result: existing_results ){

				existing_map.put( result.getID(), result );
			}

			SubscriptionResult[] current_results = ds.getResults( false );

			List<SBC_SubscriptionResult> new_results	= new ArrayList<>(current_results.length);

			for ( SubscriptionResult result: current_results ){

				SBC_SubscriptionResult existing = existing_map.remove( result.getID());

				if ( existing == null ){

					new_results.add( new SBC_SubscriptionResult( ds, result));

				}else{

					existing.updateFrom( result );
				}
			}

			if ( new_results.size() > 0 ){

				tv_subs_results.addDataSources( new_results.toArray( new SBC_SubscriptionResult[ new_results.size()]));
			}

			if ( existing_map.size() > 0 ){

				Collection<SBC_SubscriptionResult> to_remove = existing_map.values();

				tv_subs_results.removeDataSources( to_remove.toArray( new SBC_SubscriptionResult[ to_remove.size()]));

			}
		}
	}

	@Override
	public void
	subscriptionDownloaded(
		Subscription		subs,
		boolean				auto )
	{
	}

	private void
	showView()
	{
		SWTSkinObject so_list = getSkinObject("subs-results-list");

		if ( so_list != null ){

			so_list.setVisible(true);

			initTable((Composite) so_list.getControl());
		}
	}

	private void
	hideView()
	{
		Utils.disposeSWTObjects(new Object[] {
			table_parent,
		});
	}

	@Override
	public Object
	skinObjectShown(
		SWTSkinObject 	skinObject,
		Object 			params )
	{
		super.skinObjectShown(skinObject, params);

		showView();

		synchronized( this ){

			if ( ds != null ){

				ds.addListener( this );
			}
		}

		return null;
	}

	@Override
	public Object
	skinObjectHidden(
		SWTSkinObject 	skinObject,
		Object 			params )
	{
		hideView();

		synchronized( this ){

			if ( ds != null ){

				ds.removeListener( this );
			}
		}

		return( super.skinObjectHidden(skinObject, params));
	}

	@Override
	public Object
	skinObjectDestroyed(
		SWTSkinObject 	skinObject,
		Object 			params )
	{
		synchronized( this ){

			if ( tv_subs_results != null ){

				tv_subs_results.delete();

				tv_subs_results = null;
			}

			if ( ds != null ){

				ds.removeListener( this );
			}
		}

		Utils.disposeSWTObjects(new Object[] {
			table_parent,
		});

		return( super.skinObjectDestroyed(skinObject, params));
	}

	private void
	initTable(
		Composite control )
	{
		tv_subs_results = TableViewFactory.createTableViewSWT(
				SBC_SubscriptionResult.class,
				TABLE_SR,
				TABLE_SR,
				new TableColumnCore[0],
				ColumnSearchSubResultAge.COLUMN_ID,
				SWT.MULTI | SWT.FULL_SELECTION | SWT.VIRTUAL );

		TableColumnManager tableManager = TableColumnManager.getInstance();

		tableManager.setDefaultColumnNames( TABLE_SR,
				new String[] {
					ColumnSubResultNew.COLUMN_ID,
					ColumnSearchSubResultType.COLUMN_ID,
					ColumnSearchSubResultName.COLUMN_ID,
					ColumnSearchSubResultActions.COLUMN_ID,
					ColumnSearchSubResultSize.COLUMN_ID,
					ColumnSearchSubResultSeedsPeers.COLUMN_ID,
					ColumnSearchSubResultRatings.COLUMN_ID,
					ColumnSearchSubResultAge.COLUMN_ID,
					ColumnSearchSubResultRank.COLUMN_ID,
					ColumnSearchSubResultCategory.COLUMN_ID,
				});

		tableManager.setDefaultSortColumnName(TABLE_SR, ColumnSearchSubResultAge.COLUMN_ID);

		TableColumnCore tcc = tableManager.getTableColumnCore( TABLE_SR, ColumnSearchSubResultAge.COLUMN_ID );

		if ( tcc != null ){

			tcc.setDefaultSortAscending( true );
		}

		if (txtFilter != null) {
			tv_subs_results.enableFilterCheck(txtFilter, this);
		}

		tv_subs_results.setRowDefaultHeight(COConfigurationManager.getIntParameter( "Search Subs Row Height" ));

		SWTSkinObject soSizeSlider = getSkinObject("table-size-slider");
		if (soSizeSlider instanceof SWTSkinObjectContainer) {
			SWTSkinObjectContainer so = (SWTSkinObjectContainer) soSizeSlider;
			if (!tv_subs_results.enableSizeSlider(so.getComposite(), 16, 100)) {
				so.setVisible(false);
			}
		}

		table_parent = new Composite(control, SWT.NONE);
		table_parent.setLayoutData(Utils.getFilledFormData());
		GridLayout layout = new GridLayout();
		layout.marginHeight = layout.marginWidth = layout.verticalSpacing = layout.horizontalSpacing = 0;
		table_parent.setLayout(layout);

		tv_subs_results.addSelectionListener(new TableSelectionListener() {

			@Override
			public void
			selected(
				TableRowCore[] _rows)
			{
				updateSelectedContent();
			}

			@Override
			public void mouseExit(TableRowCore row) {
			}

			@Override
			public void mouseEnter(TableRowCore row) {
			}

			@Override
			public void focusChanged(TableRowCore focus) {
				UIFunctions uiFunctions = UIFunctionsManager.getUIFunctions();
				if (uiFunctions != null) {
					uiFunctions.refreshIconBar();
				}
			}

			@Override
			public void deselected(TableRowCore[] rows) {
				updateSelectedContent();
			}

			@Override
			public void defaultSelected(TableRowCore[] rows, int stateMask) {
				if ( rows.length == 1 ){

					SBC_SubscriptionResult rc = (SBC_SubscriptionResult)rows[0].getDataSource();

					SBC_SearchResultsView.downloadAction( rc );
				}
			}

			private void
			updateSelectedContent()
			{
				TableRowCore[] rows = tv_subs_results.getSelectedRows();

				ArrayList<ISelectedContent>	valid = new ArrayList<>();

				last_selected_content.clear();

				for (int i=0;i<rows.length;i++){

					SBC_SubscriptionResult rc = (SBC_SubscriptionResult)rows[i].getDataSource();

					last_selected_content.add( rc );

					byte[] hash = rc.getHash();

					if ( hash != null && hash.length > 0 ){

						SelectedContent sc = new SelectedContent(Base32.encode(hash), rc.getName());

						sc.setDownloadInfo(new DownloadUrlInfo(	getDownloadURI( rc )));

						valid.add(sc);
					}
				}

				ISelectedContent[] sels = valid.toArray( new ISelectedContent[valid.size()] );

				SelectedContentManager.changeCurrentlySelectedContent("IconBarEnabler",
						sels, tv_subs_results);

				UIFunctions uiFunctions = UIFunctionsManager.getUIFunctions();

				if ( uiFunctions != null ){

					uiFunctions.refreshIconBar();
				}
			}
		}, false);

		tv_subs_results.addLifeCycleListener(new TableLifeCycleListener() {
			@Override
			public void tableLifeCycleEventOccurred(TableView tv, int eventType,
					Map<String, Object> data) {
				if (eventType == TableLifeCycleListener.EVENT_TABLELIFECYCLE_INITIALIZED) {
					reconcileResults(ds);
				}
			}
		});

		tv_subs_results.addMenuFillListener(
			new TableViewSWTMenuFillListener()
			{
				@Override
				public void
				fillMenu(String sColumnName, Menu menu)
				{
					Object[] _related_content = tv_subs_results.getSelectedDataSources().toArray();

					final SBC_SubscriptionResult[] results = new SBC_SubscriptionResult[_related_content.length];

					System.arraycopy(_related_content, 0, results, 0, results.length);

					MenuItem item = new MenuItem(menu, SWT.PUSH);
					item.setText(MessageText.getString("label.copy.url.to.clip"));
					item.addSelectionListener(new SelectionAdapter() {
						@Override
						public void widgetSelected(SelectionEvent e) {

							StringBuffer buffer = new StringBuffer(1024);

							for ( SBC_SubscriptionResult result: results ){

								if ( buffer.length() > 0 ){
									buffer.append( "\r\n" );
								}

								buffer.append( getDownloadURI( result ));
							}
							ClipboardCopy.copyToClipBoard( buffer.toString());
						}
					});

					item.setEnabled( results.length > 0 );

					SearchSubsUtils.addMenu( results, menu );

					new MenuItem(menu, SWT.SEPARATOR );

					if ( results.length == 1 ){

						if ( SearchSubsUtils.addMenu( results[0], menu )){

							new MenuItem(menu, SWT.SEPARATOR );
						}
					}

					final MenuItem remove_item = new MenuItem(menu, SWT.PUSH);

					remove_item.setText(MessageText.getString("azbuddy.ui.menu.remove"));

					Utils.setMenuItemImage( remove_item, "delete" );

					remove_item.addSelectionListener(new SelectionAdapter() {
						@Override
						public void widgetSelected(SelectionEvent e) {
							userDelete(results);
						}

					});

					remove_item.setEnabled( results.length > 0 );

					new MenuItem(menu, SWT.SEPARATOR );
				}

				@Override
				public void
				addThisColumnSubMenu(
					String columnName, Menu menuThisColumn)
				{
				}
			});

		tv_subs_results.addKeyListener(
				new KeyListener()
				{
					@Override
					public void
					keyPressed(
						KeyEvent e )
					{
						if ( e.stateMask == 0 && e.keyCode == SWT.DEL ){

							Object[] selected;

							synchronized (this) {

								if ( tv_subs_results == null ){

									selected = new Object[0];

								}else{

									selected = tv_subs_results.getSelectedDataSources().toArray();
								}
							}

							SBC_SubscriptionResult[] content = new SBC_SubscriptionResult[ selected.length ];

							for ( int i=0;i<content.length;i++){

								content[i] = (SBC_SubscriptionResult)selected[i];
							}

							userDelete( content );

							e.doit = false;
						}
					}

					@Override
					public void
					keyReleased(
						KeyEvent arg0 )
					{
					}
				});

		/*
		if (ds instanceof RCMItemSubView) {
	  		tv_related_content.addCountChangeListener(new TableCountChangeListener() {

	  			public void rowRemoved(TableRowCore row) {
	  				updateCount();
	  			}

	  			public void rowAdded(TableRowCore row) {
	  				updateCount();
	  			}

					private void updateCount() {
						int size = tv_related_content == null ? 0 : tv_related_content.size(false);
						((RCMItemSubView) ds).setCount(size);
					}
	  		});
	  		((RCMItemSubView) ds).setCount(0);
		}
		*/

		tv_subs_results.initialize( table_parent );

		control.layout(true);
	}

	private void
	userDelete(
		SBC_SubscriptionResult[] results )
	{
		TableRowCore focusedRow = tv_subs_results.getFocusedRow();

		TableRowCore focusRow = null;

		if (focusedRow != null) {
			int i = tv_subs_results.indexOf(focusedRow);
			int size = tv_subs_results.size(false);
			if (i < size - 1) {
				focusRow = tv_subs_results.getRow(i + 1);
			} else if (i > 0) {
				focusRow = tv_subs_results.getRow(i - 1);
			}
		}

		// TODO: Need a mass delete option.  This one saves the Subscription History
		// on each call..
		for ( SBC_SubscriptionResult result: results ){

			result.delete();
		}

		if ( focusRow != null ){

			tv_subs_results.setSelectedRows(new TableRowCore[]{focusRow });
		}
	}

	@Override
	public String
	getUpdateUIName()
	{
		return( "SubscriptionResultsView" );
	}

	@Override
	public void
	updateUI()
	{
		if ( tv_subs_results != null ){

			tv_subs_results.refreshTable( false );
		}
	}

	@Override
	public boolean
	filterCheck(
		SBC_SubscriptionResult 		ds,
		String 						filter,
		boolean 					regex )
	{
		if (!isOurContent(ds)){

			return false;
		}

		return( SearchSubsUtils.filterCheck( ds, filter, regex ));
	}

	@Override
	public void filterSet(String filter) {
	}

	@Override
	public boolean
	toolBarItemActivated(
		ToolBarItem item,
		long activationType,
		Object datasource )
	{
		if ( tv_subs_results == null || !tv_subs_results.isVisible()){

			return( false );
		}

		if (item.getID().equals("remove")) {

			Object[] _related_content = tv_subs_results.getSelectedDataSources().toArray();

			if ( _related_content.length > 0 ){

				SBC_SubscriptionResult[] related_content = new SBC_SubscriptionResult[_related_content.length];

				System.arraycopy( _related_content, 0, related_content, 0, related_content.length );

				userDelete( related_content );

				return true;
			}
		}

		return false;
	}

	@Override
	public void
	refreshToolBarItems(
		Map<String, Long> list)
	{
		if ( tv_subs_results == null || !tv_subs_results.isVisible()){

			return;
		}

			// make sure we're operating on a selection we understand...

		ISelectedContent[] content = SelectedContentManager.getCurrentlySelectedContent();

		for ( ISelectedContent c: content ){

			if ( c.getDownloadManager() != null ){

				return;
			}
		}

		list.put("remove", tv_subs_results.getSelectedDataSources().size() > 0 ? UIToolBarItem.STATE_ENABLED : 0);
	}

	public String
	getDownloadURI(
		SBC_SubscriptionResult	result )
	{
		String torrent_url = (String)result.getTorrentLink();

		if ( torrent_url != null && torrent_url.length() > 0 ){

			return( torrent_url );
		}

		String uri = UrlUtils.getMagnetURI( result.getHash(), result.getName(), ds.getHistory().getDownloadNetworks());

		return( uri );
	}

}
