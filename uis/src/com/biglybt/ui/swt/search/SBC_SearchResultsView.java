/*
 * Created on Dec 7, 2016
 * Created by Paul Gardner
 *
 * Copyright 2016 Azureus Software, Inc.  All rights reserved.
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
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA 02111-1307, USA.
 */


package com.biglybt.ui.swt.search;

import java.net.URL;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

import com.biglybt.core.Core;
import com.biglybt.core.CoreFactory;
import com.biglybt.core.CoreRunningListener;
import com.biglybt.core.util.*;
import com.biglybt.pifimpl.local.PluginInitializer;
import com.biglybt.ui.UIFunctionsManager;
import com.biglybt.ui.UserPrompterResultListener;
import com.biglybt.ui.common.RememberedDecisionsManager;
import com.biglybt.ui.common.table.*;
import com.biglybt.ui.common.table.impl.TableColumnManager;
import com.biglybt.ui.selectedcontent.DownloadUrlInfo;
import com.biglybt.ui.selectedcontent.ISelectedContent;
import com.biglybt.ui.selectedcontent.SelectedContent;
import com.biglybt.ui.selectedcontent.SelectedContentManager;
import com.biglybt.ui.swt.columns.search.ColumnSearchResultSite;
import com.biglybt.ui.swt.columns.searchsubs.*;
import com.biglybt.ui.swt.components.BubbleTextBox;
import com.biglybt.ui.swt.search.SearchResultsTabArea.SearchQuery;
import com.biglybt.ui.swt.utils.SearchSubsUtils;
import com.biglybt.ui.swt.views.skin.VuzeMessageBox;
import com.biglybt.ui.swt.views.skin.VuzeMessageBoxListener;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.*;
import org.eclipse.swt.graphics.*;
import org.eclipse.swt.layout.FormData;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.layout.RowData;
import org.eclipse.swt.layout.RowLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Canvas;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Listener;
import org.eclipse.swt.widgets.Menu;
import org.eclipse.swt.widgets.MenuItem;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Spinner;
import org.eclipse.swt.widgets.Text;
import com.biglybt.core.config.COConfigurationManager;
import com.biglybt.core.internat.MessageText;
import com.biglybt.pif.ui.tables.TableColumn;
import com.biglybt.pif.ui.tables.TableColumnCreationListener;
import com.biglybt.ui.swt.Messages;
import com.biglybt.ui.swt.Utils;
import com.biglybt.ui.swt.mainwindow.ClipboardCopy;
import com.biglybt.ui.swt.mainwindow.Colors;
import com.biglybt.ui.swt.views.table.TableViewSWT;
import com.biglybt.ui.swt.views.table.TableViewSWTMenuFillListener;
import com.biglybt.ui.swt.views.table.impl.TableViewFactory;
import com.biglybt.ui.swt.views.table.utils.TableColumnFilterHelper;
import com.biglybt.core.metasearch.Engine;
import com.biglybt.core.metasearch.MetaSearchListener;
import com.biglybt.core.metasearch.MetaSearchManager;
import com.biglybt.core.metasearch.MetaSearchManagerFactory;
import com.biglybt.core.metasearch.Result;
import com.biglybt.core.metasearch.ResultListener;
import com.biglybt.core.metasearch.SearchParameter;
import com.biglybt.core.metasearch.impl.web.WebEngine;
import com.biglybt.core.subs.Subscription;
import com.biglybt.core.subs.util.SubscriptionResultFilterable;
import com.biglybt.core.subs.util.SearchSubsResultBase;
import com.biglybt.ui.UIFunctions;
import com.biglybt.ui.swt.skin.SWTSkin;
import com.biglybt.ui.swt.skin.SWTSkinCheckboxListener;
import com.biglybt.ui.swt.skin.SWTSkinObject;
import com.biglybt.ui.swt.skin.SWTSkinObjectCheckbox;
import com.biglybt.ui.swt.skin.SWTSkinObjectContainer;
import com.biglybt.ui.swt.skin.SWTSkinObjectText;
import com.biglybt.ui.swt.skin.SWTSkinObjectTextbox;
import com.biglybt.ui.swt.skin.SWTSkinObjectToggle;
import com.biglybt.ui.swt.skin.SWTSkinProperties;
import com.biglybt.ui.swt.skin.SWTSkinToggleListener;
import com.biglybt.ui.swt.imageloader.ImageLoader;


public class
SBC_SearchResultsView
	implements SearchResultsTabAreaBase, TableViewFilterCheck<SBC_SearchResult>, MetaSearchListener
{
	public static final String TABLE_SR = "SearchResults";

	private static boolean columnsAdded = false;

	private static Image[]	vitality_images;
	private static Image	ok_image;
	private static Image	fail_image;
	private static Image	auth_image;

	private SearchResultsTabArea		parent;

	private TableViewSWT<SBC_SearchResult> 				tv_subs_results;
	private TableColumnFilterHelper<SBC_SearchResult>	col_filter_helper;

	private Composite			table_parent;


	private final Object filter_lock = new Object();

	private Spinner spinMinSize;
	private Spinner spinMaxSize;
	private Text textWithKW;
	private Text textWithoutKW;

	private int minSize;
	private int maxSize;

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

	private final CopyOnWriteSet<String>	deselected_engines = new CopyOnWriteSet<>( false );

	private Composite engine_area;

	private List<SBC_SearchResult>	last_selected_content = new ArrayList<>();

	private Object 			search_lock	= new Object();
	private SearchInstance	current_search;

	private List<String> loadedImageIDs = new ArrayList<>();

	protected
	SBC_SearchResultsView(
		SearchResultsTabArea		_parent )
	{
		parent	= _parent;
	}

	private SWTSkinObject
	getSkinObject(
		String viewID )
	{
		return( parent.getSkinObject(viewID));
	}

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

		if ( vitality_images == null ){

			ImageLoader loader = ImageLoader.getInstance();

			vitality_images = loader.getImages( "image.sidebar.vitality.dots" );

			ok_image 	= loader.getImage( "tick_mark" );
			fail_image 	= loader.getImage( "progress_cancel" );
			auth_image 	= loader.getImage( "image.sidebar.vitality.auth" );
		}

		final SWTSkinObject soFilterArea = getSkinObject("filterarea");
		if (soFilterArea != null) {

			SWTSkinObjectToggle soFilterButton = (SWTSkinObjectToggle) getSkinObject("filter-button");
			if (soFilterButton != null) {

				boolean toggled = COConfigurationManager.getBooleanParameter( "Search View Filter Options Expanded", false );

				if ( toggled ){

					soFilterButton.setToggled( true );

					soFilterArea.setVisible( true );
				}

				soFilterButton.addSelectionListener(new SWTSkinToggleListener() {
					@Override
					public void toggleChanged(SWTSkinObjectToggle so, boolean toggled) {

						COConfigurationManager.setParameter( "Search View Filter Options Expanded", toggled );

						soFilterArea.setVisible(toggled);
						Utils.relayout(soFilterArea.getControl().getParent());
					}
				});
			}

			Composite parent = (Composite) soFilterArea.getControl();

			Composite filter_area = new Composite(parent, SWT.NONE);
			FormData fd = Utils.getFilledFormData();
			filter_area.setLayoutData(fd);

			GridLayout layout = new GridLayout();
			layout.marginBottom = layout.marginTop = layout.marginLeft = layout.marginRight = 0;
			filter_area.setLayout(layout);

			Label label;
			int sepHeight = 20;

			Composite cRow = new Composite(filter_area, SWT.NONE);
			cRow.setLayoutData(new GridData( GridData.FILL_HORIZONTAL ));

			RowLayout rowLayout = new RowLayout(SWT.HORIZONTAL);
			rowLayout.spacing = 5;
			rowLayout.marginBottom = rowLayout.marginTop = rowLayout.marginLeft = rowLayout.marginRight = 0;
			rowLayout.center = true;
			cRow.setLayout(rowLayout);


				// with/without keywords

			ImageLoader imageLoader = ImageLoader.getInstance();

			for ( int i=0;i<2;i++){

				final boolean with = i == 0;

				if ( !with ){

					label = new Label(cRow, SWT.VERTICAL | SWT.SEPARATOR);
					label.setLayoutData(new RowData(-1, sepHeight));
				}

				Composite cWithKW = new Composite(cRow, SWT.NONE);
				layout = new GridLayout(2, false);
				layout.marginWidth = 0;
				layout.marginBottom = layout.marginTop = layout.marginLeft = layout.marginRight = 0;
				cWithKW.setLayout(layout);
				//Label lblWithKW = new Label(cWithKW, SWT.NONE);
				//lblWithKW.setText(MessageText.getString(with?"SubscriptionResults.filter.with.words":"SubscriptionResults.filter.without.words"));
				Label lblWithKWImg = new Label(cWithKW, SWT.NONE);
				lblWithKWImg.setImage( imageLoader.getImage( with?"icon_filter_plus":"icon_filter_minus"));

				final Text textWidget = new Text(cWithKW, SWT.BORDER);
				if ( with ){
					textWithKW = textWidget;
				}else{
					textWithoutKW = textWidget;
				}
				textWidget.setMessage(MessageText.getString(with?"SubscriptionResults.filter.with.words":"SubscriptionResults.filter.without.words"));
				GridData gd = new GridData();
				gd.widthHint = 100;
				textWidget.setLayoutData( gd );
				textWidget.addModifyListener(
					new ModifyListener() {

						@Override
						public void modifyText(ModifyEvent e) {
							String text = textWidget.getText().toLowerCase( Locale.US );
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

			label = new Label(cRow, SWT.VERTICAL | SWT.SEPARATOR);
			label.setLayoutData(new RowData(-1, sepHeight));

			Composite cMinSize = new Composite(cRow, SWT.NONE);
			layout = new GridLayout(2, false);
			layout.marginWidth = 0;
			layout.marginBottom = layout.marginTop = layout.marginLeft = layout.marginRight = 0;
			cMinSize.setLayout(layout);
			Label lblMinSize = new Label(cMinSize, SWT.NONE);
			lblMinSize.setText(MessageText.getString("SubscriptionResults.filter.min_size"));
			spinMinSize = new Spinner(cMinSize, SWT.BORDER);
			spinMinSize.setMinimum(0);
			spinMinSize.setMaximum(100*kinb*kinb);	// 100 TB should do...
			spinMinSize.setSelection(minSize);
			spinMinSize.addListener(SWT.Selection, new Listener() {
				@Override
				public void handleEvent(Event event) {
					minSize = ((Spinner) event.widget).getSelection();
					refilter();
				}
			});

			// max size

			label = new Label(cRow, SWT.VERTICAL | SWT.SEPARATOR);
			label.setLayoutData(new RowData(-1, sepHeight));

			Composite cMaxSize = new Composite(cRow, SWT.NONE);
			layout = new GridLayout(2, false);
			layout.marginWidth = 0;
			layout.marginBottom = layout.marginTop = layout.marginLeft = layout.marginRight = 0;
			cMaxSize.setLayout(layout);
			Label lblMaxSize = new Label(cMaxSize, SWT.NONE);
			lblMaxSize.setText(MessageText.getString("SubscriptionResults.filter.max_size"));
			spinMaxSize = new Spinner(cMaxSize, SWT.BORDER);
			spinMaxSize.setMinimum(0);
			spinMaxSize.setMaximum(100*kinb*kinb);	// 100 TB should do...
			spinMaxSize.setSelection(maxSize);
			spinMaxSize.addListener(SWT.Selection, new Listener() {
				@Override
				public void handleEvent(Event event) {
					maxSize = ((Spinner) event.widget).getSelection();
					refilter();
				}
			});

			engine_area = new Composite(filter_area, SWT.NONE);
			engine_area.setLayoutData(new GridData( GridData.FILL_HORIZONTAL ));

			buildEngineArea( null  );

			parent.layout(true);
		}

		return null;
	}

	public Object skinObjectDestroyed(SWTSkinObject skinObject, Object params) {
		ImageLoader instance = ImageLoader.getInstance();
		for (String loadedImageID : loadedImageIDs) {
			instance.releaseImage(loadedImageID);
		}
		loadedImageIDs.clear();

		return null;
	}

	private void
	buildEngineArea(
		final SearchInstance		search )
	{
		if ( engine_area.isDisposed()){

			return;
		}

		final Engine[]	engines = search==null?new Engine[0]:search.getEngines();

		Utils.disposeComposite( engine_area, false );

		Arrays.sort(
			engines,
			new Comparator<Engine>()
			{
				@Override
				public int compare(Engine o1, Engine o2) {
					return( o1.getName().compareTo( o2.getName()));
				}
			});

		RowLayout rowLayout = new RowLayout(SWT.HORIZONTAL);
		rowLayout.spacing = 3;
		rowLayout.marginBottom = rowLayout.marginTop = rowLayout.marginLeft = rowLayout.marginRight = 0;
		rowLayout.pack = false;
		engine_area.setLayout(rowLayout);

		final Composite label_comp = new Composite( engine_area, SWT.NULL );

		GridLayout layout = new GridLayout();
		layout.marginBottom = layout.marginTop = layout.marginLeft = layout.marginRight = 1;
		label_comp.setLayout(layout);

		Label label = new Label( label_comp, SWT.NULL );
		Messages.setLanguageText( label, "label.show.results.from" );
		GridData grid_data = new GridData( SWT.LEFT, SWT.CENTER, true, true );

		label.setLayoutData( grid_data );

		final List<Button>		buttons 		= new ArrayList<>();
		final List<Label>		result_counts	= new ArrayList<>();
		final List<ImageLabel>	indicators		= new ArrayList<>();

		label.addMouseListener(
			new MouseAdapter() {

				@Override
				public void mouseDown(MouseEvent e) {

					deselected_engines.clear();

					for ( Button b: buttons ){

						b.setSelection( true );
					}

					refilter();
				}
			});

		for ( final Engine engine: engines ){

			final Composite engine_comp = new Composite( engine_area, SWT.NULL );

			layout = new GridLayout(3,false);
			layout.marginBottom = layout.marginTop = layout.marginLeft = layout.marginRight = 1;
			engine_comp.setLayout(layout);

			engine_comp.addPaintListener(
				new PaintListener() {

					@Override
					public void paintControl(PaintEvent e) {
						GC gc = e.gc;
						gc.setForeground( Colors.grey);

						Point size = engine_comp.getSize();

						gc.drawRectangle( new Rectangle( 0,  0, size.x-1, size.y-1 ));
					}
				});

			final Button button = new Button( engine_comp, SWT.CHECK );

			button.setData( engine );

			buttons.add( button );

			button.setText( engine.getName());

			button.setSelection( !deselected_engines.contains( engine.getUID()));

			Image image =
				getIcon(
					engine,
					new ImageLoadListener() {

						@Override
						public void imageLoaded(Image image) {
							button.setImage( image );
						}
					});

			if ( image != null ){

				try{
					button.setImage( image );
					
				}catch( Throwable e ){
					
				}
			}

			button.addSelectionListener(
				new SelectionAdapter() {

					@Override
					public void widgetSelected(SelectionEvent e){

						String id = engine.getUID();

						if ( button.getSelection()){

							deselected_engines.remove( id );

						}else{

							deselected_engines.add( id );
						}

						refilter();
					}
				});

			Menu menu = new Menu( button );

			button.setMenu( menu );

			MenuItem mi = new MenuItem( menu, SWT.PUSH );

			mi.setText( MessageText.getString( "label.this.site.only" ));

			mi.addSelectionListener(
				new SelectionAdapter() {
					@Override
					public void widgetSelected(SelectionEvent e) {

						deselected_engines.clear();

						button.setSelection( true );

						for ( Button b: buttons ){

							if ( b != button ){

								b.setSelection( false );

								deselected_engines.add(((Engine)b.getData()).getUID());
							}
						}

						refilter();
					}
				});

			MenuItem miCreateSubscription = new MenuItem(menu, SWT.PUSH);
			Messages.setLanguageText(miCreateSubscription, "menu.search.create.subscription");
			miCreateSubscription.addSelectionListener(new SelectionListener() {

				@Override
				public void widgetSelected(SelectionEvent e) {
					Map filterMap = buildFilterMap();
					SearchUtils.showCreateSubscriptionDialog(engine.getId(),
							current_search.sq.term, filterMap);
				}

				@Override
				public void widgetDefaultSelected(SelectionEvent e) {
				}
			});

			SearchUtils.addMenus( menu, engine, true );

			Label results = new Label( engine_comp, SWT.NULL );

			GC temp = new GC( results );
			Point size = temp.textExtent( "(888)" );
			temp.dispose();

			GridData gd = new GridData();

			gd.widthHint = size.x;

			results.setLayoutData( gd );

			result_counts.add( results );

			ImageLabel indicator = new ImageLabel( engine_comp, vitality_images[0] );

			indicators.add( indicator );

			indicator.addMouseListener(
				new MouseAdapter(){

					@Override
					public void
					mouseDown(
						MouseEvent e )
					{
						deselected_engines.clear();

						boolean	only_me_selected = button.getSelection();

						if ( only_me_selected ){

							for ( Button b: buttons ){

								if ( b != button ){

									if ( b.getSelection()){

										only_me_selected = false;
									}
								}
							}
						}

						if ( only_me_selected ){

							button.setSelection( false );

							deselected_engines.add( engine.getUID());

							for ( Button b: buttons ){

								if ( b != button ){

									b.setSelection( true );
								}
							}
						}else{

							button.setSelection( true );

							for ( Button b: buttons ){

								if ( b != button ){

									b.setSelection( false );

									deselected_engines.add(((Engine)b.getData()).getUID());
								}
							}
						}

						refilter();
					}
				});
		}

		Composite cAddEdit = new Composite(engine_area, SWT.NONE);
		cAddEdit.setLayout(new GridLayout());
		Button btnAddEdit = new Button(cAddEdit, SWT.PUSH);
		btnAddEdit.setLayoutData(new GridData(SWT.CENTER,0, true, true));
		Messages.setLanguageText(btnAddEdit, "button.add.edit.search.templates");
		btnAddEdit.addSelectionListener(new SelectionListener() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				UIFunctions functions = UIFunctionsManager.getUIFunctions();
				if (functions != null) {
					functions.viewURL(Constants.URL_SEARCH_ADDEDIT, null, "");
				}
			}

			@Override
			public void widgetDefaultSelected(SelectionEvent e) {
			}
		});

		Composite cCreateTemplate = new Composite(engine_area, SWT.NONE);
		cCreateTemplate.setLayout(new GridLayout());
		Button btnCreateTemplate = new Button(cCreateTemplate, SWT.PUSH);
		btnCreateTemplate.setLayoutData(new GridData(SWT.CENTER,0, true, true));
		Messages.setLanguageText(btnCreateTemplate, "menu.search.create.subscription");
		btnCreateTemplate.addSelectionListener(new SelectionListener() {

			@Override
			public void widgetSelected(SelectionEvent e) {
				Map filterMap = buildFilterMap();
				SearchUtils.showCreateSubscriptionDialog(-1,
						current_search.sq.term, buildFilterMap());
			}

			@Override
			public void widgetDefaultSelected(SelectionEvent e) {
			}
		});



		if ( engines.length > 0 ){

			new AEThread2( "updater") {

				int	ticks;
				int	image_index = 0;

				volatile boolean	running = true;


				@Override
				public void run() {

					while( running ){

						if ( label_comp.isDisposed()){

							return;
						}

						try{
							Thread.sleep(100);

						}catch( Throwable e ){
						}

						Utils.execSWTThread(
							new Runnable() {

								@Override
								public void
								run()
								{
									if ( label_comp.isDisposed()){

										return;
									}

									ticks++;

									image_index++;

									if ( image_index == vitality_images.length){

										image_index = 0;
									}

									boolean	do_results = ticks%5 == 0;

									boolean all_done = do_results;

									for ( int i=0;i<engines.length; i++ ){

										Object[] status = search.getEngineStatus( engines[i] );

										int state = (Integer)status[0];

										ImageLabel indicator = indicators.get(i);

										if ( state == 0 ){

											indicator.setImage( vitality_images[ image_index ]);

										}else if ( state == 1 ){

											indicator.setImage( ok_image );

										}else if ( state == 2 ){

											indicator.setImage( fail_image );

											String msg = (String)status[2];

											if ( msg != null ){

												Utils.setTT(indicator, msg );
											}
										}else{

											indicator.setImage( auth_image );
										}

										if ( do_results ){

											if ( state == 0 ){

												all_done = false;
											}

											String str = "(" + status[1] + ")";

											Label rc = result_counts.get(i);

											if ( !str.equals( rc.getText())){

												rc.setText( str );
											}
										}
									}

									if ( all_done ){

										running = false;
									}
								}
							});
					}
				}
			}.start();
		}
		engine_area.layout( true );
	}

	protected Map buildFilterMap() {
		Map<String, Object> mapFilter = new HashMap<>();
		if (without_keywords != null && without_keywords.length > 0) {
			mapFilter.put("text_filter_out", GeneralUtils.stringJoin(Arrays.asList(without_keywords), " "));
		}
		if (with_keywords != null && with_keywords.length > 0) {
			mapFilter.put("text_filter", GeneralUtils.stringJoin(Arrays.asList(with_keywords), " "));
		}
		long kinb = DisplayFormatters.getKinB();

		if (maxSize > 0) {
			mapFilter.put("max_size", maxSize * kinb * kinb);
		}
		if (minSize > 0) {
			mapFilter.put("min_size", minSize * kinb * kinb);
		}
		//mapFilter.put("category", "");
		return mapFilter;
	}

	private void
	resetFilters()
	{
		synchronized( filter_lock ){

			minSize		= 0;
			maxSize		= 0;

			with_keywords 		= new String[0];
			without_keywords 	= new String[0];

			deselected_engines.clear();
		}

		Utils.execSWTThread(
			new Runnable()
			{
				@Override
				public void
				run()
				{
					if ( spinMinSize != null && !spinMinSize.isDisposed()){
						spinMinSize.setSelection(0);
					}
					if ( spinMaxSize != null && !spinMaxSize.isDisposed()){
						spinMaxSize.setSelection(0);
					}
					if ( textWithKW != null && !textWithKW.isDisposed()){
						textWithKW.setText("");
					}
					if ( textWithoutKW != null && !textWithoutKW.isDisposed()){
						textWithoutKW.setText("");
					}

					if ( tv_subs_results != null) {
						tv_subs_results.setFilterText("", false);
					}
				}
			});
	}

	private void
	setSearchEngines(
		final SearchInstance		si )
	{
		Utils.execSWTThread(
			new Runnable()
			{
				@Override
				public void
				run()
				{
					buildEngineArea( si );
				}
			});
	}

	private boolean
	isOurContent(
		SBC_SearchResult result)
	{
		long	size = result.getSize();

		long kInB = DisplayFormatters.getKinB();
		long mInB = kInB*kInB;

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

		String engine_id = result.getEngine().getUID();

		if ( deselected_engines.contains( engine_id )){

			return( false );
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
		synchronized( SBC_SearchResultsView.class ){

			if ( columnsAdded ){

				return;
			}

			columnsAdded = true;
		}

		TableColumnManager tableManager = TableColumnManager.getInstance();

		tableManager.registerColumn(
			SBC_SearchResult.class,
			ColumnSearchSubResultType.COLUMN_ID,
				new TableColumnCreationListener() {

					@Override
					public void tableColumnCreated(TableColumn column) {
						new ColumnSearchSubResultType(column);
					}
				});
		tableManager.registerColumn(
			SBC_SearchResult.class,
			ColumnSearchSubResultName.COLUMN_ID,
				new TableColumnCreationListener() {

					@Override
					public void tableColumnCreated(TableColumn column) {
						new ColumnSearchSubResultName(column);
					}
				});

		tableManager.registerColumn(
			SBC_SearchResult.class,
			ColumnSearchSubResultActions.COLUMN_ID,
				new TableColumnCreationListener() {

					@Override
					public void tableColumnCreated(TableColumn column) {
						new ColumnSearchSubResultActions(column);
					}
				});

		tableManager.registerColumn(
			SBC_SearchResult.class,
			ColumnSearchSubResultSize.COLUMN_ID,
				new TableColumnCreationListener() {

					@Override
					public void tableColumnCreated(TableColumn column) {
						new ColumnSearchSubResultSize(column);
					}
				});

		tableManager.registerColumn(
			SBC_SearchResult.class,
			ColumnSearchSubResultSeedsPeers.COLUMN_ID,
				new TableColumnCreationListener() {

					@Override
					public void tableColumnCreated(TableColumn column) {
						new ColumnSearchSubResultSeedsPeers(column);
					}
				});

		tableManager.registerColumn(
			SBC_SearchResult.class,
			ColumnSearchSubResultRatings.COLUMN_ID,
				new TableColumnCreationListener() {

					@Override
					public void tableColumnCreated(TableColumn column) {
						new ColumnSearchSubResultRatings(column);
					}
				});

		tableManager.registerColumn(
			SBC_SearchResult.class,
			ColumnSearchSubResultAge.COLUMN_ID,
				new TableColumnCreationListener() {

					@Override
					public void tableColumnCreated(TableColumn column) {
						new ColumnSearchSubResultAge(column);
					}
				});

		tableManager.registerColumn(
			SBC_SearchResult.class,
			ColumnSearchSubResultRank.COLUMN_ID,
				new TableColumnCreationListener() {

					@Override
					public void tableColumnCreated(TableColumn column) {
						new ColumnSearchSubResultRank(column);
					}
				});

		tableManager.registerColumn(
			SBC_SearchResult.class,
			ColumnSearchSubResultCategory.COLUMN_ID,
				new TableColumnCreationListener() {

					@Override
					public void tableColumnCreated(TableColumn column) {
						new ColumnSearchSubResultCategory(column);
					}
				});

		tableManager.registerColumn(
			SBC_SearchResult.class,
			ColumnSearchResultSite.COLUMN_ID,
				new TableColumnCreationListener() {

					@Override
					public void tableColumnCreated(TableColumn column) {
						new ColumnSearchResultSite(column);
					}
				});

		tableManager.registerColumn(
			SBC_SearchResult.class,
			ColumnSearchSubResultHash.COLUMN_ID,
				new TableColumnCreationListener() {

					@Override
					public void tableColumnCreated(TableColumn column) {
						new ColumnSearchSubResultHash(column);
					}
				});

		tableManager.registerColumn(
			SBC_SearchResult.class,
			ColumnSearchSubResultExisting.COLUMN_ID,
				new TableColumnCreationListener() {

					@Override
					public void tableColumnCreated(TableColumn column) {
						new ColumnSearchSubResultExisting(column);
					}
				});
	}

	@Override
	public void
	showView()
	{
		SWTSkinObject so_list = getSkinObject("search-results-list");

		if ( so_list != null ){

			MetaSearchManagerFactory.getSingleton().getMetaSearch().addListener( this );

			so_list.setVisible(true);

			initTable((Composite) so_list.getControl());
		}
	}

	@Override
	public void
	refreshView()
	{
		if ( tv_subs_results != null ){

			tv_subs_results.refreshTable( false );
		}
	}

	@Override
	public void
	hideView()
	{
		synchronized( search_lock ){

			if ( current_search != null ){

				current_search.cancel();

				current_search = null;
			}
		}

		MetaSearchManagerFactory.getSingleton().getMetaSearch().removeListener( this );

		Utils.disposeSWTObjects(new Object[] {
			table_parent,
		});
	}

	@Override
	public void
	engineAdded(
		Engine		engine )
	{
		if ( engine.isActive()){

			autoSearchAgain();
		}
	}

	@Override
	public void
	engineUpdated(
		Engine		engine )
	{
	}

	@Override
	public void
	engineRemoved(
		Engine		engine )
	{
		SearchInstance si = current_search;

		if ( si != null ){

			if ( si.getEngineIndex( engine ) >= 0 ){

				autoSearchAgain();
			}
		}
	}

	@Override
	public void
	engineStateChanged(
		Engine 		engine )
	{
		SearchInstance si = current_search;

		if ( si != null ){

			if ( si.getEngineIndex( engine ) >= 0 ){

				autoSearchAgain();
			}
		}
	}

	private void
	initTable(
		Composite control )
	{
		tv_subs_results = TableViewFactory.createTableViewSWT(
				SBC_SearchResult.class,
				TABLE_SR,
				TABLE_SR,
				new TableColumnCore[0],
				ColumnSearchSubResultName.COLUMN_ID,
				SWT.MULTI | SWT.FULL_SELECTION | SWT.VIRTUAL );

		TableColumnManager tableManager = TableColumnManager.getInstance();

		tableManager.setDefaultColumnNames( TABLE_SR,
				new String[] {
				ColumnSearchSubResultType.COLUMN_ID,
				ColumnSearchSubResultName.COLUMN_ID,
				ColumnSearchSubResultActions.COLUMN_ID,
				ColumnSearchSubResultSize.COLUMN_ID,
				ColumnSearchSubResultSeedsPeers.COLUMN_ID,
				ColumnSearchSubResultRatings.COLUMN_ID,
				ColumnSearchSubResultAge.COLUMN_ID,
				ColumnSearchSubResultRank.COLUMN_ID,
				ColumnSearchSubResultCategory.COLUMN_ID,
				ColumnSearchResultSite.COLUMN_ID,
			});

		tableManager.setDefaultSortColumnName(TABLE_SR, ColumnSearchSubResultRank.COLUMN_ID);


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

					SBC_SearchResult rc = (SBC_SearchResult)rows[0].getDataSource();

					downloadAction( rc );
				}
			}

			private void
			updateSelectedContent()
			{
				TableRowCore[] rows = tv_subs_results.getSelectedRows();

				ArrayList<ISelectedContent>	valid = new ArrayList<>();

				last_selected_content.clear();

				for (int i=0;i<rows.length;i++){

					SBC_SearchResult rc = (SBC_SearchResult)rows[i].getDataSource();

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

		tv_subs_results.addMenuFillListener(
			new TableViewSWTMenuFillListener()
			{
				@Override
				public void
				fillMenu(String sColumnName, Menu menu)
				{
					Object[] _related_content = tv_subs_results.getSelectedDataSources().toArray();

					final SBC_SearchResult[] results = new SBC_SearchResult[_related_content.length];

					System.arraycopy(_related_content, 0, results, 0, results.length);

					MenuItem item = new MenuItem(menu, SWT.PUSH);
					item.setText(MessageText.getString("label.copy.url.to.clip"));
					item.addSelectionListener(new SelectionAdapter() {
						@Override
						public void widgetSelected(SelectionEvent e) {

							StringBuffer buffer = new StringBuffer(1024);

							for ( SBC_SearchResult result: results ){

								if ( buffer.length() > 0 ){
									buffer.append( "\r\n" );
								}

								buffer.append( getDownloadURI( result ));
							}
							ClipboardCopy.copyToClipBoard( buffer.toString());
						}
					});

					item.setEnabled( results.length > 0 );

					SearchSubsUtils.addMenu( null, results, menu );

					new MenuItem(menu, SWT.SEPARATOR );

					if ( results.length == 1 ){

						if ( SearchSubsUtils.addMenu( results[0], menu )){

							new MenuItem(menu, SWT.SEPARATOR );
						}
					}
				}

				@Override
				public void
				addThisColumnSubMenu(
					String columnName, Menu menuThisColumn)
				{
				}
			});

		col_filter_helper = new TableColumnFilterHelper<>( tv_subs_results, "srv:search" );

		tv_subs_results.initialize( table_parent );

		control.layout(true);
	}

	protected void
	invalidate(
		SBC_SearchResult		result )
	{
		TableRowCore row = tv_subs_results.getRow( result );

		if ( row != null ){

			row.invalidate( true );
		}
	}

	@Override
	public boolean
	filterCheck(
		SBC_SearchResult 	ds,
		String 				filter,
		boolean 			regex,
		boolean				confusable )
	{
		if (!isOurContent(ds)){

			return false;
		}

		return( SearchSubsUtils.filterCheck( col_filter_helper, ds, filter, regex, confusable ));
	}

	@Override
	public void filterSet(String filter) {
	}

	private void
	autoSearchAgain()
	{
		SearchInstance si = current_search;

		if ( si != null ){

			anotherSearch( si.getSearchQuery());
		}
	}

	@Override
	public void
	anotherSearch(
		SearchQuery sq )
	{
		synchronized( search_lock ){

			if ( current_search != null ){

				current_search.cancel();
			}

			resetFilters();

			current_search = new SearchInstance( sq );
		}
	}

	public String
	getDownloadURI(
		SBC_SearchResult	result )
	{
		String torrent_url = (String)result.getTorrentLink();

		if ( torrent_url != null && torrent_url.length() > 0 ){

			return( torrent_url );
		}

		String uri = UrlUtils.getMagnetURI( result.getHash(), result.getName(), new String[]{ AENetworkClassifier.AT_PUBLIC });

		return( uri );
	}

	public Image
	getIcon(
		final SBC_SearchResult		result )
	{
		return( getIcon( result.getEngine(), result ));
	}

	public Image
	getIcon(
		Engine					engine,
		ImageLoadListener		result )
	{
		return getIconSupport( engine, result );
	}
	
	private Image
	getIconSupport(
		Engine					engine,
		ImageLoadListener		result )
	{
		String icon = engine.getIcon();
		if ( icon == null ){
			return null;
		}

		return ImageLoader.getInstance().getUrlImage(
			icon,
			new Point(0, 16),
			(image, key, returnedImmediately) -> {
				loadedImageIDs.add(key);

				if (returnedImmediately) {
					return;
				}

				result.imageLoaded( image );
			});
	}

	@Override
	public int
	getResultCount()
	{
		SearchInstance ci = current_search;

		if ( ci == null ){

			return( -1 );
		}

		return( current_search.getResultCount());
	}

	public interface
	ImageLoadListener
	{
		public void
		imageLoaded(
			Image		image );
	}

	private class
	SearchInstance
		implements ResultListener
	{
		private final SearchQuery		sq;
		private final Engine[]			engines;
		private final Object[][]		engine_status;

		private boolean	_cancelled;

		private Set<Engine>	pending = new HashSet<>();

		private AtomicInteger	result_count = new AtomicInteger();

		private
		SearchInstance(
			SearchQuery		_sq )
		{
			sq		= _sq;

			tv_subs_results.removeAllTableRows();

			SWTSkinObjectText title = (SWTSkinObjectText)parent.getSkinObject("title");

			if ( title != null ){

				title.setText( MessageText.getString( "search.results.view.title", new String[]{ sq.term }));
			}

			MetaSearchManager metaSearchManager = MetaSearchManagerFactory.getSingleton();

			List<SearchParameter>	sps = new ArrayList<>();

			sps.add( new SearchParameter( "s", sq.term ));

			SearchParameter[] parameters = sps.toArray(new SearchParameter[ sps.size()] );

			Map<String,String>	context = new HashMap<>();

			context.put( Engine.SC_FORCE_FULL, "true" );

			context.put( Engine.SC_BATCH_PERIOD, "250" );

			context.put( Engine.SC_REMOVE_DUP_HASH, "true" );

			String headers = null;	// use defaults

			parent.setBusy( true );

			synchronized( pending ){

				engines = metaSearchManager.getMetaSearch().search( this, parameters, headers, context, 500 );

				engine_status 	= new Object[engines.length][];

				for ( int i=0;i<engine_status.length;i++){

					engine_status[i] = new Object[]{ 0, 0, null };
				}

				setSearchEngines( this );

				if ( engines.length == 0 ){

					parent.setBusy( false );

				}else{

					pending.addAll( Arrays.asList( engines ));
				}
			}
		}

		protected SearchQuery
		getSearchQuery()
		{
			return( sq );
		}

		protected Engine[]
		getEngines()
		{
			synchronized( pending ){

				return( engines );
			}
		}

		protected int
		getEngineIndex(
			Engine	e )
		{
			synchronized( pending ){

				for ( int i=0;i<engines.length;i++ ){
					if ( engines[i] == e ){
						return( i );
					}
				}
				return( -1 );
			}
		}

		protected Object[]
		getEngineStatus(
			Engine		engine )
		{
			int i = getEngineIndex( engine );

			if ( i >= 0 ){

				return( engine_status[i] );

			}else{

				return( null );
			}
		}

		protected void
		cancel()
		{
			_cancelled	= true;

			parent.setBusy( false );
		}

		private boolean
		isCancelled()
		{
			synchronized( search_lock ){

				return( _cancelled );
			}
		}

		@Override
		public void
		contentReceived(
			Engine engine,
			String content )
		{
		}

		@Override
		public void
		matchFound(
			Engine 		engine,
			String[] 	fields )
		{
		}

		@Override
		public void
		engineFailed(
			Engine 		engine,
			Throwable 	e )
		{
			if ( isCancelled()){

				return;
			}

			engineDone( engine, 2, Debug.getNestedExceptionMessage( e ));
		}

		@Override
		public void
		engineRequiresLogin(
			Engine 		engine,
			Throwable 	e )
		{
			if ( isCancelled()){

				return;
			}

			engineDone( engine, 3, null );
		}

		@Override
		public void
		resultsComplete(
			Engine engine )
		{
			if ( isCancelled()){

				return;
			}

			engineDone( engine, 1, null );
		}

		private void
		engineDone(
			Engine		engine,
			int			state,
			String		msg )
		{
			int	i = getEngineIndex( engine );

			if ( i >= 0 ){

				engine_status[i][0] = state;
				engine_status[i][2] = msg;
			}

			synchronized( pending ){

				pending.remove( engine );

				if ( pending.isEmpty()){

					parent.setBusy( false );
				}
			}
		}

		@Override
		public void
		resultsReceived(
			Engine 		engine,
			Result[] 	results)
		{
			synchronized( search_lock ){

				if ( isCancelled()){

					return;
				}

				int	index = getEngineIndex( engine );

				if ( index >= 0 ){

					int count = (Integer)engine_status[index][1];

					engine_status[index][1] = count + results.length;
				}

				SBC_SearchResult[]	data_sources = new  SBC_SearchResult[ results.length ];

				for ( int i=0;i<results.length;i++){

					data_sources[i] = new SBC_SearchResult( SBC_SearchResultsView.this, engine, results[i] );
				}

				tv_subs_results.addDataSources( data_sources );

				tv_subs_results.processDataSourceQueueSync();

				result_count.addAndGet( results.length );

				parent.resultsFound();
			}
		}

		protected int
		getResultCount()
		{
			return( result_count.get());
		}
	}

	static class
	ImageLabel
		extends Canvas implements PaintListener
	{
		private Image		image;

		public
		ImageLabel(
			Composite 	parent,
			Image		_image )
		{
			super( parent, SWT.DOUBLE_BUFFERED );

			image	= _image;

			addPaintListener(this);
		}

		@Override
		public void
		paintControl(
			PaintEvent e)
		{
			if ( !image.isDisposed()){

				Point size = getSize();

				Rectangle rect = image.getBounds();

				int x_offset = Math.max( 0, ( size.x - rect.width )/2 );
				int y_offset = Math.max( 0, ( size.y - rect.height )/2 );

				e.gc.drawImage( image, x_offset, y_offset );
			}
		}


		@Override
		public Point
		computeSize(
			int 	wHint,
			int 	hHint,
			boolean changed )
		{
			if ( image.isDisposed()){
				return( new Point(0,0));
			}

			Rectangle rect = image.getBounds();

			return( new Point( rect.width, rect.height ));
		}

		private void
		setImage(
			Image	_image )
		{
			if ( _image == image ){

				return;
			}

			image	= _image;

			redraw();
		}
	}

	public static void
	downloadAction(
		final SearchSubsResultBase entry )
	{
		String link = entry.getTorrentLink();

		if ( link == null ){
			
			return;
		}
		
		if ( link.startsWith( "chat:" )){

			Utils.launch( link );

			return;
		}

		showDownloadFTUX(
			entry,
			new UserPrompterResultListener()
			{
				@Override
				public void prompterClosed(int result) {

					if ( result == 0 ){
						String referer_str = null;

						String torrentUrl = entry.getTorrentLink();

						try{
							Map headers = UrlUtils.getBrowserHeaders( referer_str );

							if ( entry instanceof SubscriptionResultFilterable ){

								SubscriptionResultFilterable sub_entry = (SubscriptionResultFilterable)entry;

								Subscription subs = sub_entry.getSubscription();

								try{
									Engine engine = subs.getEngine();

									if ( engine != null && engine instanceof WebEngine ){

										WebEngine webEngine = (WebEngine) engine;

										if ( webEngine.isNeedsAuth()){

											headers.put( "Cookie",webEngine.getCookies());
										}
									}
								}catch( Throwable e ){

									Debug.out( e );
								}

								subs.addPotentialAssociation( sub_entry.getID(), torrentUrl );

							}else{

								SBC_SearchResult	search_entry = (SBC_SearchResult)entry;

								Engine engine = search_entry.getEngine();

								if ( engine != null ){

									engine.addPotentialAssociation( torrentUrl );

									if ( engine instanceof WebEngine ){

										WebEngine webEngine = (WebEngine) engine;

										if ( webEngine.isNeedsAuth()){

											headers.put( "Cookie",webEngine.getCookies());
										}
									}
								}
							}

							byte[] torrent_hash = entry.getHash();

							if ( torrent_hash != null ){

								if ( torrent_hash != null && !torrentUrl.toLowerCase().startsWith( "magnet" )){

									String title = entry.getName();

									String magnet = UrlUtils.getMagnetURI( torrent_hash, title, null );

									headers.put( "X-Alternative-URI-1", magnet );
								}
							}

							PluginInitializer.getDefaultInterface().getDownloadManager().addDownload(
									new URL(torrentUrl),
									headers );

						}catch( Throwable e ){

							Debug.out( e );
						}
					}
				}
		});
	}

	protected static void
	showDownloadFTUX(
		SearchSubsResultBase				entry,
		final UserPrompterResultListener 	listener )
	{
		if ( entry instanceof SubscriptionResultFilterable ){

			listener.prompterClosed( 0 );

			return;
		}

		if ( RememberedDecisionsManager.getRememberedDecision( "searchsubs.dl.ftux" ) == 1 ){

			listener.prompterClosed( 0 );

			return;
		}

		final VuzeMessageBox box = new VuzeMessageBox(
				MessageText.getString("searchsubs.dl.ftux.title"), null, new String[] {
					MessageText.getString("Button.ok"),
					MessageText.getString("Button.cancel"),
				}, 0);
		box.setSubTitle(MessageText.getString("searchsubs.dl.ftux.heading"));

		final boolean[]	check_state = new boolean[]{ true };

		box.setListener(new VuzeMessageBoxListener() {
			@Override
			public void shellReady(Shell shell, SWTSkinObjectContainer soExtra) {
				SWTSkin skin = soExtra.getSkin();
				addResourceBundle(skin, "com/biglybt/ui/swt/columns/searchsubs/",
						"skin3_dl_ftux");

				String id = "searchsubs.dlftux.shell";
				skin.createSkinObject(id, id, soExtra);

				final SWTSkinObjectCheckbox cb = (SWTSkinObjectCheckbox) skin.getSkinObject("agree-checkbox");
				cb.setChecked( true );
				cb.addSelectionListener(new SWTSkinCheckboxListener() {
					@Override
					public void checkboxChanged(SWTSkinObjectCheckbox so, boolean checked) {
						check_state[0] = checked;
					}
				});
			}
		});

		box.open(
			new UserPrompterResultListener()
			{
				@Override
				public void prompterClosed(int result){

					if ( result == 0 && check_state[0] ){

						RememberedDecisionsManager.setRemembered( "searchsubs.dl.ftux", 1 );
					}

					listener.prompterClosed(result);
				}

			});
	}

	private static void addResourceBundle(SWTSkin skin, String path, String name) {
		String sFile = path + name;
		ClassLoader loader = ColumnSearchSubResultActions.class.getClassLoader();
		SWTSkinProperties skinProperties = skin.getSkinProperties();
		try {
			ResourceBundle subBundle = ResourceBundle.getBundle(sFile,
					Locale.getDefault(), loader);
			skinProperties.addResourceBundle(subBundle, path, loader);
		} catch (MissingResourceException mre) {
			Debug.out(mre);
		}
	}
}
