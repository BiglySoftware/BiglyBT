/*
 * Created on Jan 3, 2009
 *
 * Copyright (C) Azureus Software, Inc, All Rights Reserved.
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

package com.biglybt.ui.swt.views.columnsetup;

import java.util.*;
import java.util.List;
import java.util.function.Consumer;
import java.util.regex.Pattern;

import org.eclipse.swt.SWT;
import org.eclipse.swt.dnd.*;
import org.eclipse.swt.events.*;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.layout.*;
import org.eclipse.swt.widgets.*;

import com.biglybt.core.config.COConfigurationManager;
import com.biglybt.core.internat.MessageText;
import com.biglybt.core.util.*;
import com.biglybt.ui.UserPrompterResultListener;
import com.biglybt.ui.common.table.*;
import com.biglybt.ui.common.table.impl.TableColumnManager;
import com.biglybt.ui.common.updater.UIUpdatable;
import com.biglybt.ui.swt.Messages;
import com.biglybt.ui.swt.Utils;
import com.biglybt.ui.swt.Utils.ColorButton;
import com.biglybt.ui.swt.components.BubbleTextBox;
import com.biglybt.ui.swt.components.shell.ShellFactory;
import com.biglybt.ui.swt.imageloader.ImageLoader;
import com.biglybt.ui.swt.mainwindow.ClipboardCopy;
import com.biglybt.ui.swt.mainwindow.Colors;
import com.biglybt.ui.swt.shells.GCStringPrinter;
import com.biglybt.ui.swt.shells.MessageBoxShell;
import com.biglybt.ui.swt.uiupdater.UIUpdaterSWT;
import com.biglybt.ui.swt.utils.FontUtils;
import com.biglybt.ui.swt.views.table.TableRowSWT;
import com.biglybt.ui.swt.views.table.TableViewSWT;
import com.biglybt.ui.swt.views.table.impl.TableViewFactory;

import com.biglybt.pif.ui.tables.TableColumn;
import com.biglybt.pif.ui.tables.TableColumnInfo;
import com.biglybt.pif.ui.tables.TableRow;

/**
 * @author TuxPaper
 * @created Jan 3, 2009
 *
 */
public class TableColumnSetupWindow
	implements UIUpdatable
{
	private static final String TABLEID_AVAIL = "ColumnSetupAvail";

	private static final String TABLEID_CHOSEN = "ColumnSetupChosen";

	private static final boolean CAT_BUTTONS = true;

	private Shell shell;

	private TableViewSWT<TableColumn> tvAvail;

	private final String forTableID;

	private final Class<?> forDataSourceType;

	private Composite cTableAvail;

	private Composite cCategories;

	private TableViewSWT<TableColumnCore> tvChosen;

	private Composite cTableChosen;

	private final TableColumnCore[] columnsCurrentOrder;
	private final TableColumnCore[] columnsOriginalOrder;

	private final TableRow sampleRow;

	private DragSourceListener dragSourceListener;

	private final TableStructureModificationListener<?> listener;

	protected boolean apply = false;

	private Button[] radProficiency = new Button[3];

	private Map<TableColumnCore, Boolean> mapNewVisibility = new HashMap<>();

	private ArrayList<TableColumnCore> listColumnsNoCat;

	private ArrayList<String> listCats;

	private Combo comboFilter;

	private Group cPickArea;

	private Button btnApply;
	private Button btnExport;
		
	public 
	TableColumnSetupWindow(
		Class<?> 		forDataSourceType, 
		String 			_tableID,
		TableColumnCore	selectedColumn,
		TableRow 		sampleRow, 
		TableStructureModificationListener<?> _listener) 
	{
		this.sampleRow = sampleRow;
		this.listener = _listener;
		FormData fd;
		this.forDataSourceType = forDataSourceType;
		forTableID = _tableID;

		dragSourceListener = new DragSourceListener() {
			private TableColumnCore tableColumn;

			@Override
			public void dragStart(DragSourceEvent event) {
				event.doit = true;

				if (!(event.widget instanceof DragSource)) {
					event.doit = false;
					return;
				}

				TableView<?> tv = (TableView<?>) ((DragSource) event.widget).getData("tv");
				// drag start happens a bit after the mouse moves, so the
				// cursor location isn't accurate
				//Point cursorLocation = event.display.getCursorLocation();
				//cursorLocation = tv.getTableComposite().toControl(cursorLocation);
				//TableRowCore row = tv.getRow(cursorLocation.x, cursorLocation.y);
				//System.out.println("" + event.x + ";" + event.y + "/" + cursorLocation);

				// event.x and y doesn't always return correct values!
				//TableRowCore row = tv.getRow(event.x, event.y);

				TableRowCore row = tv.getFocusedRow();
				if (row == null) {
					event.doit = false;
					return;
				}

				tableColumn = (TableColumnCore) row.getDataSource();


				if (event.image != null && !Constants.isLinux) {
					try {
  					GC gc = new GC(event.image);
  					try {
    					Rectangle bounds = event.image.getBounds();
    					gc.fillRectangle(bounds);
    					String title = MessageText.getString(
    							tableColumn.getTitleLanguageKey(), tableColumn.getName());
    					String s = title
    							+ " Column will be placed at the location you drop it, shifting other columns down";
    					GCStringPrinter sp = new GCStringPrinter(gc, s, bounds, false, false,
    							SWT.CENTER | SWT.WRAP);
    					sp.calculateMetrics();
    					if (sp.isCutoff()) {
    						GCStringPrinter.printString(gc, title, bounds, false, false,
    								SWT.CENTER | SWT.WRAP);
    					} else {
    						sp.printString();
    					}
  					} finally {
  						gc.dispose();
  					}
					} catch (Throwable t) {
						//ignore
					}
				}
			}

			@Override
			public void dragSetData(DragSourceEvent event) {
				if (!(event.widget instanceof DragSource)) {
					return;
				}

				TableView<?> tv = (TableView<?>) ((DragSource) event.widget).getData("tv");
				event.data = "" + (tv == tvChosen ? "c" : "a");
			}

			@Override
			public void dragFinished(DragSourceEvent event) {
			}
		};

		String baseID = Utils.getBaseViewID( _tableID );
		
		String tableName = MessageText.getString(baseID + "View.header",
				(String) null);
		if (tableName == null) {
			tableName = MessageText.getString(baseID + "View.title.full",
					(String) null);
			if (tableName == null) {
				tableName = baseID;
			}
		}

		TableColumnManager tcm = TableColumnManager.getInstance();

		columnsCurrentOrder = tcm.getAllTableColumnCoreAsArray(forDataSourceType, forTableID);
		Arrays.sort(columnsCurrentOrder,TableColumnManager.getTableColumnOrderComparator());
		
		columnsOriginalOrder = new TableColumnCore[columnsCurrentOrder.length];
		System.arraycopy(columnsCurrentOrder, 0, columnsOriginalOrder, 0,columnsCurrentOrder.length);
		
		for (int i = 0; i < columnsCurrentOrder.length; i++) {
			boolean visible = columnsCurrentOrder[i].isVisible();
			mapNewVisibility.put(columnsCurrentOrder[i], Boolean.valueOf(visible));
		}
		
		
		shell = ShellFactory.createShell(Utils.findAnyShell(), SWT.SHELL_TRIM);
		Utils.setShellIcon(shell);
		FormLayout formLayout = new FormLayout();
		shell.setText(MessageText.getString("ColumnSetup.title", new String[] {
			tableName
		}));
		shell.setLayout(formLayout);
		shell.setSize(780, 550);

		shell.addTraverseListener(new TraverseListener() {
			@Override
			public void keyTraversed(TraverseEvent e) {
				if (e.detail == SWT.TRAVERSE_ESCAPE) {
					shell.dispose();
				}
			}
		});
		shell.addDisposeListener(new DisposeListener() {
			@Override
			public void widgetDisposed(DisposeEvent e) {
				close();
			}
		});

		Label topInfo = new Label(shell, SWT.WRAP);
		Messages.setLanguageText(topInfo, "ColumnSetup.explain");

		fd = Utils.getFilledFormData();
		fd.left.offset = 5;
		fd.top.offset = 5;
		fd.bottom = null;
		topInfo.setLayoutData(fd);

		Button btnOk = new Button(shell, SWT.PUSH);
		Messages.setLanguageText(btnOk, "Button.ok");
		btnOk.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				apply = true;
				shell.dispose();
			}
		});

		cPickArea = new Group(shell, SWT.NONE);
		cPickArea.setLayout(new FormLayout());


		final ExpandBar expandFilters = new ExpandBar(cPickArea, SWT.NONE);
		expandFilters.setSpacing(1);

		final Composite cFilterArea = new Composite(expandFilters, SWT.NONE);
		cFilterArea.setLayout(new FormLayout());

		Group cResultArea = new Group(shell, SWT.NONE);
		Messages.setLanguageText(cResultArea, "ColumnSetup.chosencolumns");
		cResultArea.setLayout(new FormLayout());

		Composite cResultButtonArea = new Composite(cResultArea, SWT.NONE);
		cResultButtonArea.setLayout(new FormLayout());

		Composite cColumnButtonArea = new Composite(cResultArea, SWT.NONE);
		cColumnButtonArea.setLayout(new FormLayout());

		tvAvail = createTVAvail();

		cTableAvail = new Composite(cPickArea, SWT.NO_FOCUS);
		GridLayout gridLayout = new GridLayout();
		gridLayout.marginWidth = gridLayout.marginHeight = 0;
		cTableAvail.setLayout(gridLayout);

		BubbleTextBox bubbleTextBox = new BubbleTextBox(cTableAvail, SWT.BORDER | SWT.SEARCH | SWT.ICON_SEARCH | SWT.ICON_CANCEL | SWT.SINGLE);
		
		GridData gd = new GridData(SWT.RIGHT,SWT.CENTER,true,false);
		gd.widthHint = 150;
				
		bubbleTextBox.setMessageAndLayout( MessageText.getString("column.setup.search"), gd );

		tvAvail.enableFilterCheck(
			bubbleTextBox,
			new TableViewFilterCheck<TableColumn>()
			{
				@Override
				public boolean
				filterCheck(
					TableColumn 	ds,
					String 			filter,
					boolean 		regex,
					boolean			confusable )
				{
					if ( confusable ){
						
						return( false );
					}
					
					TableColumnCore core = (TableColumnCore)ds;

					String raw_key 		= core.getTitleLanguageKey( false );
					String current_key 	= core.getTitleLanguageKey( true );

					String name1 = MessageText.getString(raw_key, core.getName());
					String name2 = null;

					if ( !raw_key.equals( current_key )){
						String rename = MessageText.getString(current_key, "");
						if ( rename.length() > 0 ){
							name2 = rename;
						}
					}
					String[] names = {
						name1,
						name2,
						MessageText.getString( core.getTitleLanguageKey() + ".info" )
					};

					for ( String name: names ){

						if ( name == null ){

							continue;
						}

						String s = regex ? filter : RegExUtil.splitAndQuote( filter, "\\s*[|;]\\s*" );

						boolean	match_result = true;

						if ( regex && s.startsWith( "!" )){

							s = s.substring(1);

							match_result = false;
						}

						Pattern pattern = RegExUtil.getCachedPattern( "tcs:search", s, Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE );

						if ( pattern.matcher(name).find() == match_result ){

							return( true );
						}

					}

					return( false );
				}

				@Override
				public void filterSet(String filter) {
				}
			});

		tvAvail.initialize(cTableAvail);

		TableColumnCore[] datasources = tcm.getAllTableColumnCoreAsArray(
				forDataSourceType, forTableID);

		listColumnsNoCat = new ArrayList<>(Arrays.asList(datasources));
		listCats = new ArrayList<>();
		for (int i = 0; i < datasources.length; i++) {
			TableColumnCore column = datasources[i];
			TableColumnInfo info = tcm.getColumnInfo(forDataSourceType, forTableID,
					column.getName());
			if (info != null) {
				String[] categories = info.getCategories();
				if (categories != null && categories.length > 0) {
					for (int j = 0; j < categories.length; j++) {
						String cat = categories[j];
						if (!listCats.contains(cat)) {
							listCats.add(cat);
						}
					}
					listColumnsNoCat.remove(column);
				}
			}
		}

		Listener radListener = new Listener() {
			@Override
			public void handleEvent(Event event) {
				fillAvail();
			}
		};


		Composite cProficiency = new Composite(cFilterArea, SWT.NONE);
		cProficiency.setLayout(new FormLayout());

		Label lblProficiency = new Label(cProficiency, SWT.NONE);
		Messages.setLanguageText(lblProficiency, "ColumnSetup.proficiency");

		radProficiency[0] = new Button(cProficiency, SWT.RADIO);
		Messages.setLanguageText(radProficiency[0], "ConfigView.section.mode.beginner");
		fd = new FormData();
		fd.left = new FormAttachment(lblProficiency, 5);
		radProficiency[0].setLayoutData(fd);
		radProficiency[0].addListener(SWT.Selection, radListener);

		radProficiency[1] = new Button(cProficiency, SWT.RADIO);
		Messages.setLanguageText(radProficiency[1], "ConfigView.section.mode.intermediate");
		fd = new FormData();
		fd.left = new FormAttachment(radProficiency[0], 5);
		radProficiency[1].setLayoutData(fd);
		radProficiency[1].addListener(SWT.Selection, radListener);

		radProficiency[2] = new Button(cProficiency, SWT.RADIO);
		Messages.setLanguageText(radProficiency[2], "ConfigView.section.mode.advanced");
		fd = new FormData();
		fd.left = new FormAttachment(radProficiency[1], 5);
		radProficiency[2].setLayoutData(fd);
		radProficiency[2].addListener(SWT.Selection, radListener);

		int userMode = COConfigurationManager.getIntParameter("User Mode");
		if (userMode < 0) {
			userMode = 0;
		} else if (userMode >= radProficiency.length) {
			userMode = radProficiency.length - 1;
		}
		radProficiency[userMode].setSelection(true);

		// >>>>>>>> Buttons

		Listener buttonListener = new Listener() {
			@Override
			public void handleEvent(Event event) {
				Control[] children = cCategories.getChildren();
				for (int i = 0; i < children.length; i++) {
					Control child = children[i];
					if (child != event.widget && (child instanceof Button)) {
						Button btn = (Button) child;
						btn.setSelection(false);
					}
				}

				fillAvail();
			}
		};

		Label lblCat = new Label(cFilterArea, SWT.NONE);
		Messages.setLanguageText(lblCat, "ColumnSetup.categories");

		if (CAT_BUTTONS) {
			cCategories = new Composite(cFilterArea, SWT.NONE);
			cCategories.setLayout(new RowLayout());

  		Button button = new Button(cCategories, SWT.TOGGLE);
  		Messages.setLanguageText(button, "Categories.all");
  		button.addListener(SWT.Selection, buttonListener);
  		button.setSelection(true);

  		for (String cat : listCats) {
  			button = new Button(cCategories, SWT.TOGGLE);
  			button.setData("cat", cat);
  			if (MessageText.keyExists("ColumnCategory." + cat)) {
    			button.setText(MessageText.getString("ColumnCategory." + cat));
  			} else {
    			button.setText(cat);
  			}
  			button.addListener(SWT.Selection, buttonListener);
  		}

  		if (listColumnsNoCat.size() > 0) {
  			button = new Button(cCategories, SWT.TOGGLE);
  			if (MessageText.keyExists("ColumnCategory.uncat")) {
    			button.setText(MessageText.getString("ColumnCategory.uncat"));
  			} else {
    			button.setText("?");
  			}
  			button.setText("?");
  			button.setData("cat", "uncat");
  			button.addListener(SWT.Selection, buttonListener);
  		}
		} else {
			comboFilter = new Combo(cFilterArea, SWT.DROP_DOWN | SWT.READ_ONLY);
			comboFilter.addListener(SWT.Selection, radListener);

			listCats.add(0, "all");
			for (String cat : listCats) {
				comboFilter.add(cat);
			}
			comboFilter.select(0);
		}

		final ExpandItem expandItemFilters = new ExpandItem(expandFilters, SWT.NONE);
		expandItemFilters.setText(MessageText.getString("ColumnSetup.filters"));
		expandItemFilters.setControl(cFilterArea);
		expandFilters.addListener(SWT.Resize, new Listener() {
			@Override
			public void handleEvent(Event event) {
				expandItemFilters.setHeight(cFilterArea.computeSize(
						expandFilters.getSize().x, SWT.DEFAULT).y + 3);
			}
		});

		expandFilters.addListener(SWT.Expand, new Listener() {
			@Override
			public void handleEvent(Event event) {
				Utils.execSWTThreadLater(Constants.isLinux ? 250 : 0, new AERunnable() {
					@Override
					public void runSupport() {
						shell.layout(true, true);
					}
				});
			}
		});
		expandFilters.addListener(SWT.Collapse, new Listener() {
			@Override
			public void handleEvent(Event event) {
				Utils.execSWTThreadLater(Constants.isLinux ? 250 : 0, new AERunnable() {
					@Override
					public void runSupport() {
						shell.layout(true, true);
					}
				});
			}
		});


		// <<<<<<< Buttons

		// >>>>>>> Chosen

		ImageLoader imageLoader = ImageLoader.getInstance();

		Button[] alignButtons = new Button[3];
		
		Consumer<Integer> updateAlignButtons = (align)->{
			alignButtons[0].setBackground( align==TableColumn.ALIGN_LEAD?Colors.fadedBlue:null);
			alignButtons[1].setBackground( align==TableColumn.ALIGN_CENTER?Colors.fadedBlue:null);
			alignButtons[2].setBackground( align==TableColumn.ALIGN_TRAIL?Colors.fadedBlue:null);
		};
		
		Button btnLeft = new Button(cColumnButtonArea, SWT.PUSH);
		imageLoader.setButtonImage(btnLeft, "alignleft");
		Messages.setLanguageTooltip(btnLeft, "MyTracker.column.left" );
		btnLeft.addSelectionListener(new SelectionListener() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				alignChosen( TableColumnCore.ALIGN_LEAD);
				updateAlignButtons.accept( TableColumnCore.ALIGN_LEAD );
			}

			@Override
			public void widgetDefaultSelected(SelectionEvent e) {
			}
		});

		Button btnCentre = new Button(cColumnButtonArea, SWT.PUSH);
		imageLoader.setButtonImage(btnCentre, "aligncentre");
		Messages.setLanguageTooltip(btnCentre, "label.center" );
		btnCentre.addSelectionListener(new SelectionListener() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				alignChosen( TableColumnCore.ALIGN_CENTER );
				updateAlignButtons.accept( TableColumnCore.ALIGN_CENTER );
			}

			@Override
			public void widgetDefaultSelected(SelectionEvent e) {
			}
		});

		Button btnRight = new Button(cColumnButtonArea, SWT.PUSH);
		imageLoader.setButtonImage(btnRight, "alignright");
		Messages.setLanguageTooltip(btnRight, "label.right" );
		
		btnRight.addSelectionListener(new SelectionListener() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				alignChosen( TableColumnCore.ALIGN_TRAIL );
				updateAlignButtons.accept( TableColumnCore.ALIGN_TRAIL );
			}

			@Override
			public void widgetDefaultSelected(SelectionEvent e) {
			}
		});

		alignButtons[0] = btnLeft;
		alignButtons[1] = btnCentre;
		alignButtons[2] = btnRight;
		
		ColorButton colorForeground = 
				Utils.createColorButton( 
					cColumnButtonArea, new Point( 16, 16 ), true, null, null,
					(rgb)->{
						setChosenColor( rgb, true );
					});
				
		Button btnForeground = colorForeground.getButton();
		
		ColorButton colorBackground = 
				Utils.createColorButton( 
					cColumnButtonArea, new Point( 16, 16 ), false, null, null,
					(rgb)->{
						setChosenColor( rgb, false );
					});
				
		Button btnBackground= colorBackground.getButton();

		
		Button btnUp = new Button(cColumnButtonArea, SWT.PUSH);
		imageLoader.setButtonImage(btnUp, "up");
		Messages.setLanguageTooltip(btnUp, "label.move.up" );
		btnUp.addSelectionListener(new SelectionListener() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				moveChosenUp();
			}

			@Override
			public void widgetDefaultSelected(SelectionEvent e) {
			}
		});

		Button btnDown = new Button(cColumnButtonArea, SWT.PUSH);
		imageLoader.setButtonImage(btnDown, "down");
		Messages.setLanguageTooltip(btnDown, "label.move.down" );
		btnDown.addSelectionListener(new SelectionListener() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				moveChosenDown();
			}

			@Override
			public void widgetDefaultSelected(SelectionEvent e) {
			}
		});

		Button btnDel = new Button(cColumnButtonArea, SWT.PUSH);
		imageLoader.setButtonImage(btnDel, "delete2");
		Messages.setLanguageTooltip(btnDel, "MySharesView.menu.remove" );
		btnDel.addSelectionListener(new SelectionListener() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				removeSelectedChosen();
			}

			@Override
			public void widgetDefaultSelected(SelectionEvent e) {
			}
		});

			// import + export
		
		Button btnImport = new Button(cResultButtonArea, SWT.PUSH);
		imageLoader.setButtonImage(btnImport, "import");
		Messages.setLanguageTooltip(btnImport, "label.import.config.from.clip");

		
		btnExport = new Button(cResultButtonArea, SWT.PUSH);
		imageLoader.setButtonImage(btnExport, "export");
		Messages.setLanguageTooltip(btnExport, "label.export.config.to.clip");
		btnExport.addListener(
			SWT.Selection,
			(e)->{
			
					// need to flush values held in columns into the tcm map
				
				tcm.saveTableColumns( forDataSourceType, forTableID);
				
				Map config = tcm.getTableConfigMap(forTableID);
				
				Map map = new HashMap();
				
				map.put( "table-id", Utils.getBaseViewID( forTableID ));
				map.put( "config", config );
				
				String json = BEncoder.encodeToJSON( map );
				
				ClipboardCopy.copyToClipBoard( json );
			});
		
		tvChosen = createTVChosen();

		tvChosen.addSelectionListener(
			new TableSelectionAdapter()
			{
				@Override
				public void selectionChanged(TableRowCore[] selected_rows, TableRowCore[] deselected_rows){
										
					Utils.execSWTThread(()->{
						
						List<Object> ds = tvChosen.getSelectedDataSources();
						
						boolean hasSelection = ds.size() > 0;

						btnLeft.setEnabled( hasSelection );
						btnCentre.setEnabled( hasSelection );
						btnRight.setEnabled( hasSelection );
						btnForeground.setEnabled( hasSelection );
						btnBackground.setEnabled( hasSelection );
						btnUp.setEnabled( hasSelection );
						btnDown.setEnabled( hasSelection );
						btnDel.setEnabled( hasSelection );
						
						if ( hasSelection && ds.size() == 1 ){
							
							TableColumnCore tc = (TableColumnCore)ds.get(0);
							
							colorForeground.setColor(tc.getForegroundColor());
							colorBackground.setColor(tc.getBackgroundColor());
							
							int align = tc.getAlignment();
							
							updateAlignButtons.accept( align );
						}else{
							colorForeground.setColor( null );
							colorBackground.setColor( null );
							
							updateAlignButtons.accept( -1 );
						}
					});
				}
			}, true);
		
		cTableChosen = new Composite(cResultArea, SWT.NONE);
		gridLayout = new GridLayout();
		gridLayout.marginWidth = gridLayout.marginHeight = 0;
		cTableChosen.setLayout(gridLayout);

		tvChosen.initialize(cTableChosen);

		for (int i = 0; i < columnsCurrentOrder.length; i++) {
			boolean visible = columnsCurrentOrder[i].isVisible();
			if (visible) {
				tvChosen.addDataSource(columnsCurrentOrder[i]);
			}
		}
		tvChosen.processDataSourceQueueSync();

		if ( selectedColumn != null ){
			
			TableRowCore row = tvChosen.getRow( selectedColumn );
			
			if ( row != null ){
				
				tvChosen.setSelectedRows( new TableRowCore[]{ row } );
				
				Utils.execSWTThreadLater( 100, ()->{ tvChosen.showRow( row ); });
			}
		}
		
		Button btnReset = new Button(cResultButtonArea, SWT.PUSH);
  		Messages.setLanguageText(btnReset, "Button.reset");
  		
		String[] defaultColumnNames = tcm.getDefaultColumnNames(forTableID);
		
		btnReset.setEnabled( defaultColumnNames != null );

		final Button btnCancel = new Button(shell, SWT.PUSH);
		Messages.setLanguageText(btnCancel, "Button.cancel");
		btnCancel.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				shell.dispose();
			}
		});

		btnApply = new Button(cResultButtonArea, SWT.PUSH);
		Messages.setLanguageText(btnApply, "Button.apply");
		btnApply.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				apply();
				btnCancel.setEnabled(false);
			}
		});
		
		
		btnImport.addListener(
			SWT.Selection,
			(ev)->{
			
			String json = ClipboardCopy.copyFromClipboard();
					
				try{
					Map map = BDecoder.decodeFromJSON( json );
					
					map = BDecoder.decodeStrings( map );
					
					String tableID = (String)map.get( "table-id" );
					
					Map config = (Map)map.get( "config" );
					
					if ( tableID.equals( Utils.getBaseViewID( forTableID ))){
						
						tcm.setTableConfigMap(forTableID, config );
											
						tcm.loadTableColumnSettings( forDataSourceType, forTableID );
						
						listener.tableStructureChanged(true, forDataSourceType);
						
						listener.sortOrderChanged();
						
						Arrays.sort(columnsCurrentOrder,TableColumnManager.getTableColumnOrderComparator());
	
						for (int i = 0; i < columnsCurrentOrder.length; i++) {
							boolean visible = columnsCurrentOrder[i].isVisible();
							mapNewVisibility.put(columnsCurrentOrder[i], Boolean.valueOf(visible));
						}
											
						fillChosen();
						
						fillAvail();
						
						setHasChanges( false );
						
						btnCancel.setEnabled(false);
						
					}else{
						
						MessageBoxShell mb = new MessageBoxShell(SWT.ICON_ERROR | SWT.OK,
								MessageText.getString( "ConfigView.section.security.op.error.title"),
								MessageText.getString( "table.columns.incorrect.table", new String[]{ Utils.getBaseViewID( forTableID ) + "/" + tableID } ));
						
						mb.setParent(shell);
						
						mb.open(null);		
						
						
					}
				}catch( Throwable e ){
				
					//Debug.out( e );
	
					MessageBoxShell mb = new MessageBoxShell(SWT.ICON_ERROR | SWT.OK,
							MessageText.getString( "ConfigView.section.security.op.error.title"),
							MessageText.getString( "label.invalid.configuration" ));
					
					mb.setParent(shell);
					
					mb.open(null);				
				}
			});
		
		
		if (defaultColumnNames != null) {
	  		
	  		btnReset.addSelectionListener(new SelectionAdapter() {
	  			@Override
				  public void widgetSelected(SelectionEvent e) {
	  				MessageBoxShell mb =
							new MessageBoxShell(
								MessageText.getString("table.columns.reset.dialog.title"),
								MessageText.getString("table.columns.reset.dialog.text"),
								new String[] {
									MessageText.getString("Button.yes"),
									MessageText.getString("Button.no")
								},
								1 );
	
					mb.open(new UserPrompterResultListener() {
						@Override
						public void prompterClosed(int result) {
							if  (result == 0 ){
				  				
								tcm.resetColumns( forDataSourceType, forTableID);
				  				
								Arrays.sort(columnsCurrentOrder,TableColumnManager.getTableColumnOrderComparator());

								for (int i = 0; i < columnsCurrentOrder.length; i++) {
									boolean visible = columnsCurrentOrder[i].isVisible();
									mapNewVisibility.put(columnsCurrentOrder[i], Boolean.valueOf(visible));
								}
								
		  						fillChosen();
		  						
		  						fillAvail();
		  										  						
		  						setHasChanges( false );
		  						
		  						btnCancel.setEnabled(false);
		  						
		  						updateAlignButtons.accept( -1 );
		  						colorForeground.setColor( null );
								colorBackground.setColor( null );
							}
						}
					});
	  			}
	  		});
		}
		
		
		fd = new FormData();
		fd.left = new FormAttachment(0, 5);
		fd.right = new FormAttachment(100, -5);
		//fd.bottom = new FormAttachment(100, -5);
		//Utils.setLayoutData(lblChosenHeader, fd);

		fd = new FormData();
		fd.top = new FormAttachment(topInfo, 5);
		fd.right = new FormAttachment(100, -3);
		fd.bottom = new FormAttachment(btnOk, -5);
		fd.width = Constants.isWindows?200:230;
		cResultArea.setLayoutData(fd);

		fd = new FormData();
		fd.top = new FormAttachment(0, 3);
		fd.left = new FormAttachment(0, 3);
		fd.right = new FormAttachment(100, -3);
		fd.bottom = new FormAttachment(cColumnButtonArea, -3);
		cTableChosen.setLayoutData(fd);

		fd = new FormData();
		fd.bottom = new FormAttachment(cResultButtonArea, -3);
		fd.left = new FormAttachment(cTableChosen, 0, SWT.CENTER );
		fd.right = new FormAttachment(100, 0);
		cColumnButtonArea.setLayoutData(fd);
		
		fd = new FormData();
		fd.bottom = new FormAttachment(100, 0);
		fd.left = new FormAttachment(0, 0);
		fd.right = new FormAttachment(100, 0);
		cResultButtonArea.setLayoutData(fd);
		
			// align

		fd = new FormData();
		fd.top = new FormAttachment(cColumnButtonArea, 3);
		fd.left = new FormAttachment(0, 3);
		btnLeft.setLayoutData(fd);

		fd = new FormData();
		fd.left = new FormAttachment(btnLeft, 3);
		fd.top = new FormAttachment(btnLeft, 0, SWT.TOP);
		fd.bottom = new FormAttachment(btnLeft, 0, SWT.BOTTOM);
		btnCentre.setLayoutData(fd);

		fd = new FormData();
		fd.left = new FormAttachment(btnCentre, 3);
		fd.top = new FormAttachment(btnLeft, 0, SWT.TOP);
		fd.bottom = new FormAttachment(btnLeft, 0, SWT.BOTTOM);
		btnRight.setLayoutData(fd);

			// colours
		
		fd = new FormData();
		fd.left = new FormAttachment(btnRight, 3);
		fd.top = new FormAttachment(btnLeft, 0, SWT.TOP);
		fd.bottom = new FormAttachment(btnLeft, 0, SWT.BOTTOM);
		btnForeground.setLayoutData(fd);

		fd = new FormData();
		fd.left = new FormAttachment(btnForeground, 3);
		fd.top = new FormAttachment(btnLeft, 0, SWT.TOP);
		fd.bottom = new FormAttachment(btnLeft, 0, SWT.BOTTOM);
		btnBackground.setLayoutData(fd);


			// move

		fd = new FormData();
		fd.left = new FormAttachment(btnCentre, 0, SWT.LEFT );
		fd.top = new FormAttachment(btnLeft, 2);
		btnUp.setLayoutData(fd);

		fd = new FormData();
		fd.left = new FormAttachment(btnUp, 3);
		fd.top = new FormAttachment(btnUp, 0, SWT.TOP);
		btnDown.setLayoutData(fd);

		fd = new FormData();
		fd.left = new FormAttachment(btnDown, 3);
		fd.top = new FormAttachment(btnUp, 0, SWT.TOP);
		btnDel.setLayoutData(fd);

			// import, export, reset, apply
		
 		fd = new FormData();
 		fd.left =  new FormAttachment(0, 5);
  		fd.bottom = new FormAttachment(btnExport, 0, SWT.BOTTOM);
		btnImport.setLayoutData(fd);

		fd = new FormData();
  		fd.left = new FormAttachment(btnImport, 3);
  		fd.bottom = new FormAttachment(btnReset, 0, SWT.BOTTOM);
  		btnExport.setLayoutData(fd);
	
		
  		fd = new FormData();
  		fd.right = new FormAttachment(btnApply, -3);
  		fd.bottom = new FormAttachment(btnApply, 0, SWT.BOTTOM);
		btnReset.setLayoutData(fd);

		fd = new FormData();
		fd.right = new FormAttachment(100, -5);
		fd.top = new FormAttachment(btnUp, 3, SWT.BOTTOM);
		//fd.width = 64;
		btnApply.setLayoutData(fd);

		fd = new FormData();
		fd.right = new FormAttachment(100, -8);
		fd.bottom = new FormAttachment(100, -3);
		//fd.width = 65;
		btnCancel.setLayoutData(fd);

		fd = new FormData();
		fd.right = new FormAttachment(btnCancel, -3);
		fd.bottom = new FormAttachment(btnCancel, 0, SWT.BOTTOM);
		//fd.width = 64;
		btnOk.setLayoutData(fd);

		// <<<<<<<<< Chosen

		fd = new FormData();
		fd.top = new FormAttachment(topInfo, 5);
		fd.left = new FormAttachment(0, 3);
		fd.right = new FormAttachment(cResultArea, -3);
		fd.bottom = new FormAttachment(100, -3);
		cPickArea.setLayoutData(fd);

		fd = new FormData();
		fd.bottom = new FormAttachment(100, 0);
		fd.left = new FormAttachment(0, 0);
		fd.right = new FormAttachment(100, 0);
		expandFilters.setLayoutData(fd);


		if (CAT_BUTTONS) {
			fd = new FormData();
			fd.bottom = new FormAttachment(cCategories, 0, SWT.CENTER);
			fd.left = new FormAttachment(0, 5);
			lblCat.setLayoutData(fd);

			fd = new FormData();
			//fd.top = new FormAttachment(0, 0);
			fd.bottom = new FormAttachment(radProficiency[0], 0, SWT.CENTER);
			fd.left = new FormAttachment(0, 0);
			lblProficiency.setLayoutData(fd);

  		fd = new FormData();
  		fd.top = new FormAttachment(cProficiency, 5);
  		fd.left = new FormAttachment(lblCat, 5);
  		fd.right = new FormAttachment(100, 0);
			cCategories.setLayoutData(fd);
		} else {
			fd = new FormData();
			fd.top = new FormAttachment(comboFilter, -5);
			fd.right = new FormAttachment(comboFilter, 0, SWT.CENTER);
			lblCat.setLayoutData(fd);

  		fd = new FormData();
  		fd.top = new FormAttachment(cProficiency, 0, SWT.CENTER);
  		fd.right = new FormAttachment(100, 0);
			comboFilter.setLayoutData(fd);
		}

		fd = new FormData();
		fd.top = new FormAttachment(0, 5);
		fd.left = new FormAttachment(0, 5);
		cProficiency.setLayoutData(fd);

		fd = new FormData();
		fd.top = new FormAttachment(0, 3);
		fd.left = new FormAttachment(0, 3);
		fd.right = new FormAttachment(100, -3);
		fd.bottom = new FormAttachment(expandFilters, -3);
		cTableAvail.setLayoutData(fd);


		setHasChanges( false );
		
		//cTableAvail.setFocus();
		//tvAvail.getTableComposite().setFocus();

		shell.setTabList(new Control[] {
			cPickArea,
			cResultArea,
			btnOk,
			btnCancel,
		});

		cPickArea.setTabList(new Control[] {
			cTableAvail
		});

		fillAvail();

		UIUpdaterSWT.getInstance().addUpdater(this);
	}

	private void
	setHasChanges(
		boolean		hasChanges )
	{
		btnApply.setEnabled( hasChanges );
		btnExport.setEnabled( !hasChanges );
	}
	
	private void
	fillChosen()
	{
		tvChosen.removeAllTableRows();
		for (int i = 0; i < columnsCurrentOrder.length; i++) {
			boolean visible = mapNewVisibility.get( columnsCurrentOrder[i]);
			if (visible) {
				tvChosen.addDataSource(columnsCurrentOrder[i]);
			}
		}
		tvChosen.processDataSourceQueue();
	}
	
	protected void fillAvail() {
		String selectedCat = null;
		if (CAT_BUTTONS) {
  		Control[] children = cCategories.getChildren();
  		for (int i = 0; i < children.length; i++) {
  			Control child = children[i];
  			if (child instanceof Button) {
  				Button btn = (Button) child;
  				if (btn.getSelection()) {
  					selectedCat = (String) btn.getData("cat");
  					break;
  				}
  			}
  		}
		} else {
			selectedCat = comboFilter.getItem(comboFilter.getSelectionIndex());
		}

		if (selectedCat != null && selectedCat.equals("all")) {
			selectedCat = null;
		}


		byte selectedProf = 0;
		for (byte i = 0; i < radProficiency.length; i++) {
			Button btn = radProficiency[i];
			if (btn.getSelection()) {
				selectedProf = i;
				break;
			}
		}

		String s;
		//= "Available " + radProficiency[selectedProf].getText() + " Columns";
		if (selectedCat != null) {
			s = MessageText.getString("ColumnSetup.availcolumns.filteredby", new String[] {
				radProficiency[selectedProf].getText(),
				selectedCat
			});
		} else {
			s = MessageText.getString("ColumnSetup.availcolumns", new String[] {
				radProficiency[selectedProf].getText(),
			});
		}
		cPickArea.setText(s);

		tvAvail.removeAllTableRows();

		final TableColumnManager tcm = TableColumnManager.getInstance();
		TableColumnCore[] datasources = tcm.getAllTableColumnCoreAsArray(
				forDataSourceType, forTableID);

		if (selectedCat == "uncat") {
			datasources = listColumnsNoCat.toArray( new TableColumnCore[listColumnsNoCat.size()]);
		}
		for (int i = 0; i < datasources.length; i++) {
			TableColumnCore column = datasources[i];
			TableColumnInfo info = tcm.getColumnInfo(forDataSourceType,
					forTableID, column.getName());
			String[] cats = info == null ? null : info.getCategories();
			if (cats == null) {
				if (selectedCat == null || selectedCat.equals("uncat")) {
					tvAvail.addDataSource(column);
				}
			} else {
  			for (int j = 0; j < cats.length; j++) {
  				String cat = cats[j];
  				if ((selectedCat == null || selectedCat.equalsIgnoreCase(cat))
  						&& info.getProficiency() <= selectedProf) {
  					tvAvail.addDataSource(column);
  					break;
  				}
  			}
			}
		}
		tvAvail.processDataSourceQueue();
	}

	/**
	 *
	 *
	 * @since 4.0.0.5
	 */
	protected void removeSelectedChosen() {
		Object[] datasources = tvChosen.getSelectedDataSources().toArray();
		TableColumnCore[] cols = new TableColumnCore[datasources.length];
		for (int i = 0; i < datasources.length; i++) {
			TableColumnCore column = (TableColumnCore) datasources[i];
			cols[i] = column;
			mapNewVisibility.put(column, Boolean.FALSE);
		}
		tvChosen.removeDataSources(cols);
		tvChosen.processDataSourceQueue();
		for (int i = 0; i < datasources.length; i++) {
			TableRowSWT row = (TableRowSWT) tvAvail.getRow((TableColumn)datasources[i]);
			if (row != null) {
				row.redraw();
			}
		}
		
		setHasChanges( true );
	}

	/**
	 *
	 *
	 * @since 4.0.0.5
	 */
	protected void moveChosenDown() {
		TableRowCore[] selectedRows = tvChosen.getSelectedRows();
		TableRowCore[] rows = tvChosen.getRows();
		for (int i = selectedRows.length - 1; i >= 0; i--) {
			TableRowCore row = selectedRows[i];
			TableColumnCore column = (TableColumnCore) row.getDataSource();
			if (column != null) {
				int oldColumnPos = column.getPosition();
				int oldRowPos = row.getIndex();
				if (oldRowPos < rows.length - 1) {
					TableRowCore displacedRow = rows[oldRowPos + 1];
					((TableColumnCore) displacedRow.getDataSource()).setPositionNoShift(oldColumnPos);
					rows[oldRowPos + 1] = rows[oldRowPos];
					rows[oldRowPos] = displacedRow;
					column.setPositionNoShift(oldColumnPos + 1);
				}
			}
		}
		tvChosen.tableInvalidate();
		tvChosen.refreshTable(true);
		
		setHasChanges( true );
	}

	/**
	 *
	 *
	 * @since 4.0.0.5
	 */
	protected void moveChosenUp() {
		TableRowCore[] selectedRows = tvChosen.getSelectedRows();
		TableRowCore[] rows = tvChosen.getRows();
		for (int i = 0; i < selectedRows.length; i++) {
			TableRowCore row = selectedRows[i];
			TableColumnCore column = (TableColumnCore) row.getDataSource();
			if (column != null) {
				int oldColumnPos = column.getPosition();
				int oldRowPos = row.getIndex();
				if (oldRowPos > 0) {
					TableRowCore displacedRow = rows[oldRowPos - 1];
					((TableColumnCore) displacedRow.getDataSource()).setPositionNoShift(oldColumnPos);
					rows[oldRowPos - 1] = rows[oldRowPos];
					rows[oldRowPos] = displacedRow;
					column.setPositionNoShift(oldColumnPos - 1);

					column.setAlignment( TableColumnCore.ALIGN_CENTER );
				}
			}
		}
		tvChosen.tableInvalidate();
		tvChosen.refreshTable(true);
		
		setHasChanges( true );
	}

	protected void alignChosen( int align ) {
		TableRowCore[] selectedRows = tvChosen.getSelectedRows();
		for (int i = 0; i < selectedRows.length; i++) {
			TableRowCore row = selectedRows[i];
			TableColumnCore column = (TableColumnCore) row.getDataSource();
			if (column != null) {
				column.setAlignment( align );
			}
		}
		tvChosen.tableInvalidate();
		tvChosen.refreshTable(true);
		
		setHasChanges( true );
	}

	protected void setChosenColor( int[] rgb, boolean fg ) {
		TableRowCore[] selectedRows = tvChosen.getSelectedRows();
		for (int i = 0; i < selectedRows.length; i++) {
			TableRowCore row = selectedRows[i];
			TableColumnCore column = (TableColumnCore) row.getDataSource();
			if (column != null) {
				if ( fg ){
					column.setForegroundColor(rgb);
				}else{
					column.setBackgroundColor(rgb);
				}
			}
		}
		tvChosen.tableInvalidate();
		tvChosen.refreshTable(true);
		
		setHasChanges( true );
	}

	/**
	 *
	 *
	 * @since 4.0.0.5
	 */
	protected void apply() {
		TableColumnManager tcm = TableColumnManager.getInstance();

		for (TableColumnCore tc : mapNewVisibility.keySet()) {
			boolean visible = mapNewVisibility.get(tc).booleanValue();
			tc.setVisible(visible);
		}
		
		tcm.saveTableColumns(forDataSourceType, forTableID);
		listener.tableStructureChanged(true, forDataSourceType);
		
		setHasChanges( false );
	}

	/**
	 * @return
	 *
	 * @since 4.0.0.5
	 */
	private TableViewSWT<TableColumnCore> createTVChosen() {
		final TableColumnManager tcm = TableColumnManager.getInstance();
		TableColumnCore[] columnTVChosen = tcm.getAllTableColumnCoreAsArray(
				TableColumn.class, TABLEID_CHOSEN);
		for (int i = 0; i < columnTVChosen.length; i++) {
			TableColumnCore column = columnTVChosen[i];
			if (column.getName().equals(ColumnTC_ChosenColumn.COLUMN_ID)) {
				column.setVisible(true);
				column.setWidth(175);
				column.setSortAscending(true);
			} else {
				column.setVisible(false);
			}
		}

		final TableViewSWT<TableColumnCore> tvChosen = TableViewFactory.createTableViewSWT(
				TableColumn.class, TABLEID_CHOSEN, TABLEID_CHOSEN, columnTVChosen,
				ColumnTC_ChosenColumn.COLUMN_ID, SWT.FULL_SELECTION | SWT.VIRTUAL
						| SWT.MULTI);
		tvAvail.setParentDataSource(this);
		tvChosen.setMenuEnabled(false);
		tvChosen.setHeaderVisible(false);
		//tvChosen.setRowDefaultHeight(16);

		tvChosen.addLifeCycleListener(new TableLifeCycleListener() {
			@Override
			public void tableLifeCycleEventOccurred(TableView tv, int eventType, Map<String, Object> data) {
				if (eventType == EVENT_TABLELIFECYCLE_INITIALIZED) {
					tableViewInitialized();
				}
			}

			private void tableViewInitialized() {
				DragSource dragSource = tvChosen.createDragSource(DND.DROP_MOVE | DND.DROP_COPY
					| DND.DROP_LINK);
				dragSource.setTransfer(TextTransfer.getInstance());
				dragSource.setData("tv", tvChosen);
				dragSource.addDragListener(dragSourceListener);

				DropTarget dropTarget = tvChosen.createDropTarget(DND.DROP_DEFAULT
					| DND.DROP_MOVE | DND.DROP_COPY | DND.DROP_LINK
					| DND.DROP_TARGET_MOVE);
				dropTarget.setTransfer(TextTransfer.getInstance());
				dropTarget.addDropListener(new DropTargetAdapter() {

					@Override
					public void drop(DropTargetEvent event) {
						String id = (String) event.data;
						TableRowCore destRow = tvChosen.getRow(event);

						TableView<?> tv = id.equals("c") ? tvChosen : tvAvail;

						Object[] dataSources = tv.getSelectedDataSources().toArray();
						for (Object dataSource : dataSources) {
							if (!(dataSource instanceof TableColumnCore)) {
								continue;
							}
							TableColumnCore column = (TableColumnCore) dataSource;
							chooseColumn(column, destRow, true);
							TableRowCore row = tvAvail.getRow(column);
							if (row != null) {
								row.redraw();
							}
						}
					}
				});
			}
		});

		tvChosen.addKeyListener(new KeyListener() {
			@Override
			public void keyReleased(KeyEvent e) {
			}

			@Override
			public void keyPressed(KeyEvent e) {
				if (e.stateMask == 0
						&& (e.keyCode == SWT.ARROW_LEFT || e.keyCode == SWT.DEL)) {
					removeSelectedChosen();
					e.doit = false;
				}

				if (e.stateMask == SWT.CONTROL) {
					if (e.keyCode == SWT.ARROW_UP) {
						moveChosenUp();
						e.doit = false;
					} else if (e.keyCode == SWT.ARROW_DOWN) {
						moveChosenDown();
						e.doit = false;
					}
				}
			}
		});
		return tvChosen;
	}

	/**
	 * @return
	 *
	 * @since 4.0.0.5
	 */
	private TableViewSWT<TableColumn> createTVAvail() {
		final TableColumnManager tcm = TableColumnManager.getInstance();
		Map<String, TableColumnCore> mapColumns = tcm.getTableColumnsAsMap(
				TableColumn.class, TABLEID_AVAIL);
		TableColumnCore[] columns;
		int[] widths = { 405, 105 };
		if (sampleRow == null) {
			columns = new TableColumnCore[] {
				mapColumns.get(ColumnTC_NameInfo.COLUMN_ID),
			};
			widths = new int[] { 510 };
		} else {
			columns = new TableColumnCore[] {
				mapColumns.get(ColumnTC_NameInfo.COLUMN_ID),
				mapColumns.get(ColumnTC_Sample.COLUMN_ID),
			};
		}
		for (int i = 0; i < columns.length; i++) {
			TableColumnCore column = columns[i];
			if (column != null) {
				column.setVisible(true);
				column.setPositionNoShift(i);
				column.setWidth(widths[i]);
			}
		}

		final TableViewSWT<TableColumn> tvAvail = TableViewFactory.createTableViewSWT(
				TableColumn.class, TABLEID_AVAIL, TABLEID_AVAIL, columns,
				ColumnTC_NameInfo.COLUMN_ID, SWT.FULL_SELECTION | SWT.VIRTUAL
						| SWT.SINGLE);
		tvAvail.setParentDataSource(this);
		tvAvail.setMenuEnabled(false);

		tvAvail.setRowDefaultHeightEM(5);

		tvAvail.addLifeCycleListener(new TableLifeCycleListener() {
			@Override
			public void tableLifeCycleEventOccurred(TableView tv, int eventType, Map<String, Object> data) {
				if (eventType == EVENT_TABLELIFECYCLE_INITIALIZED) {
					tableViewInitialized();
				}
			}

			private void tableViewInitialized() {
				DragSource dragSource = tvAvail.createDragSource(
						DND.DROP_MOVE | DND.DROP_COPY | DND.DROP_LINK);
				dragSource.setTransfer(TextTransfer.getInstance());
				dragSource.setData("tv", tvAvail);
				dragSource.addDragListener(dragSourceListener);


				DropTarget dropTarget = tvAvail.createDropTarget(
						DND.DROP_DEFAULT | DND.DROP_MOVE | DND.DROP_COPY | DND.DROP_LINK
								| DND.DROP_TARGET_MOVE);
				dropTarget.setTransfer(TextTransfer.getInstance());
				dropTarget.addDropListener(new DropTargetAdapter() {
					@Override
					public void drop(DropTargetEvent event) {
						String id = (String) event.data;

						if (!id.equals("c")) {
							return;
						}

						removeSelectedChosen();
					}
				});

			}

		});

		tvAvail.addSelectionListener(new TableSelectionAdapter() {
			@Override
			public void defaultSelected(TableRowCore[] rows, int stateMask) {
				for (int i = 0; i < rows.length; i++) {
					TableRowCore row = rows[i];
					TableColumnCore column = (TableColumnCore) row.getDataSource();
					chooseColumn(column, null, false);
				}
			}
		}, false);

		tvAvail.addKeyListener(new KeyListener() {
			@Override
			public void keyReleased(KeyEvent e) {
			}

			@Override
			public void keyPressed(KeyEvent e) {
				if (e.stateMask == 0) {
					if (e.keyCode == SWT.ARROW_RIGHT) {
						TableRowCore[] selectedRows = tvAvail.getSelectedRows();
						for (int i = 0; i < selectedRows.length; i++) {
							TableRowCore row = selectedRows[i];
							TableColumnCore column = (TableColumnCore) row.getDataSource();
							chooseColumn(column, null, false);
							tvChosen.processDataSourceQueue();
							row.redraw();
						}
						e.doit = false;
					} else if (e.keyCode == SWT.ARROW_LEFT) {
						TableRowCore[] selectedRows = tvAvail.getSelectedRows();
						for (int i = 0; i < selectedRows.length; i++) {
							TableRowCore row = selectedRows[i];
							TableColumnCore column = (TableColumnCore) row.getDataSource();
							mapNewVisibility.put(column, Boolean.FALSE);
							tvChosen.removeDataSource(column);
							tvChosen.processDataSourceQueue();
							row.redraw();
						}
						e.doit = false;
					}
				}
			}
		});

		return tvAvail;
	}

	public void open() {
		shell.open();
	}

	// @see UIUpdatable#getUpdateUIName()
	@Override
	public String getUpdateUIName() {
		// TODO Auto-generated method stub
		return null;
	}

	// @see UIUpdatable#updateUI()
	@Override
	public void updateUI() {
		if (shell.isDisposed()) {
			UIUpdaterSWT.getInstance().removeUpdater(this);
			return;
		}
		if (tvAvail != null && !tvAvail.isDisposed()) {
			tvAvail.refreshTable(false);
		}
		if (tvChosen != null && !tvChosen.isDisposed()) {
			tvChosen.refreshTable(false);
		}
	}

	public TableRow getSampleRow() {
		return sampleRow;
	}

	public void chooseColumn(TableColumnCore column) {
		chooseColumn(column, null, false);
		TableRowCore row = tvAvail.getRow(column);
		if (row != null) {
			row.redraw();
		}
	}

	public boolean isColumnAdded(TableColumnCore column) {
		if (tvChosen == null) {
			return false;
		}
		TableRowCore row = tvChosen.getRow(column);
		return row != null;
	}

	/**
	 * @param column
	 *
	 * @since 4.0.0.5
	 */
	public void chooseColumn(final TableColumnCore column,
			TableRowCore placeAboveRow, boolean ignoreExisting) {
		TableRowCore row = tvChosen.getRow(column);

		if (row == null || ignoreExisting) {
			int newPosition = 0;

			row = placeAboveRow == null && !ignoreExisting ? tvChosen.getFocusedRow():placeAboveRow;
			
			if ( row == null || row.getDataSource() == null){
				
				if (columnsCurrentOrder.length > 0) {
					
					newPosition = columnsCurrentOrder.length;
				}
			}else{
				
				newPosition = ((TableColumn) row.getDataSource()).getPosition();
			}

			column.setPositionNoShift( newPosition );
			
			for ( TableColumnCore col: columnsCurrentOrder ){
				
				if ( col != column ){
				
					int pos = col.getPosition();
					
					if ( pos >= newPosition ){
						
						col.setPositionNoShift( pos+1 );
					}
				}
			}

			Arrays.sort(columnsCurrentOrder,	TableColumnManager.getTableColumnOrderComparator());

			for ( int i=0;i<columnsCurrentOrder.length;i++){
				columnsCurrentOrder[i].setPositionNoShift(i);
			}
			
			mapNewVisibility.put(column, Boolean.TRUE);
			

			TableRowCore existingRow = tvChosen.getRow(column);
			if (existingRow == null) {
				tvChosen.addDataSource(column);
				tvChosen.processDataSourceQueueSync();
				
				TableRowCore thisRow = tvChosen.getRow(column);
				
				if ( thisRow != null ){
					Utils.execSWTThreadLater(
						1,
						()->{
							tvChosen.setSelectedRows(new TableRowCore[] { thisRow });
							tvChosen.showRow(thisRow);
						});
						
				}
			}

			tvChosen.tableInvalidate();
			tvChosen.refreshTable(true);

			setHasChanges( true );
			
		} else {
			row.setSelected(true);
		}
	}

	private void close() {
		if (apply) {
			apply();
		} else {
			for (int i = 0; i < columnsOriginalOrder.length; i++) {
				TableColumnCore column = columnsOriginalOrder[i];
				if (column != null) {
					column.setPositionNoShift(i);
				}
			}
		}
		
		if ( tvAvail != null ){
			
			tvAvail.delete();
		}
		
		if ( tvChosen != null ){
			
			tvChosen.delete();
		}
	}
}
