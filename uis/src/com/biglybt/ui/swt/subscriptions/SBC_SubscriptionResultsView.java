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
import java.util.function.Function;

import org.eclipse.swt.SWT;
import org.eclipse.swt.events.*;
import org.eclipse.swt.graphics.Color;
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
import com.biglybt.core.subs.util.SubscriptionResultFilterable;
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
import com.biglybt.ui.selectedcontent.DownloadUrlInfo;
import com.biglybt.ui.selectedcontent.ISelectedContent;
import com.biglybt.ui.selectedcontent.SelectedContent;
import com.biglybt.ui.selectedcontent.SelectedContentManager;
import com.biglybt.ui.swt.MenuBuildUtils;
import com.biglybt.ui.swt.UIFunctionsManagerSWT;
import com.biglybt.ui.swt.Utils;
import com.biglybt.ui.swt.columns.searchsubs.*;
import com.biglybt.ui.swt.columns.subscriptions.ColumnSubResultNew;
import com.biglybt.ui.swt.components.BubbleTextBox;
import com.biglybt.ui.swt.imageloader.ImageLoader;
import com.biglybt.ui.swt.mainwindow.ClipboardCopy;
import com.biglybt.ui.swt.mainwindow.Colors;
import com.biglybt.ui.swt.mdi.MdiEntrySWT;
import com.biglybt.ui.swt.mdi.MultipleDocumentInterfaceSWT;
import com.biglybt.ui.swt.pif.UISWTViewEvent;
import com.biglybt.ui.swt.search.SBC_SearchResultsView;
import com.biglybt.ui.swt.skin.*;
import com.biglybt.ui.swt.utils.SearchSubsUtils;
import com.biglybt.ui.swt.views.skin.SkinView;
import com.biglybt.ui.swt.views.table.TableRowSWT;
import com.biglybt.ui.swt.views.table.TableViewSWT;
import com.biglybt.ui.swt.views.table.TableViewSWTMenuFillListener;
import com.biglybt.ui.swt.views.table.impl.TableViewFactory;
import com.biglybt.ui.swt.views.table.utils.TableColumnCreator;
import com.biglybt.ui.swt.views.table.utils.TableColumnFilterHelper;
import com.biglybt.ui.swt.views.tableitems.ColumnDateSizer;

public class
SBC_SubscriptionResultsView
	extends SkinView
	implements UIUpdatable, UIPluginViewToolBarListener, TableViewFilterCheck<SubscriptionResultFilterable>, SubscriptionListener
{
	public static final String TABLE_SR = "SubscriptionResults";

	private final Object FILTER_KEY = new Object();	// not static as we want separate per view
	
	private static boolean columnsAdded = false;

	private static TableViewSWT.ColorRequester colour_requester = ()->1;
	
	private static LinkedList<MdiEntrySWT> activated_views	= new LinkedList<>();
	
	private TableViewSWT<SubscriptionResultFilterable>				tv_subs_results;
	private TableColumnFilterHelper<SubscriptionResultFilterable>	col_filter_helper;

	private MdiEntrySWT			mdi_entry;
	private Composite			table_parent;


	private final Object filter_lock = new Object();


	private FrequencyLimitedDispatcher	refilter_dispatcher =
			new FrequencyLimitedDispatcher(
				new AERunnable() {

					@Override
					public void runSupport()
					{
						refilter();
					}
				}, 250 );

	private Subscription	 			ds;
	private SubscriptionResultFilter	ds_filter = SubscriptionResultFilter.getTransientFilter();
	private Runnable 					pFilterUpdater;
	private long						ds_filter_version = -1;
	
	private List<SubscriptionResultFilterable>	last_selected_content = new ArrayList<>();

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
				
				MdiEntrySWT to_deactivate = null;
				
				synchronized( activated_views ){
					
					activated_views.add( mdi_entry );
					
					if ( activated_views.size() > 8 ){
						
						for ( MdiEntrySWT m: activated_views ){
							
							if ( m != mdi_entry ){
							
									// stand-alone views can cause confusion - don't deactive the one
									// currently being built to avoid borkage...
								
								to_deactivate = m;
								
								break;
							}
						}
					}
				}
				
				if ( to_deactivate != null ){
					
					to_deactivate.triggerEvent( UISWTViewEvent.TYPE_DESTROY, null );
				}
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

				menu.addMenuListener(
					new MenuListener() {

						@Override
						public void menuShown(MenuEvent e) {
							for ( MenuItem mi: menu.getItems()){
								mi.dispose();
							}

							String menu_key = SubscriptionMDIEntry.setupMenus( ds, null );

							com.biglybt.pif.ui.menus.MenuItem[] menu_items = MenuItemManager.getInstance().getAllAsArray( menu_key );

							MenuBuildUtils.addPluginMenuItems(menu_items, menu, true, true,
									new MenuBuildUtils.MenuItemPluginMenuControllerImpl(new Object[]{ ds }));
						}

						@Override
						public void menuHidden(MenuEvent e) {
						}
					});
			}
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

			pFilterUpdater = null;

			if ( ds != null ){

				try{
					Composite pFilters = new Composite(cFilters, SWT.NONE);
					pFilters.setLayoutData(new GridData( GridData.FILL_HORIZONTAL ));

					layout = new GridLayout( 1, false );
					layout.marginBottom = layout.marginTop = layout.marginLeft = layout.marginRight = 0;
					layout.marginWidth = layout.marginHeight = 0;

					pFilters.setLayout( layout );

					final Label deplabel = new Label( pFilters, SWT.NONE );
					deplabel.setLayoutData(new GridData( GridData.FILL_HORIZONTAL ));
					
					final Label pflabel = new Label( pFilters, SWT.NONE );
					pflabel.setLayoutData(new GridData( GridData.FILL_HORIZONTAL ));
										
					pFilterUpdater = new Runnable()
					{
						@Override
						public void
						run()
						{
							if ( pflabel.isDisposed()){
								
								if ( pFilterUpdater == this ){
									
									pFilterUpdater = null;
								}
								
								return;
							}
							
							List<Subscription> deps = ds_filter.getDependsOn();
							
							if ( deps != null && !deps.isEmpty()){
								
								String str = "";
								for ( Subscription dep: deps ){
									str += (str.isEmpty()?"":"; ") + dep.getName();
									
									try{
										str += " {" + dep.getFilters().getString().replaceAll("&","&&") + "}";
									}catch( Throwable e ){
									}
								}
								
								deplabel.setText( MessageText.getString( "subs.inherited.filters" ) + ": " + str );
								
								deplabel.setVisible( true );
								GridData gd = (GridData)deplabel.getLayoutData();
								if ( gd.widthHint == 0 ){
									gd.widthHint = gd.heightHint = SWT.DEFAULT;
									parent.getParent().getParent().layout(true, true);
								}
								
							}else{
								deplabel.setVisible( false );
								GridData gd = (GridData)deplabel.getLayoutData();
								if ( gd.widthHint != 0 ){
									gd.widthHint = gd.heightHint = 0;
									parent.getParent().getParent().layout(true, true);
								}
							}
							
							if ( ds.isUpdateable()){
								long kInB = DisplayFormatters.getKinB();
								long mInB = kInB*kInB;
	
								String mb = DisplayFormatters.getUnit( DisplayFormatters.UNIT_MB );
								
								String with_words = getString( ds_filter.getWithWords());
								String without_words = getString( ds_filter.getWithoutWords());
								
								long	min_size = Math.max( 0,  ds_filter.getMinSize()/mInB );
								long	max_size = Math.max( 0,  ds_filter.getMaxSize()/mInB );
								long	min_seeds = Math.max( 0,  ds_filter.getMinSeeds());
								long	max_seeds = Math.max( 0,  ds_filter.getMaxSeeds());
								long	min_peers = Math.max( 0,  ds_filter.getMinPeers());
								long	max_age = Math.max( 0,  ds_filter.getMaxAgeSecs());
								
								String[] line = {""};
								
								addLine( line, !with_words.isEmpty(), MessageText.getString( "SubscriptionResults.filter.with.words" ), "[" + with_words + "]" );	
								addLine( line, !without_words.isEmpty(), MessageText.getString( "SubscriptionResults.filter.without.words" ), "[" + without_words + "]" );	
								addLine( line, min_size>0, MessageText.getString( "label.min.size"),  min_size + " " + mb ); 
								addLine( line, max_size>0, MessageText.getString( "label.max.size"),  max_size + " " + mb); 
								addLine( line, min_seeds>0, MessageText.getString( "label.min.seeds"), String.valueOf( min_seeds ));
								addLine( line, max_seeds>0, MessageText.getString( "label.max.seeds"), String.valueOf(max_seeds )); 
								addLine( line, min_peers>0, MessageText.getString( "label.min.peers"), String.valueOf(min_peers )); 
								addLine( line, max_age>0, MessageText.getString( "label.max.age"), TimeFormatter.format3( max_age, null, true ));
								
								String pf = line[0].trim();
								
								if ( pf.isEmpty()){
									
									pf = MessageText.getString( "label.none" );
								}
								
								pflabel.setText( MessageText.getString("subs.persistent.filters2" ) + ": " + pf );
								
							}else{
								pflabel.setVisible( false );
								GridData gd = (GridData)pflabel.getLayoutData();
								gd.widthHint = gd.heightHint = 0;
							}
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
			rowLayout.spacing = 3;
			rowLayout.marginBottom = rowLayout.marginTop = rowLayout.marginLeft = rowLayout.marginRight = 0;
			rowLayout.center = true;
			vFilters.setLayout(rowLayout);


			// with/without keywords

			ImageLoader imageLoader = ImageLoader.getInstance();

			Function<String[],String> flattener = 
				( words ) ->{
					String str ="";
					
					for ( String word: words ){
						str += (str.isEmpty()?"":" ") + word;
					}
					
					return( str );
				};
				
			for ( int i=0;i<2;i++){

				final boolean with = i == 0;

				if ( !with ){

					Control sep = Utils.createSkinnedLabelSeparator(vFilters, SWT.VERTICAL );
					sep.setLayoutData(new RowData(-1, sepHeight));
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
				Utils.setTT(textWithKW,MessageText.getString("SubscriptionResults.filter.words.tt" ));
				textWithKW.setMessage(MessageText.getString(with?"SubscriptionResults.filter.with.words":"SubscriptionResults.filter.without.words"));
				GridData gd = new GridData();
				gd.widthHint = 100;
				textWithKW.setText( flattener.apply( with?ds_filter.getWithWords():ds_filter.getWithoutWords()));
				textWithKW.setLayoutData( gd );
				textWithKW.addModifyListener(
					new ModifyListener() {

						@Override
						public void modifyText(ModifyEvent e) {
							String text = textWithKW.getText();
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
									ds_filter.setWithWords( words );
								}else{
									ds_filter.setWithoutWords( words );
								}
							}
							refilter_dispatcher.dispatch();
						}
					});
			}

			int kInB = DisplayFormatters.getKinB();
			long mInB = kInB*kInB;
			
				// min size

			Control sep = Utils.createSkinnedLabelSeparator(vFilters, SWT.VERTICAL );
			sep.setLayoutData(new RowData(-1, sepHeight));

			{
				Composite cMinSize = new Composite(vFilters, SWT.NONE);
				layout = new GridLayout(2, false);
				layout.marginWidth = 0;
				layout.marginBottom = layout.marginTop = layout.marginLeft = layout.marginRight = 0;
				cMinSize.setLayout(layout);
				Label lblMinSize = new Label(cMinSize, SWT.NONE);
				lblMinSize.setText(MessageText.getString("label.min.size") + " (" + DisplayFormatters.getUnit(DisplayFormatters.UNIT_MB) + ")");
				Spinner spinMinSize = new Spinner(cMinSize, SWT.BORDER);
				GridData gd = new GridData();
				gd.widthHint = 20;
				spinMinSize.setLayoutData(gd);
				spinMinSize.setMinimum(0);
				spinMinSize.setMaximum(100*kInB*kInB);	// 100 TB should do...
				spinMinSize.setSelection(Math.max( 0,  (int)( ds_filter.getMinSize()/mInB )));
				spinMinSize.addListener(SWT.Selection, new Listener() {
					@Override
					public void handleEvent(Event event) {
						ds_filter.setMinSize(((Spinner) event.widget).getSelection() * mInB);
						refilter_dispatcher.dispatch();
					}
				});
			}
			
			// max size

			sep = Utils.createSkinnedLabelSeparator(vFilters, SWT.VERTICAL );
			sep.setLayoutData(new RowData(-1, sepHeight));

			{
				Composite cMaxSize = new Composite(vFilters, SWT.NONE);
				layout = new GridLayout(2, false);
				layout.marginWidth = 0;
				layout.marginBottom = layout.marginTop = layout.marginLeft = layout.marginRight = 0;
				cMaxSize.setLayout(layout);
				Label lblMaxSize = new Label(cMaxSize, SWT.NONE);
				lblMaxSize.setText(MessageText.getString("label.max.size") + " (" + DisplayFormatters.getUnit(DisplayFormatters.UNIT_MB) + ")");
				Spinner spinMaxSize = new Spinner(cMaxSize, SWT.BORDER);
				GridData gd = new GridData();
				gd.widthHint = 20;
				spinMaxSize.setLayoutData(gd);
				spinMaxSize.setMinimum(0);
				spinMaxSize.setMaximum(100*kInB*kInB);	// 100 TB should do...
				spinMaxSize.setSelection(Math.max( 0,  (int)( ds_filter.getMaxSize()/mInB )));
				spinMaxSize.addListener(SWT.Selection, new Listener() {
					@Override
					public void handleEvent(Event event) {
						ds_filter.setMaxSize(((Spinner) event.widget).getSelection() * mInB);
						refilter_dispatcher.dispatch();
					}
				});
			}
			
				// min seeds
			
			sep = Utils.createSkinnedLabelSeparator(vFilters, SWT.VERTICAL );
			sep.setLayoutData(new RowData(-1, sepHeight));

			{	
				Composite cMinSeeds = new Composite(vFilters, SWT.NONE);
				layout = new GridLayout(2, false);
				layout.marginWidth = 0;
				layout.marginBottom = layout.marginTop = layout.marginLeft = layout.marginRight = 0;
				cMinSeeds.setLayout(layout);
				Label lblMinSeeds = new Label(cMinSeeds, SWT.NONE);
				lblMinSeeds.setText(MessageText.getString("label.min.seeds"));
				Spinner spinMinSeeds = new Spinner(cMinSeeds, SWT.BORDER);
				GridData gd = new GridData();
				gd.widthHint = 20;
				spinMinSeeds.setLayoutData(gd);
				spinMinSeeds.setMinimum(0);
				spinMinSeeds.setMaximum(Integer.MAX_VALUE);
				spinMinSeeds.setSelection((int)ds_filter.getMinSeeds());
				spinMinSeeds.addListener(SWT.Selection, new Listener() {
					@Override
					public void handleEvent(Event event) {
						ds_filter.setMinSeeds(((Spinner) event.widget).getSelection());
						refilter_dispatcher.dispatch();
					}
				});
			}
			
				// max seeds
			
			sep = Utils.createSkinnedLabelSeparator(vFilters, SWT.VERTICAL );
			sep.setLayoutData(new RowData(-1, sepHeight));

			{		
				Composite cMaxSeeds = new Composite(vFilters, SWT.NONE);
				layout = new GridLayout(2, false);
				layout.marginWidth = 0;
				layout.marginBottom = layout.marginTop = layout.marginLeft = layout.marginRight = 0;
				cMaxSeeds.setLayout(layout);
				Label lblMaxSeeds = new Label(cMaxSeeds, SWT.NONE);
				lblMaxSeeds.setText(MessageText.getString("label.max.seeds"));
				Spinner spinMaxSeeds = new Spinner(cMaxSeeds, SWT.BORDER);
				GridData gd = new GridData();
				gd.widthHint = 20;
				spinMaxSeeds.setLayoutData(gd);
				spinMaxSeeds.setMinimum(0);
				spinMaxSeeds.setMaximum(Integer.MAX_VALUE);
				spinMaxSeeds.setSelection((int)ds_filter.getMaxSeeds());
				spinMaxSeeds.addListener(SWT.Selection, new Listener() {
					@Override
					public void handleEvent(Event event) {
						ds_filter.setMaxSeeds(((Spinner) event.widget).getSelection());
						refilter_dispatcher.dispatch();
					}
				});
			}
			
				// min peers
			
			sep = Utils.createSkinnedLabelSeparator(vFilters, SWT.VERTICAL );
			sep.setLayoutData(new RowData(-1, sepHeight));

			{
	
				Composite cMinPeers = new Composite(vFilters, SWT.NONE);
				layout = new GridLayout(2, false);
				layout.marginWidth = 0;
				layout.marginBottom = layout.marginTop = layout.marginLeft = layout.marginRight = 0;
				cMinPeers.setLayout(layout);
				Label lblMinPeers = new Label(cMinPeers, SWT.NONE);
				lblMinPeers.setText(MessageText.getString("label.min.peers"));
				Spinner spinMinPeers = new Spinner(cMinPeers, SWT.BORDER);
				GridData gd = new GridData();
				gd.widthHint = 20;
				spinMinPeers.setLayoutData(gd);
				spinMinPeers.setMinimum(0);
				spinMinPeers.setMaximum(Integer.MAX_VALUE);
				spinMinPeers.setSelection((int)ds_filter.getMinPeers());
				spinMinPeers.addListener(SWT.Selection, new Listener() {
					@Override
					public void handleEvent(Event event) {
						ds_filter.setMinPeers(((Spinner) event.widget).getSelection());
						refilter_dispatcher.dispatch();
					}
				});
			}
					
				// max age
			
			sep = Utils.createSkinnedLabelSeparator(vFilters, SWT.VERTICAL );
			sep.setLayoutData(new RowData(-1, sepHeight));

			{
				Composite cMaxAge = new Composite(vFilters, SWT.NONE);
				layout = new GridLayout(3, false);
				layout.marginWidth = 0;
				layout.marginBottom = layout.marginTop = layout.marginLeft = layout.marginRight = 0;
				cMaxAge.setLayout(layout);
				
				Label lblMaxAge = new Label(cMaxAge, SWT.NONE);
				lblMaxAge.setText(MessageText.getString("label.max.age"));
				
				Spinner spinMaxAge = new Spinner(cMaxAge, SWT.BORDER);
				GridData gd = new GridData();
				gd.widthHint = 20;
				spinMaxAge.setLayoutData(gd);
				spinMaxAge.setMinimum(0);
				spinMaxAge.setMaximum(Integer.MAX_VALUE);
				
				Combo combMaxAge = new Combo( cMaxAge, SWT.SINGLE | SWT.READ_ONLY );
				
				for ( String unit: TimeFormatter.TIME_SUFFIXES_2 ){
				
					combMaxAge.add( unit );
				}
				
				Listener maxAgeListener = (e)->{
					int	val 	= spinMaxAge.getSelection();
					int unit	= combMaxAge.getSelectionIndex();
					
					long secs = TimeFormatter.TIME_SUFFIXES_2_MULT[unit] * (long)( val + 1 ) - 1;
					
					ds_filter.setMaxAgeSecs( secs );
					
					refilter_dispatcher.dispatch();
				};
				
				int[] temp = TimeFormatter.format3Support( ds_filter.getMaxAgeSecs(), null, true );
				
				int val = temp[0];
				
				spinMaxAge.setSelection( val<0?0:val );
				combMaxAge.select( temp[1] );			
				
				spinMaxAge.addListener(SWT.Selection, maxAgeListener );		
				combMaxAge.addListener(SWT.Selection, maxAgeListener );
			}
			
			if ( ds != null ){

				if ( ds.isUpdateable() ){
					
					sep = Utils.createSkinnedLabelSeparator(vFilters, SWT.VERTICAL );
					
					sep.setLayoutData(new RowData(-1, sepHeight));
	
					final Runnable	f_pFilterUpdater 	= pFilterUpdater;
	
					Button save = new Button( vFilters,SWT.PUSH );
					
					save.setText( MessageText.getString( "ConfigView.button.save" ));
					
					save.addListener(SWT.Selection, new Listener() {
						@Override
						public void handleEvent(Event event) {
	
							Utils.getOffOfSWTThread(()->{
								
								try{
									ds_filter.save();
									
								}catch( Throwable e ){
									
									Debug.out( e );
	
								}finally{
									
									Utils.execSWTThread(()->f_pFilterUpdater.run());
								}
							});
						}
					});
				}
				
				sep = Utils.createSkinnedLabelSeparator(vFilters, SWT.VERTICAL );
				sep.setLayoutData(new RowData(-1, sepHeight));
				
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

	private void
	addLine(
		String[]	line,
		boolean		add,
		String		name,
		String		value )
	{
		if ( add ){
			
			String text = name + " = " + value;
			
			if ( line[0].isEmpty()){
				
				line[0] = text;
				
			}else{
				
				line[0] += ", " + text;
			}
		}
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
		SubscriptionResultFilterable result)
	{
		if ( ds_filter == null ){
			
			return( true );
			
		}else{
		
			return( !ds_filter.isFiltered( result ));
		}
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
			SubscriptionResultFilterable.class,
			ColumnSubResultNew.COLUMN_ID,
				new TableColumnCreationListener() {

					@Override
					public void tableColumnCreated(TableColumn column) {
						new ColumnSubResultNew(column);
					}
				});

		tableManager.registerColumn(
			SubscriptionResultFilterable.class,
			ColumnSearchSubResultType.COLUMN_ID,
				new TableColumnCreationListener() {

					@Override
					public void tableColumnCreated(TableColumn column) {
						new ColumnSearchSubResultType(column);
					}
				});

		tableManager.registerColumn(
			SubscriptionResultFilterable.class,
			ColumnSearchSubResultName.COLUMN_ID,
				new TableColumnCreationListener() {

					@Override
					public void tableColumnCreated(TableColumn column) {
						new ColumnSearchSubResultName(column);
					}
				});

		tableManager.registerColumn(
			SubscriptionResultFilterable.class,
			ColumnSearchSubResultActions.COLUMN_ID,
				new TableColumnCreationListener() {

					@Override
					public void tableColumnCreated(TableColumn column) {
						new ColumnSearchSubResultActions(column);
					}
				});

		tableManager.registerColumn(
			SubscriptionResultFilterable.class,
			ColumnSearchSubResultSize.COLUMN_ID,
				new TableColumnCreationListener() {

					@Override
					public void tableColumnCreated(TableColumn column) {
						new ColumnSearchSubResultSize(column);
					}
				});

		tableManager.registerColumn(
			SubscriptionResultFilterable.class,
			ColumnSearchSubResultSeedsPeers.COLUMN_ID,
				new TableColumnCreationListener() {

					@Override
					public void tableColumnCreated(TableColumn column) {
						new ColumnSearchSubResultSeedsPeers(column);
					}
				});

		tableManager.registerColumn(
			SubscriptionResultFilterable.class,
			ColumnSearchSubResultSeeds.COLUMN_ID,
				new TableColumnCreationListener() {

					@Override
					public void tableColumnCreated(TableColumn column) {
						new ColumnSearchSubResultSeeds(column);
					}
				});

		tableManager.registerColumn(
			SubscriptionResultFilterable.class,
			ColumnSearchSubResultPeers.COLUMN_ID,
				new TableColumnCreationListener() {

					@Override
					public void tableColumnCreated(TableColumn column) {
						new ColumnSearchSubResultPeers(column);
					}
				});
		
		tableManager.registerColumn(
			SubscriptionResultFilterable.class,
			ColumnSearchSubResultGrabbed.COLUMN_ID,
				new TableColumnCreationListener() {

					@Override
					public void tableColumnCreated(TableColumn column) {
						new ColumnSearchSubResultGrabbed(column);
					}
				});
		
		tableManager.registerColumn(
			SubscriptionResultFilterable.class,
			ColumnSearchSubResultRatings.COLUMN_ID,
				new TableColumnCreationListener() {

					@Override
					public void tableColumnCreated(TableColumn column) {
						new ColumnSearchSubResultRatings(column);
					}
				});

		tableManager.registerColumn(
			SubscriptionResultFilterable.class,
			ColumnSearchSubResultAge.COLUMN_ID,
				new TableColumnCreationListener() {

					@Override
					public void tableColumnCreated(TableColumn column) {
						new ColumnSearchSubResultAge(column);
					}
				});
		
		tableManager.registerColumn(
			SubscriptionResultFilterable.class,
			ColumnSearchSubResultAssetDate.COLUMN_ID,
			new TableColumnCoreCreationListener() {
				@Override
				public TableColumnCore createTableColumnCore(
						Class<?> forDataSourceType, String tableID, String columnID) {
					return new ColumnDateSizer(SubscriptionResultFilterable.class, columnID,
							TableColumnCreator.DATE_COLUMN_WIDTH, tableID) {
					};
				}

				@Override
				public void tableColumnCreated(TableColumn column) {
					new ColumnSearchSubResultAssetDate(column);
				}
			});

		tableManager.registerColumn(
			SubscriptionResultFilterable.class,
			ColumnSearchSubResultRank.COLUMN_ID,
				new TableColumnCreationListener() {

					@Override
					public void tableColumnCreated(TableColumn column) {
						new ColumnSearchSubResultRank(column);
					}
				});

		tableManager.registerColumn(
			SubscriptionResultFilterable.class,
			ColumnSearchSubResultCategory.COLUMN_ID,
				new TableColumnCreationListener() {

					@Override
					public void tableColumnCreated(TableColumn column) {
						new ColumnSearchSubResultCategory(column);
					}
				});

		tableManager.registerColumn(
			SubscriptionResultFilterable.class,
			ColumnSearchSubResultTags.COLUMN_ID,
				new TableColumnCoreCreationListener() 
				{
					@Override
					public TableColumnCore 
					createTableColumnCore(
						Class<?> forDataSourceType, String tableID, String columnID )
					{
						return( new ColumnSearchSubResultTags(forDataSourceType, tableID, columnID ));
					}
				
					@Override
					public void tableColumnCreated(TableColumn column) {				
					}
				});

		tableManager.registerColumn(
			SubscriptionResultFilterable.class,
			ColumnSearchSubResultHash.COLUMN_ID,
				new TableColumnCreationListener() {

					@Override
					public void tableColumnCreated(TableColumn column) {
						new ColumnSearchSubResultHash(column);
					}
				});

		tableManager.registerColumn(
			SubscriptionResultFilterable.class,
			ColumnSearchSubResultExisting.COLUMN_ID,
				new TableColumnCreationListener() {

					@Override
					public void tableColumnCreated(TableColumn column) {
						new ColumnSearchSubResultExisting(column);
					}
				});
		
		tableManager.registerColumn(
				SubscriptionResultFilterable.class,
				ColumnSearchSubResultDLHistoryAdded.COLUMN_ID,
				new TableColumnCoreCreationListener() {
					@Override
					public TableColumnCore 
					createTableColumnCore(
							Class<?> forDataSourceType, String tableID, String columnID) 
					{
						return new ColumnDateSizer(
								SubscriptionResultFilterable.class, columnID,
								TableColumnCreator.DATE_COLUMN_WIDTH, tableID){};
					}

					@Override
					public void tableColumnCreated(TableColumn column) {
						new ColumnSearchSubResultDLHistoryAdded(column);
					}
				});
		
		tableManager.registerColumn(
			SubscriptionResultFilterable.class,
			ColumnSearchSubResultDLHistoryCompleted.COLUMN_ID,
			new TableColumnCoreCreationListener() {
				@Override
				public TableColumnCore 
				createTableColumnCore(
						Class<?> forDataSourceType, String tableID, String columnID) 
				{
					return new ColumnDateSizer(
							SubscriptionResultFilterable.class, columnID,
							TableColumnCreator.DATE_COLUMN_WIDTH, tableID){};
				}

				@Override
				public void tableColumnCreated(TableColumn column) {
					new ColumnSearchSubResultDLHistoryCompleted(column);
				}
			});
		
		tableManager.registerColumn(
				SubscriptionResultFilterable.class,
				ColumnSearchSubResultDLHistoryRemoved.COLUMN_ID,
				new TableColumnCoreCreationListener() {
					@Override
					public TableColumnCore 
					createTableColumnCore(
							Class<?> forDataSourceType, String tableID, String columnID) 
					{
						return new ColumnDateSizer(
								SubscriptionResultFilterable.class, columnID,
								TableColumnCreator.DATE_COLUMN_WIDTH, tableID){};
					}

					@Override
					public void tableColumnCreated(TableColumn column) {
						new ColumnSearchSubResultDLHistoryRemoved(column);
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
				
				try{
					ds_filter = (SubscriptionResultFilter)ds.getUserData( FILTER_KEY );
					
					if ( ds_filter == null ){
						
						ds_filter = ds.getFilters();
						
						ds.setUserData( FILTER_KEY, ds_filter );
					}
					
				}catch( Throwable e ){
					
					ds_filter = SubscriptionResultFilter.getTransientFilter();
				}
			}else{
				
				ds_filter = SubscriptionResultFilter.getTransientFilter();
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

			TableViewSWT<SubscriptionResultFilterable> tv = tv_subs_results;
					
			if ( tv != null ) {

				reconcileResults( subs );

				tv.runForAllRows(
					new TableGroupRowRunner() {
						@Override
						public void run(TableRowCore row) {
							row.invalidate( true );
						}
					});
			}
		}else if ( reason == CR_METADATA ){
			
			if ( pFilterUpdater != null ){
				
				Utils.execSWTThread(()->{
					
					pFilterUpdater.run();
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

			Collection<SubscriptionResultFilterable> existing_results = tv_subs_results.getDataSources( true );

			Map<String,SubscriptionResultFilterable>	existing_map = new HashMap<>();

			for ( SubscriptionResultFilterable result: existing_results ){

				existing_map.put( result.getID(), result );
			}

			SubscriptionResult[] current_results = ds.getResults( false );

			List<SubscriptionResultFilterable> new_results	= new ArrayList<>(current_results.length);

			for ( SubscriptionResult result: current_results ){

				SubscriptionResultFilterable existing = existing_map.remove( result.getID());

				if ( existing == null ){

					new_results.add( new SubscriptionResultFilterable( ds, result));

				}else{

					existing.updateFrom( result );
				}
			}

			if ( new_results.size() > 0 ){

				tv_subs_results.addDataSources( new_results.toArray( new SubscriptionResultFilterable[ new_results.size()]));
			}

			if ( existing_map.size() > 0 ){

				Collection<SubscriptionResultFilterable> to_remove = existing_map.values();

				tv_subs_results.removeDataSources( to_remove.toArray( new SubscriptionResultFilterable[ to_remove.size()]));

			}
		}
	}

	@Override
	public void
	subscriptionDownloaded(
		Subscription		subs )
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

					// remove in case already added
				
				ds.removeListener( this );
				
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

		return( super.skinObjectHidden(skinObject, params));
	}

	@Override
	public Object
	skinObjectDestroyed(
		SWTSkinObject 	skinObject,
		Object 			params )
	{
		try{
			hideView();
	
			return( super.skinObjectDestroyed(skinObject, params));
			
		}finally{
			
			if ( mdi_entry != null ){
			
				mdi_entry.removeToolbarEnabler(this);

				synchronized( activated_views ){
			
					activated_views.remove( mdi_entry );
				}
			}
		}
	}

	private void
	initTable(
		Composite control )
	{
		tv_subs_results = TableViewFactory.createTableViewSWT(
				SubscriptionResultFilterable.class,
				TABLE_SR,
				TABLE_SR,
				new TableColumnCore[0],
				ColumnSearchSubResultAge.COLUMN_ID,
				SWT.MULTI | SWT.FULL_SELECTION | SWT.VIRTUAL );

		TableColumnManager tcm = TableColumnManager.getInstance();
			
		tcm.setDefaultColumnNames( TABLE_SR,
				new String[] {
					ColumnSubResultNew.COLUMN_ID,
					ColumnSearchSubResultType.COLUMN_ID,
					ColumnSearchSubResultName.COLUMN_ID,
					ColumnSearchSubResultActions.COLUMN_ID,
					ColumnSearchSubResultSize.COLUMN_ID,
					ColumnSearchSubResultSeedsPeers.COLUMN_ID,
					ColumnSearchSubResultRatings.COLUMN_ID,
					ColumnSearchSubResultAge.COLUMN_ID,
					ColumnSearchSubResultAssetDate.COLUMN_ID,
					ColumnSearchSubResultRank.COLUMN_ID,
					ColumnSearchSubResultCategory.COLUMN_ID,
				});
	
		if ( !tcm.hasTableColumnSettings( TABLE_SR )){
			
			tcm.setDefaultSortColumnName(TABLE_SR, ColumnSearchSubResultAge.COLUMN_ID);
	
			TableColumnCore tcc = tcm.getTableColumnCore( TABLE_SR, ColumnSearchSubResultAge.COLUMN_ID );
	
			if ( tcc != null ){
	
				tcc.setDefaultSortAscending( true );
			}
		}

		SWTSkinObjectTextbox soFilterBox = (SWTSkinObjectTextbox) getSkinObject("filterbox");
		if (soFilterBox != null) {
			BubbleTextBox bubbleTextBox = soFilterBox.getBubbleTextBox();
			
			tv_subs_results.enableFilterCheck(bubbleTextBox, this);
			
			String tooltip = MessageText.getString("filter.tt.start");
			tooltip += MessageText.getString("column.filter.tt.line1");
			tooltip += MessageText.getString("column.filter.tt.line2");

			bubbleTextBox.setTooltip( tooltip );
			
			bubbleTextBox.setMessage( MessageText.getString( "Button.search2" ) );
		}

		tv_subs_results.setRowDefaultHeight(COConfigurationManager.getIntParameter( "Search Subs Row Height" ));

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

					SubscriptionResultFilterable rc = (SubscriptionResultFilterable)rows[0].getDataSource();

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

					SubscriptionResultFilterable rc = (SubscriptionResultFilterable)rows[i].getDataSource();

					last_selected_content.add( rc );

					byte[] hash = rc.getHash();

					if ( hash != null && hash.length > 0 ){

						SelectedContent sc = new SelectedContent(Base32.encode(hash), rc.getName());

						sc.setDownloadInfo(new DownloadUrlInfo(	getDownloadURI( rc )));

						valid.add(sc);
					}
				}

				TableRowCore	hash_row 	= null;
				byte[] 			hash 		= null;
				
				if ( rows.length == 1 ){
					
					hash_row = rows[0];
							
					SubscriptionResultFilterable rc = (SubscriptionResultFilterable)hash_row.getDataSource();
					
					hash = rc.getHash();
				}
				
				for ( TableRowCore row: tv_subs_results.getRows()){
						
					Color target = null;
					
					if ( row != hash_row && hash != null ){
							
						if ( Arrays.equals(((SubscriptionResultFilterable)row.getDataSource()).getHash(), hash )){
			
							target = Colors.blues[ Colors.BLUES_MIDLIGHT ];
						}
					}
					
					((TableRowSWT)row).requestBackgroundColor( colour_requester, target );
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

					final SubscriptionResultFilterable[] results = new SubscriptionResultFilterable[_related_content.length];

					System.arraycopy(_related_content, 0, results, 0, results.length);

					MenuItem item = new MenuItem(menu, SWT.PUSH);
					item.setText(MessageText.getString("label.copy.url.to.clip"));
					item.addSelectionListener(new SelectionAdapter() {
						@Override
						public void widgetSelected(SelectionEvent e) {

							StringBuffer buffer = new StringBuffer(1024);

							for ( SubscriptionResultFilterable result: results ){

								if ( buffer.length() > 0 ){
									buffer.append( "\r\n" );
								}

								buffer.append( getDownloadURI( result ));
							}
							ClipboardCopy.copyToClipBoard( buffer.toString());
						}
					});

					item.setEnabled( results.length > 0 );

					SearchSubsUtils.addMenu( ds, results, menu );

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

							SubscriptionResultFilterable[] content = new SubscriptionResultFilterable[ selected.length ];

							for ( int i=0;i<content.length;i++){

								content[i] = (SubscriptionResultFilterable)selected[i];
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

		col_filter_helper = new TableColumnFilterHelper<>( tv_subs_results, "srv:search" );
		
		tv_subs_results.initialize( table_parent );

		control.layout(true);
	}

	private void
	userDelete(
		SubscriptionResultFilterable[] results )
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

		Map<Subscription,List<String>>	result_map = new HashMap<>();
		
		for ( SubscriptionResultFilterable result: results ){

			Subscription subs = result.getSubscription();
			
			List<String> ids = result_map.get( subs );
			
			if ( ids == null ){
				
				ids = new ArrayList<>();
				
				result_map.put( subs, ids );
			}
			
			ids.add( result.getID());
		}
		
		for ( Map.Entry<Subscription,List<String>> entry: result_map.entrySet()){
		
			List<String> ids =  entry.getValue();
			
			entry.getKey().getHistory().deleteResults( ids.toArray( new String[ids.size()]));
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

			SubscriptionResultFilter	rf = ds_filter;
			
			if ( rf != null ){
			
				long new_v = rf.getDependenciesVersion();
				
				long old_v = ds_filter_version;
				
				if ( old_v != new_v ){
					
					ds_filter_version = new_v;
				
					if ( old_v != -1 ){
						
						refilter_dispatcher.dispatch();
						
						if ( pFilterUpdater != null ){
							
							Utils.execSWTThread(()->{
								
								pFilterUpdater.run();
							});
						}
					}
				}
			}
			
			tv_subs_results.refreshTable( false );
		}
	}

	@Override
	public boolean
	filterCheck(
		SubscriptionResultFilterable 	ds,
		String 							filter,
		boolean 						regex,
		boolean							confusable )
	{
		if (!isOurContent(ds)){

			return false;
		}

		return( SearchSubsUtils.filterCheck( col_filter_helper, ds, filter, regex, confusable ));
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

				SubscriptionResultFilterable[] related_content = new SubscriptionResultFilterable[_related_content.length];

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
		SubscriptionResultFilterable	result )
	{
		String torrent_url = (String)result.getTorrentLink();

		if ( torrent_url != null && torrent_url.length() > 0 ){

			return( torrent_url );
		}

		String uri = UrlUtils.getMagnetURI( result.getHash(), result.getName(), ds.getHistory().getDownloadNetworks());

		return( uri );
	}

}
