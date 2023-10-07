/*
 * Created on Feb 24, 2009
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

package com.biglybt.ui.swt.devices;

import java.io.File;
import java.net.URL;
import java.util.List;
import java.util.*;

import org.eclipse.swt.SWT;
import org.eclipse.swt.dnd.*;
import org.eclipse.swt.events.*;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.*;

import com.biglybt.core.Core;
import com.biglybt.core.CoreFactory;
import com.biglybt.core.CoreRunningListener;
import com.biglybt.core.category.Category;
import com.biglybt.core.category.CategoryManager;
import com.biglybt.core.config.COConfigurationManager;
import com.biglybt.core.devices.*;
import com.biglybt.core.disk.DiskManagerFileInfo;
import com.biglybt.core.download.DownloadManager;
import com.biglybt.core.internat.MessageText;
import com.biglybt.core.tag.*;
import com.biglybt.core.util.Debug;
import com.biglybt.pifimpl.local.PluginInitializer;
import com.biglybt.ui.UIFunctions;
import com.biglybt.ui.UIFunctions.TagReturner;
import com.biglybt.ui.UIFunctionsManager;
import com.biglybt.ui.UserPrompterResultListener;
import com.biglybt.ui.common.ToolBarItem;
import com.biglybt.ui.common.table.*;
import com.biglybt.ui.common.table.impl.TableColumnManager;
import com.biglybt.ui.common.updater.UIUpdatable;
import com.biglybt.ui.selectedcontent.SelectedContent;
import com.biglybt.ui.selectedcontent.SelectedContentManager;
import com.biglybt.ui.swt.*;
import com.biglybt.ui.swt.columns.torrent.ColumnThumbnail;
import com.biglybt.ui.swt.devices.columns.*;
import com.biglybt.ui.swt.mainwindow.ClipboardCopy;
import com.biglybt.ui.swt.mdi.MdiEntrySWT;
import com.biglybt.ui.swt.mdi.MultipleDocumentInterfaceSWT;
import com.biglybt.ui.swt.shells.MessageBoxShell;
import com.biglybt.ui.swt.skin.SWTSkinButtonUtility;
import com.biglybt.ui.swt.skin.SWTSkinButtonUtility.ButtonListenerAdapter;
import com.biglybt.ui.swt.skin.SWTSkinObject;
import com.biglybt.ui.swt.skin.SWTSkinObjectText;
import com.biglybt.ui.swt.views.skin.InfoBarUtil;
import com.biglybt.ui.swt.views.skin.SkinView;
import com.biglybt.ui.swt.views.skin.TorrentListViewsUtils;
import com.biglybt.ui.swt.views.table.TableViewSWT;
import com.biglybt.ui.swt.views.table.TableViewSWTMenuFillListener;
import com.biglybt.ui.swt.views.table.impl.TableViewFactory;
import com.biglybt.ui.swt.views.utils.CategoryUIUtils;
import com.biglybt.ui.swt.views.utils.ManagerUtils;
import com.biglybt.ui.swt.views.utils.TagUIUtils;
import com.biglybt.ui.swt.views.utils.TagUIUtils.TagMenuOptions;
import com.biglybt.util.PlayUtils;

import com.biglybt.pif.ui.UIManager;
import com.biglybt.pif.ui.UIPluginViewToolBarListener;
import com.biglybt.pif.ui.tables.TableColumn;
import com.biglybt.pif.ui.tables.TableColumnCreationListener;
import com.biglybt.pif.ui.tables.TableManager;
import com.biglybt.pif.ui.toolbar.UIToolBarItem;

/**
 * @author TuxPaper
 * @created Feb 24, 2009
 *
 */
public class SBC_DevicesView
	extends SkinView
	implements TranscodeQueueListener, UIUpdatable,
	TranscodeTargetListener, DeviceListener, UIPluginViewToolBarListener
{
	public static final String TABLE_DEVICES = "Devices";

	public static final String TABLE_TRANSCODE_QUEUE = "TranscodeQueue";

	public static final String TABLE_DEVICE_LIBRARY = "DeviceLibrary";

	private static boolean columnsAdded = false;

	private DeviceManager device_manager;

	private TranscodeManager transcode_manager;

	private TranscodeQueue transcode_queue;

	private TableViewSWT<?> 	tvDevices;
	private int					drag_drop_line_start = -1;
	private TableRowCore[]		drag_drop_rows;


	private TableViewSWT<TranscodeFile> tvFiles;

	private MdiEntrySWT mdiEntry;

	private Composite tableJobsParent;

	private Device 	device;
	private String	device_name;

	private TranscodeTarget transTarget;

	// @see SkinView#skinObjectInitialShow(SWTSkinObject, java.lang.Object)
	@Override
	public Object skinObjectInitialShow(SWTSkinObject skinObject, Object params) {
		CoreFactory.addCoreRunningListener(new CoreRunningListener() {
			@Override
			public void coreRunning(Core core) {
				initColumns(core);
			}
		});

		device_manager = DeviceManagerFactory.getSingleton();

		transcode_manager = device_manager.getTranscodeManager();

		transcode_queue = transcode_manager.getQueue();

		MultipleDocumentInterfaceSWT mdi = UIFunctionsManagerSWT.getUIFunctionsSWT().getMDISWT();
		if (mdi != null){
			mdiEntry = mdi.getCurrentEntry();
			Object ds = mdiEntry.getDataSource();
			if ( !( ds instanceof Device )){
				return( null );
			}
			device = (Device)ds;
		}

		if (device instanceof TranscodeTarget) {
			transTarget = (TranscodeTarget) device;
		}

		if (device == null) {
			new InfoBarUtil(skinObject, "devicesview.infobar", false,
					"DeviceView.infobar", "v3.deviceview.infobar") {
				@Override
				public boolean allowShow() {
					return true;
				}
			};
		} else if (device instanceof DeviceMediaRenderer) {
			DeviceMediaRenderer renderer = (DeviceMediaRenderer) device;
			int species = renderer.getRendererSpecies();
			String speciesID = null;
			switch (species) {
				case DeviceMediaRenderer.RS_ITUNES:
					speciesID = "itunes";
					break;
				case DeviceMediaRenderer.RS_PS3:
					speciesID = "ps3";
					break;
				case DeviceMediaRenderer.RS_XBOX:
					speciesID = "xbox";
					break;
				case DeviceMediaRenderer.RS_OTHER:{
					String classification = renderer.getClassification();

					if ( classification.equals( "sony.PSP")){
						speciesID = "psp";
					}else if ( classification.startsWith( "tivo.")){
						speciesID = "tivo";
					}else if ( classification.toLowerCase().contains( "android")){
						speciesID = "android";
					}
				}
				default:
					break;
			}

			if (speciesID != null) {
				final String fSpeciesID = speciesID;
				new InfoBarUtil(skinObject, "devicesview.infobar", false,
						"DeviceView.infobar." + speciesID, "v3.deviceview.infobar") {
					@Override
					public boolean allowShow() {
						return true;
					}

					// @see InfoBarUtil#created(SWTSkinObject)
					@Override
					protected void created(SWTSkinObject parent) {
						SWTSkinObjectText soLine1 = (SWTSkinObjectText) skin.getSkinObject(
								"line1", parent);
						soLine1.setTextID("v3.deviceview.infobar.line1.generic",
								new String[] {
									device.getName()
								});
						SWTSkinObjectText soLine2 = (SWTSkinObjectText) skin.getSkinObject(
								"line2", parent);
						soLine2.setTextID("v3.deviceview.infobar.line2." + fSpeciesID);
					}
				};
			}
		}

		SWTSkinObject soAdvInfo = getSkinObject("advinfo");
		if (soAdvInfo != null) {
			initAdvInfo(soAdvInfo);
		}

		if (device != null) {
			device_name = device.getName();

			SWTSkinObject soTitle = getSkinObject("title");
			if (soTitle instanceof SWTSkinObjectText) {
				((SWTSkinObjectText) soTitle).setTextID("device.view.heading",
						new String[] {
							device_name
						});
			}
		}

		return null;
	}

	/**
	 *
	 *
	 * @since 4.1.0.5
	 */
	private void initColumns(Core core) {
		if (columnsAdded) {
			return;
		}
		columnsAdded = true;
		UIManager uiManager = PluginInitializer.getDefaultInterface().getUIManager();
		TableManager tableManager = uiManager.getTableManager();
		tableManager.registerColumn(TranscodeFile.class, ColumnTJ_Rank.COLUMN_ID,
				new TableColumnCreationListener() {
					@Override
					public void tableColumnCreated(TableColumn column) {
						new ColumnTJ_Rank(column);
						if (!column.getTableID().equals(TABLE_TRANSCODE_QUEUE)) {
							column.setVisible(false);
						}
					}
				});
		tableManager.registerColumn(TranscodeFile.class, ColumnThumbnail.COLUMN_ID,
				new TableColumnCreationListener() {
					@Override
					public void tableColumnCreated(TableColumn column) {
						new ColumnThumbnail(column);
						column.setWidth(70);
						column.setVisible(false);
					}
				});
		tableManager.registerColumn(TranscodeFile.class, ColumnTJ_Name.COLUMN_ID,
				new TableColumnCreationListener() {
					@Override
					public void tableColumnCreated(TableColumn column) {
						new ColumnTJ_Name(column);
						if (column.getTableID().equals(TABLE_TRANSCODE_QUEUE)) {
							column.setWidth(200);
						} else if (!column.getTableID().endsWith(":type=1")) {
							column.setWidth(140);
						}
					}
				});
		tableManager.registerColumn(TranscodeFile.class,
				ColumnTJ_Duration.COLUMN_ID, new TableColumnCreationListener() {
					@Override
					public void tableColumnCreated(TableColumn column) {
						new ColumnTJ_Duration(column);
					}
				});
		tableManager.registerColumn(TranscodeFile.class, ColumnTJ_Device.COLUMN_ID,
				new TableColumnCreationListener() {
					@Override
					public void tableColumnCreated(TableColumn column) {
						new ColumnTJ_Device(column);
						column.setVisible(false);
					}
				});
		tableManager.registerColumn(TranscodeFile.class,
				ColumnTJ_Profile.COLUMN_ID, new TableColumnCreationListener() {
					@Override
					public void tableColumnCreated(TableColumn column) {
						new ColumnTJ_Profile(column);
						if (column.getTableID().equals(TABLE_TRANSCODE_QUEUE)) {
							column.setWidth(70);
						}
					}
				});

		tableManager.registerColumn(TranscodeFile.class,
				ColumnTJ_Resolution.COLUMN_ID, new TableColumnCreationListener() {
					@Override
					public void tableColumnCreated(TableColumn column) {
						new ColumnTJ_Resolution(column);
						column.setVisible(false);
						if (column.getTableID().equals(TABLE_TRANSCODE_QUEUE)) {
							column.setWidth(95);
						}
					}
				});

		tableManager.registerColumn(TranscodeFile.class, ColumnTJ_Status.COLUMN_ID,
				new TableColumnCreationListener() {
					@Override
					public void tableColumnCreated(TableColumn column) {
						new ColumnTJ_Status(column);
					}
				});
		tableManager.registerColumn(TranscodeFile.class,
				ColumnTJ_Completion.COLUMN_ID, new TableColumnCreationListener() {
					@Override
					public void tableColumnCreated(TableColumn column) {
						new ColumnTJ_Completion(column);
						column.setWidth(145);
					}
				});
		tableManager.registerColumn(TranscodeFile.class,
				ColumnTJ_CopiedToDevice.COLUMN_ID, new TableColumnCreationListener() {
					@Override
					public void tableColumnCreated(TableColumn column) {
						new ColumnTJ_CopiedToDevice(column);

						if (column.getTableID().endsWith(":type=1")
								|| column.getTableID().equals(TABLE_TRANSCODE_QUEUE)) {

							column.setVisible(false);
						}
					}
				});

		tableManager.registerColumn(TranscodeFile.class,
				ColumnTJ_Category.COLUMN_ID, new TableColumnCreationListener() {
					@Override
					public void tableColumnCreated(TableColumn column) {
						new ColumnTJ_Category(column);
					}
				});

		tableManager.registerColumn(TranscodeFile.class,
				ColumnTJ_Tags.COLUMN_ID, new TableColumnCreationListener() {
					@Override
					public void tableColumnCreated(TableColumn column) {
						new ColumnTJ_Tags(column);
					}
				});

		TableColumnManager tcm = TableColumnManager.getInstance();
		String[] defaultLibraryColumns = {
			ColumnTJ_Rank.COLUMN_ID,
			ColumnTJ_Name.COLUMN_ID,
			ColumnTJ_Duration.COLUMN_ID,
			ColumnTJ_Device.COLUMN_ID,
			ColumnTJ_Status.COLUMN_ID,
			ColumnTJ_Completion.COLUMN_ID,
		};
		tcm.setDefaultColumnNames(TABLE_TRANSCODE_QUEUE, defaultLibraryColumns);

		String[] defaultQColumns = {
			ColumnTJ_Name.COLUMN_ID,
			ColumnTJ_Duration.COLUMN_ID,
			ColumnTJ_Profile.COLUMN_ID,
			ColumnTJ_Status.COLUMN_ID,
			ColumnTJ_Completion.COLUMN_ID,
		};
		tcm.setDefaultColumnNames(TABLE_DEVICE_LIBRARY, defaultQColumns);
		tcm.setDefaultColumnNames(TABLE_DEVICE_LIBRARY + ":type=1", defaultQColumns);
		
	}

	// @see SkinView#skinObjectShown(SWTSkinObject, java.lang.Object)
	@Override
	public Object skinObjectShown(SWTSkinObject skinObject, Object params) {
		super.skinObjectShown(skinObject, params);

		transcode_queue.addListener(this);

		if (transTarget != null) {
			transTarget.addListener(this);
		}

		SWTSkinObject soDeviceList = getSkinObject("device-list");
		if (soDeviceList != null) {
			initDeviceListTable((Composite) soDeviceList.getControl());
		}

		SWTSkinObject soTranscodeQueue = getSkinObject("transcode-queue");
		if (soTranscodeQueue != null) {
			initTranscodeQueueTable((Composite) soTranscodeQueue.getControl());
		}

		if ( device != null ){

			device.addListener( this );
		}

		if (device instanceof TranscodeTarget){

			createDragDrop( tvFiles!=null?tvFiles:tvDevices);
		}

		setAdditionalInfoTitle(false);

		// This is bad.  Example:
		// 1) Do a search
		// 2) Sidebar entry opens under Devices
		// 3) Close search sidebar
		// 4) Device entry gets auto-selected
		// 5) User gets ftux
		// 6) User says no, anger increases
		// 7) Go to 1
		//DevicesFTUX.ensureInstalled();

		updateSelectedContent();

		return null;
	}

	/**
	 * @param soAdvInfo
	 *
	 * @since 4.1.0.5
	 */
	private void initAdvInfo(SWTSkinObject soAdvInfo) {
		SWTSkinButtonUtility btnAdvInfo = new SWTSkinButtonUtility(soAdvInfo);
		btnAdvInfo.addSelectionListener(new ButtonListenerAdapter() {
			@Override
			public void pressed(SWTSkinButtonUtility buttonUtility,
			                    SWTSkinObject skinObject, int stateMask) {
				SWTSkinObject soArea = getSkinObject("advinfo-area");
				if (soArea != null) {
					boolean newVisibility = !soArea.isVisible();
					setAdditionalInfoTitle(newVisibility);
				}
			}
		});
		setAdditionalInfoTitle(false);
	}

	/**
	 * @param newVisibility
	 *
	 * @since 4.1.0.5
	 */
	protected void setAdditionalInfoTitle(boolean newVisibility) {
		SWTSkinObject soArea = getSkinObject("advinfo-area");
		if (soArea != null) {
			soArea.setVisible(newVisibility);
		}
		SWTSkinObject soText = getSkinObject("advinfo-title");
		if (soText instanceof SWTSkinObjectText) {
			String s = (newVisibility ? "[-]" : "[+]");
			if (device != null) {
				s += "Additional Device Info and Settings";
			} else {
				s += "General Options";
			}
			((SWTSkinObjectText) soText).setText(s);
		}
	}

	// @see SkinView#skinObjectHidden(SWTSkinObject, java.lang.Object)
	@Override
	public Object skinObjectHidden(SWTSkinObject skinObject, Object params) {
		transcode_queue.removeListener(this);

		if (transTarget != null) {
			transTarget.removeListener(this);
		}

		if ( device != null ){

			device.removeListener( this );
		}

		synchronized (this) {
			if (tvFiles != null) {
				tvFiles.delete();
				tvFiles = null;
			}
		}
		Utils.disposeSWTObjects(tableJobsParent);
		if (tvDevices != null) {
			tvDevices.delete();
			tvDevices = null;
		}

		return super.skinObjectHidden(skinObject, params);
	}

	/**
	 * @param control
	 *
	 * @since 4.1.0.5
	 */
	private void initTranscodeQueueTable(Composite control) {
		String tableID;

		if (device == null) {

			tableID = TABLE_TRANSCODE_QUEUE;

		} else {

			tableID = TABLE_DEVICE_LIBRARY;

			if (device instanceof DeviceMediaRenderer) {

				DeviceMediaRenderer dmr = (DeviceMediaRenderer)device;

				if (!(dmr.canCopyToDevice()||dmr.canCopyToFolder())) {

					tableID += ":type=1";
				}
			}
		}

		tvFiles = TableViewFactory.createTableViewSWT(TranscodeFile.class, tableID,
				tableID, new TableColumnCore[0], device == null
						? ColumnTJ_Rank.COLUMN_ID : ColumnTJ_Status.COLUMN_ID, SWT.MULTI
						| SWT.FULL_SELECTION | SWT.VIRTUAL);
		tvFiles.setRowDefaultHeightEM(1.5f);
		tvFiles.setHeaderVisible(true);
		tvFiles.setParentDataSource(device);

		tableJobsParent = new Composite(control, SWT.NONE);
		tableJobsParent.setLayoutData(Utils.getFilledFormData());
		GridLayout layout = new GridLayout();
		layout.marginHeight = layout.marginWidth = layout.verticalSpacing = layout.horizontalSpacing = 0;
		tableJobsParent.setLayout(layout);

		tvFiles.addSelectionListener(new TableSelectionListener() {

			@Override
			public void selected(TableRowCore[] row) {
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
			}

			@Override
			public void deselected(TableRowCore[] rows) {
				updateSelectedContent();
			}

			@Override
			public void defaultSelected(TableRowCore[] rows, int stateMask) {
			}
		}, false);

		tvFiles.addLifeCycleListener(new TableLifeCycleListener() {
			@Override
			public void tableLifeCycleEventOccurred(TableView tv, int eventType, Map<String, Object> data) {
				switch (eventType) {
					case EVENT_TABLELIFECYCLE_INITIALIZED:
						if (transTarget == null) {
							// just add all jobs' files
							TranscodeJob[] jobs = transcode_queue.getJobs();
							for (TranscodeJob job : jobs) {
								TranscodeFile file = job.getTranscodeFile();
								if (file != null) {
									tvFiles.addDataSource(file);
								}
							}
						} else {
							tvFiles.addDataSources(transTarget.getFiles());
						}
						updateSelectedContent();
						break;
				}
			}
		});

		tvFiles.addMenuFillListener(new TableViewSWTMenuFillListener() {
			@Override
			public void fillMenu(String sColumnName, Menu menu) {
				SBC_DevicesView.this.fillMenu(menu);
			}

			@Override
			public void addThisColumnSubMenu(String columnName, Menu menuThisColumn) {
			}
		});

		tvFiles.addKeyListener(
			new KeyListener()
			{
				@Override
				public void
				keyPressed(
					KeyEvent e )
				{
					if ( e.stateMask == 0 && e.keyCode == SWT.DEL ){

						TranscodeFile[] selected;

						synchronized (this) {

							if ( tvFiles == null ){

								selected = new TranscodeFile[0];

							}else{

								List<Object> selectedDataSources = tvFiles.getSelectedDataSources();
								selected = selectedDataSources.toArray(new TranscodeFile[0]);
							}
						}

						if ( selected.length > 0 ){

							deleteFiles(selected, 0);
						}

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

		tvFiles.initialize(tableJobsParent);

		control.layout(true, true);
	}

	/**
	 * @param menu
	 *
	 * @since 4.0.0.5
	 */
	protected void fillMenu(Menu menu) {

		Object[] _files = tvFiles.getSelectedDataSources().toArray();

		final TranscodeFile[] files = new TranscodeFile[_files.length];

		System.arraycopy(_files, 0, files, 0, files.length);

		// open file

		final MenuItem open_item = new MenuItem(menu, SWT.PUSH);

		Messages.setLanguageText(open_item, "MyTorrentsView.menu.open");

		Utils.setMenuItemImage(open_item, "run");

		File target_file = null;
		File source_file = null;

		try {
			if (files.length == 1) {

				target_file = files[0].getTargetFile().getFile( true );

				if (!target_file.exists()) {

					target_file = null;
				}
			}
		} catch (Throwable e) {

			Debug.out(e);
		}

		try {
			if (files.length == 1) {

				source_file = files[0].getSourceFile().getFile( true );

				if (!source_file.exists()) {

					source_file = null;
				}
			}
		} catch (Throwable e) {

			Debug.out(e);
		}

		final File f_target_file = target_file;
		final File f_source_file = source_file;

		open_item.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent ev) {

				Utils.launch(f_target_file.getAbsolutePath());
			}
		});

		open_item.setEnabled(target_file != null);

		// show in explorer

		final boolean use_open_containing_folder = COConfigurationManager.getBooleanParameter("MyTorrentsView.menu.show_parent_folder_enabled");

		final MenuItem show_item = new MenuItem(menu, SWT.PUSH);

		Messages.setLanguageText(show_item, "MyTorrentsView.menu."
				+ (use_open_containing_folder ? "open_parent_folder" : "explore"));

		show_item.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {

				ManagerUtils.open(
						f_target_file != null ? f_target_file : f_source_file,
						use_open_containing_folder);
			}
		});

		show_item.setEnabled((source_file != null && !files[0].isComplete())
				|| (target_file != null && files[0].isComplete()));


			// category

	    Menu menu_category = new Menu(menu.getShell(), SWT.DROP_DOWN);
	    final MenuItem item_category = new MenuItem(menu, SWT.CASCADE);
	    Messages.setLanguageText(item_category, "MyTorrentsView.menu.setCategory");
	    item_category.setMenu(menu_category);

	    addCategorySubMenu( menu_category, files );

	    	// tag

	    Menu menu_tags = new Menu(menu.getShell(), SWT.DROP_DOWN);
	    final MenuItem item_tags = new MenuItem(menu, SWT.CASCADE);
	    Messages.setLanguageText(item_tags, "label.tag" );
	    item_tags.setMenu(menu_tags);

	    addTagsSubMenu( menu_tags, files );

		new MenuItem(menu, SWT.SEPARATOR);

		// pause

		final MenuItem pause_item = new MenuItem(menu, SWT.PUSH);

		pause_item.setText(MessageText.getString("v3.MainWindow.button.pause"));

		pause_item.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {

				for (int i = 0; i < files.length; i++) {
					TranscodeJob job = files[i].getJob();

					if (job != null) {
						job.pause();
					}
				}
			}
		});

		// resume

		final MenuItem resume_item = new MenuItem(menu, SWT.PUSH);

		resume_item.setText(MessageText.getString("v3.MainWindow.button.resume"));

		resume_item.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				for (int i = 0; i < files.length; i++) {
					TranscodeJob job = files[i].getJob();

					if (job != null) {
						job.resume();
					}
				}
			}
		});

		// separator

		new MenuItem(menu, SWT.SEPARATOR);

		if (device instanceof DeviceMediaRenderer) {

			DeviceMediaRenderer dmr = (DeviceMediaRenderer) device;

			if (dmr.canCopyToDevice() || dmr.canCopyToFolder()) {

				// retry

				final MenuItem retry_item = new MenuItem(menu, SWT.PUSH);

				retry_item.setText(MessageText.getString("device.retry.copy"));

				retry_item.addSelectionListener(new SelectionAdapter() {
					@Override
					public void widgetSelected(SelectionEvent e) {
						for (int i = 0; i < files.length; i++) {
							TranscodeFile file = files[i];

							if ( file.getCopyToDeviceFails() > 0 || file.isCopiedToDevice() ){

								file.retryCopyToDevice();
							}
						}
					}
				});

				retry_item.setEnabled(false);

				for (TranscodeFile file : files) {

					if ( file.getCopyToDeviceFails() > 0 || file.isCopiedToDevice()) {

						retry_item.setEnabled(true);
					}
				}

				// separator

				new MenuItem(menu, SWT.SEPARATOR);
			}
		}

		// copy stream uri

		final MenuItem sc_item = new MenuItem(menu, SWT.PUSH);

		sc_item.setText(MessageText.getString("devices.copy_url"));

		if (files.length == 1) {

			final URL url = files[0].getStreamURL();

			if (url != null) {

				sc_item.addSelectionListener(new SelectionAdapter() {
					@Override
					public void widgetSelected(SelectionEvent e) {
						ClipboardCopy.copyToClipBoard(url.toExternalForm());
					}
				});

			} else {

				sc_item.setEnabled(false);
			}
		} else {

			sc_item.setEnabled(false);
		}

		// remove

		int	comp 	= 0;
		int	incomp	= 0;

		for ( TranscodeFile f: files ){

			if ( f.isComplete()){
				comp++;
			}else{
				incomp++;
			}
		}
		final MenuItem remove_item = new MenuItem(menu, SWT.PUSH);

		String	text;

		if ( comp == 0 ){
			text = "devices.cancel_xcode";
		}else if ( incomp == 0 ){
			text = "azbuddy.ui.menu.remove";
		}else{
			text = "devices.cancel_xcode_del";
		}

		remove_item.setText(MessageText.getString(text));

		Utils.setMenuItemImage(remove_item, "delete");

		remove_item.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				deleteFiles(files, 0);
			}
		});

		// separator

		new MenuItem(menu, SWT.SEPARATOR);

		// Logic to disable items

		boolean has_selection = files.length > 0;

		remove_item.setEnabled(has_selection);

		boolean can_pause = has_selection;
		boolean can_resume = has_selection;

		int job_count = 0;

		for (int i = 0; i < files.length; i++) {
			TranscodeJob job = files[i].getJob();
			if (job == null) {
				continue;
			}

			job_count++;

			int state = job.getState();

			if (state != TranscodeJob.ST_RUNNING || !job.canPause()) {

				can_pause = false;
			}

			if (state != TranscodeJob.ST_PAUSED) {

				can_resume = false;
			}
		}

		pause_item.setEnabled(can_pause && job_count > 0);
		resume_item.setEnabled(can_resume && job_count > 0);
	}

	private void
	addCategorySubMenu(
		Menu						menu_category,
		final TranscodeFile[]		files )
	{
		MenuItem[] items = menu_category.getItems();
		int i;
		for (i = 0; i < items.length; i++) {
			items[i].dispose();
		}

		Category[] categories = CategoryManager.getCategories();
		Arrays.sort(categories);

		if (categories.length > 0) {
			Category catUncat = CategoryManager.getCategory(Category.TYPE_UNCATEGORIZED);
			if (catUncat != null) {
				final MenuItem itemCategory = new MenuItem(menu_category, SWT.PUSH);
				Messages.setLanguageText(itemCategory, catUncat.getName());
				itemCategory.setData("Category", catUncat);
				itemCategory.addListener(SWT.Selection, new Listener() {
					@Override
					public void handleEvent(Event event) {
						MenuItem item = (MenuItem)event.widget;
						assignSelectedToCategory((Category)item.getData("Category"),files);
					}
				});

				new MenuItem(menu_category, SWT.SEPARATOR);
			}

			for (i = 0; i < categories.length; i++) {
				if (categories[i].getType() == Category.TYPE_USER) {
					final MenuItem itemCategory = new MenuItem(menu_category, SWT.PUSH);
					itemCategory.setText(categories[i].getName());
					itemCategory.setData("Category", categories[i]);

					TagUIUtils.setMenuIcon( itemCategory, categories[i] );
					
					itemCategory.addListener(SWT.Selection, new Listener() {
						@Override
						public void handleEvent(Event event) {
							MenuItem item = (MenuItem)event.widget;
							assignSelectedToCategory((Category)item.getData("Category"),files);
						}
					});
				}
			}

			new MenuItem(menu_category, SWT.SEPARATOR);
		}

		final MenuItem itemAddCategory = new MenuItem(menu_category, SWT.PUSH);
		Messages.setLanguageText(itemAddCategory,
		"MyTorrentsView.menu.setCategory.add");

		itemAddCategory.addListener(SWT.Selection, new Listener() {
			@Override
			public void handleEvent(Event event) {
				addCategory(files);
			}
		});

	}

	private void
	addCategory(
		TranscodeFile[]		files )
	{
		CategoryUIUtils.showCreateCategoryDialog(new TagReturner() {
			@Override
			public void returnedTags(Tag[] tags) {
				if (tags.length == 1 && tags[0] instanceof Category) {
					assignSelectedToCategory((Category) tags[0], files);
				}
			}
		});
	}

	private void
	assignSelectedToCategory(
		Category 			category,
		TranscodeFile[]		files )
	{
		String[]	cats;

		if ( category.getType() == Category.TYPE_UNCATEGORIZED ){

			cats = new String[0];

		}else{

			cats = new String[]{ category.getName()};
		}

		for ( TranscodeFile file: files ){

			file.setCategories( cats );
		}
	}

	private void
	addTagsSubMenu(
		Menu						menu_tags,
		final TranscodeFile[]		files )
	{
		TagManager tm = TagManagerFactory.getTagManager();

		Map<Tag, Integer> mapTaggableCount = new HashMap<>();

		for ( TranscodeFile file: files ){
			String[] tags = file.getTags(false);
			for (String sTagUID : tags) {
				try {
					Tag tag = tm.lookupTagByUID(Long.parseLong(sTagUID));
					if (tag != null) {
						mapTaggableCount.compute(tag, (t, i) -> i == null ? 1 : i + 1);
					}
				} catch (Throwable ignore) {
				}
			}
		}

		if ( !mapTaggableCount.isEmpty() ){

			final MenuItem mi_no_tag = new MenuItem( menu_tags, SWT.PUSH );

			mi_no_tag.setText( MessageText.getString( "label.no.tag" ));

			mi_no_tag.addListener(SWT.Selection, event -> {

				for ( TranscodeFile file: files ){

					file.setTags( new String[0] );
				}
			});

			new MenuItem( menu_tags, SWT.SEPARATOR );
		}

		TagMenuOptions.Builder builder = TagMenuOptions.Builder()
			.setShowAddMenu(true)
			.setMenuForAutoTags(false)
			.setTagMenuFilter(TagMenuOptions.FILTER_NO_AUTOADD)
			.setMapTaggableCount(mapTaggableCount, files.length)
			.setTagSelectionListener((t, checked) -> {

				String 	tag_uid = String.valueOf( t.getTagUID());

				for ( TranscodeFile file: files ){

					Set<String> uids = new TreeSet<>(Arrays.asList(file.getTags(false)));

					boolean	update = false;

					if ( checked ){

						if ( !uids.contains(tag_uid)){

							uids.add( tag_uid );

							update = true;
						}
					}else{

						if ( uids.contains( tag_uid )){

							uids.remove( tag_uid );

							update = true;
						}
					}

					if ( update ){

						file.setTags( uids.toArray(new String[0]));
					}
				}
			});
		TagUIUtils.createTagSelectionMenu(builder, menu_tags);
	}

	@Override
	public void
	deviceChanged(
		Device		device )
	{
		String name = device.getName();

		if ( !name.equals( device_name )){

			device_name = name;

			// ensure name is up to date
			SWTSkinObject soTitle = getSkinObject("title");
			if (soTitle instanceof SWTSkinObjectText) {
				((SWTSkinObjectText) soTitle).setTextID("device.view.heading",
						new String[] {
							name
						});
			}
		}
	}


	/**
	 *
	 *
	 * @param parent
	 * @since 4.1.0.5
	 */
	private void initDeviceListTable(Composite control) {
		tvDevices = TableViewFactory.createTableViewSWT(TranscodeProvider.class, TABLE_DEVICES,
				TABLE_DEVICES, new TableColumnCore[0], ColumnTJ_Rank.COLUMN_ID, SWT.SINGLE
				| SWT.FULL_SELECTION);
		tvDevices.setRowDefaultHeightEM(1.5f);
		tvDevices.setHeaderVisible(true);



		Composite parent = new Composite(control, SWT.NONE);
		parent.setLayoutData(Utils.getFilledFormData());
		GridLayout layout = new GridLayout();
		layout.marginHeight = layout.marginWidth = layout.verticalSpacing = layout.horizontalSpacing = 0;
		parent.setLayout(layout);

		tvDevices.initialize(parent);
	}

	// @see com.biglybt.core.devices.TranscodeQueueListener#jobAdded(com.biglybt.core.devices.TranscodeJob)
	@Override
	public void jobAdded(TranscodeJob job) {
		synchronized (this) {
			if (tvFiles == null) {
				return;
			}

			if (transTarget == null) {
				TranscodeFile file = job.getTranscodeFile();
				if (file != null) {
					tvFiles.addDataSource(file);
				}
			}
		}
	}

	// @see com.biglybt.core.devices.TranscodeQueueListener#jobChanged(com.biglybt.core.devices.TranscodeJob)
	@Override
	public void jobChanged(TranscodeJob job) {
		synchronized (this) {
			if (tvFiles == null) {
				return;
			}
			TableRowCore row = tvFiles.getRow( getFileInTable( job.getTranscodeFile()));
			if (row != null) {
				row.invalidate();
				if (row.isVisible()) {
					UIFunctions uiFunctions = UIFunctionsManager.getUIFunctions();
					if (uiFunctions != null) {
						uiFunctions.refreshIconBar();
					}
				}
			}
		}
	}

	// @see com.biglybt.core.devices.TranscodeQueueListener#jobRemoved(com.biglybt.core.devices.TranscodeJob)
	@Override
	public void jobRemoved(TranscodeJob job) {
		synchronized (this) {
			if (tvFiles == null) {
				return;
			}
			if (transTarget == null) {
				TranscodeFile file = job.getTranscodeFile();
				if (file != null) {
					tvFiles.removeDataSource( getFileInTable( file ));
				}
			} else {
				TableRowCore row = tvFiles.getRow( getFileInTable( job.getTranscodeFile()));
				if (row != null) {
					row.invalidate();
					if (row.isVisible()) {
						UIFunctions uiFunctions = UIFunctionsManager.getUIFunctions();
						if (uiFunctions != null) {
							uiFunctions.refreshIconBar();
						}
					}
				}
			}
		}
	}

	private TranscodeFile
	getFileInTable(
		TranscodeFile		file )
	{
		// since table-views were moved to using identity hash maps to manage rows (which is good!) this has broken
		// removal of files as due to the caching optimisations employed by the device manager muliple file-facades
		// can be created to denote an actual TranscodeFile :(

		if ( file == null ){

			return( null );
		}

		if ( tvFiles.getRow( file ) == null ){

			Collection<TranscodeFile>	files = tvFiles.getDataSources();

			for ( TranscodeFile f: files ){

				if ( f.equals( file )){

					return( f );
				}
			}
		}

		return( file );
	}

	/* (non-Javadoc)
	 * @see com.biglybt.pif.ui.UIPluginViewToolBarListener#refreshToolBarItems(java.util.Map)
	 */
	@Override
	public void refreshToolBarItems(Map<String, Long> list) {
		Object[] selectedDS;
		int size;
		synchronized (this) {
			if (tvFiles == null) {
				return;
			}
			selectedDS = tvFiles.getSelectedDataSources().toArray();
			size = tvFiles.size(false);
		}
		if (selectedDS.length == 0) {
			return;
		}

		list.put("remove", UIToolBarItem.STATE_ENABLED);

		boolean can_stop = true;
		boolean can_queue = true;
		boolean can_move_up = true;
		boolean can_move_down = true;
		boolean hasJob = false;

		for (Object ds : selectedDS) {
			TranscodeJob job = ((TranscodeFile) ds).getJob();

			if (job == null) {
				continue;
			}

			hasJob = true;

			int index = job.getIndex();

			if (index == 1) {

				can_move_up = false;

			}

			if (index == size) {

				can_move_down = false;
			}

			int state = job.getState();

			if (state != TranscodeJob.ST_PAUSED && state != TranscodeJob.ST_RUNNING
					&& state != TranscodeJob.ST_FAILED && state != TranscodeJob.ST_QUEUED) {

				can_stop = false;
			}

			if (state != TranscodeJob.ST_PAUSED && state != TranscodeJob.ST_STOPPED
					&& state != TranscodeJob.ST_FAILED) {

				can_queue = false;
			}
		}

		if (!hasJob) {
			can_stop = can_queue = can_move_down = can_move_up = false;
		}

		if ( can_queue && can_stop ){
			can_stop = false;
		}

		list.put("stop", can_stop ? UIToolBarItem.STATE_ENABLED : 0);
		list.put("start", can_queue ? UIToolBarItem.STATE_ENABLED : 0);
		list.put("up", can_move_up ? UIToolBarItem.STATE_ENABLED : 0);
		list.put("down", can_move_down ? UIToolBarItem.STATE_ENABLED : 0);

		if ( selectedDS.length == 1 ){

			TranscodeFile f = (TranscodeFile)selectedDS[0];

			if ( f.isComplete() && f.getStreamURL() != null ){

				try{
					if( PlayUtils.canUseEMP( f.getTargetFile())){

						list.put( "play", UIToolBarItem.STATE_ENABLED );
					}
				}catch( Throwable e ){

					Debug.out( e );
				}
			}
		}
	}

	/* (non-Javadoc)
	 * @see com.biglybt.pif.ui.toolbar.UIToolBarActivationListener#toolBarItemActivated(ToolBarItem, long, java.lang.Object)
	 */
	@Override
	public boolean toolBarItemActivated(ToolBarItem item, long activationType,
	                                    Object datasource) {
		// assumed to be on SWT thread, so it's safe to use tvFiles without a sync
		if (tvFiles == null) {
			return false;
		}

		TranscodeFile[] selectedDS = tvFiles.getSelectedDataSources().toArray(new TranscodeFile[0]);
		if (selectedDS.length == 0) {
			return false;
		}

		String itemKey = item.getID();

		if (itemKey.equals("remove")) {
			deleteFiles(selectedDS, 0);
			return true;
		}

		if ( itemKey.equals( "play" )){

			if ( selectedDS.length == 1 ){

				TranscodeFile f = selectedDS[0];

				if ( TorrentListViewsUtils.openInEMP( f.getName(), f.getStreamURL()) == 0 ){

					return( true );
				}
			}
		}

		java.util.List<TranscodeJob> jobs = new ArrayList<>(selectedDS.length);

		boolean can_stop = true;
		boolean can_queue = true;

		for (int i = 0; i < selectedDS.length; i++) {
			TranscodeFile file = selectedDS[i];
			TranscodeJob job = file.getJob();
			if (job != null) {
				jobs.add(job);

				int state = job.getState();

				if (state != TranscodeJob.ST_PAUSED && state != TranscodeJob.ST_RUNNING
						&& state != TranscodeJob.ST_FAILED && state != TranscodeJob.ST_QUEUED) {

					can_stop = false;
				}

				if (state != TranscodeJob.ST_PAUSED && state != TranscodeJob.ST_STOPPED
						&& state != TranscodeJob.ST_FAILED) {

					can_queue = false;
				}
			}
		}

		if (jobs.size() == 0) {
			return false;
		}

		if ( can_queue && can_stop ){
			can_stop = false;
		}

		if (itemKey.equals("up") || itemKey.equals("down")) {

			final String f_itemKey = itemKey;

			Collections.sort(jobs, new Comparator<TranscodeJob>() {
				@Override
				public int compare(TranscodeJob j1, TranscodeJob j2) {

					return ((f_itemKey.equals("up") ? 1 : -1) * (j1.getIndex() - j2.getIndex()));
				}
			});
		}

		if ( itemKey.equals( "startstop" )){

			if ( can_queue ){
				itemKey = "start";
			}else if ( can_stop ){
				itemKey = "stop";
			}

		}
		boolean didSomething = false;
		boolean forceSort = false;
		for (TranscodeJob job : jobs) {

			if (itemKey.equals("stop")) {

				job.stop();
				didSomething = true;

			} else if (itemKey.equals("start")) {

				didSomething = true;
				job.queue();

			} else if (itemKey.equals("up")) {

				didSomething = true;
				job.moveUp();

				TableColumnCore[] sortColumn = tvFiles.getSortColumns();
				forceSort = sortColumn.length != 0
					&& ColumnTJ_Rank.COLUMN_ID.equals(sortColumn[0].getName());

			} else if (itemKey.equals("down")) {

				didSomething = true;
				job.moveDown();

				TableColumnCore[] sortColumn = tvFiles.getSortColumns();
				forceSort = sortColumn.length != 0
					&& ColumnTJ_Rank.COLUMN_ID.equals(sortColumn[0].getName());
			}
		}

		tvFiles.refreshTable(forceSort);

		return didSomething;
	}

	// @see UIUpdatable#getUpdateUIName()
	@Override
	public String getUpdateUIName() {
		return "DevicesView";
	}

	// @see UIUpdatable#updateUI()
	@Override
	public void updateUI() {
		if (tvFiles != null) {
			tvFiles.refreshTable(false);
		}
	}

	// @see com.biglybt.core.devices.TranscodeTargetListener#fileAdded(TranscodeFile)
	@Override
	public void fileAdded(TranscodeFile file) {
		synchronized (this) {
			if (tvFiles != null) {
				tvFiles.addDataSource(file);
			}
		}
	}

	// @see com.biglybt.core.devices.TranscodeTargetListener#fileChanged(TranscodeFile, int, java.lang.Object)
	@Override
	public void fileChanged(TranscodeFile file, int type, Object data) {
		TableRowCore row;
		synchronized (this) {
			if (tvFiles == null) {
				return;
			}
			row = tvFiles.getRow( getFileInTable( file ));
		}
		if (row != null) {
			row.invalidate();
			if (row.isVisible()) {
				UIFunctions uiFunctions = UIFunctionsManager.getUIFunctions();
				if (uiFunctions != null) {
					uiFunctions.refreshIconBar();
				}
			}
		}
	}

	// @see com.biglybt.core.devices.TranscodeTargetListener#fileRemoved(TranscodeFile)
	@Override
	public void fileRemoved(TranscodeFile file) {
		synchronized (this) {
			if (tvFiles != null) {
				tvFiles.removeDataSource( getFileInTable( file ));
			}
		}
	}

	protected void deleteFiles(final TranscodeFile[] toRemove, final int startIndex) {
		if (toRemove[startIndex] == null) {
			int nextIndex = startIndex + 1;
			if (nextIndex < toRemove.length) {
				deleteFiles(toRemove, nextIndex);
			}
			return;
		}

		final TranscodeFile file = toRemove[startIndex];
		try {

			File cache_file = file.getCacheFileIfExists();

			if (cache_file != null && cache_file.exists() && file.isComplete()) {

				String path = cache_file.toString();

				String title = MessageText.getString("xcode.deletedata.title");

				String copy_text = "";

				Device device = file.getDevice();

				if (device instanceof DeviceMediaRenderer) {

					DeviceMediaRenderer dmr = (DeviceMediaRenderer)device;

					File copy_to = dmr.getCopyToFolder();

					if ( dmr.canCopyToDevice() || ( dmr.canCopyToFolder() && copy_to != null && copy_to.exists())){

						copy_text = MessageText.getString("xcode.deletedata.message.2",
								new String[] {
									device.getName()
								});
					}
				}

				String text = MessageText.getString("xcode.deletedata.message",
						new String[] {
							file.getName(),
							file.getProfileName(),
							copy_text
						});

				MessageBoxShell mb = new MessageBoxShell(title, text);
				mb.setRemember("xcode.deletedata.noconfirm.key", false,
						MessageText.getString("deletedata.noprompt"));

				if (startIndex == toRemove.length - 1) {
  				mb.setButtons(0, new String[] {
  					MessageText.getString("Button.yes"),
  					MessageText.getString("Button.no"),
  				}, new Integer[] { 0, 1 });
  				mb.setRememberOnlyIfButton(0);
				} else {
  				mb.setButtons(1, new String[] {
  					MessageText.getString("Button.removeAll"),
  					MessageText.getString("Button.yes"),
  					MessageText.getString("Button.no"),
  				}, new Integer[] { 2, 0, 1 });
  				mb.setRememberOnlyIfButton(1);
				}

				DownloadManager dm = null;

				if (dm != null) {

					mb.setRelatedObject(dm);
				}

				mb.setLeftImage(SWT.ICON_WARNING);

				mb.open(new UserPrompterResultListener() {
					@Override
					public void prompterClosed(int result) {
						if (result == -1) {
							return;
						} else if (result == 0) {
							deleteNoCheck(file);
						} else if (result == 2) {
							for (int i = startIndex; i < toRemove.length; i++) {
								if (toRemove[i] != null) {
									deleteNoCheck(toRemove[i]);
								}
							}
							return;
						}

						int nextIndex = startIndex + 1;
						if (nextIndex < toRemove.length) {
							deleteFiles(toRemove, nextIndex);
						}
					}
				});

			} else {

				deleteNoCheck(file);

				int nextIndex = startIndex + 1;
				if (nextIndex < toRemove.length) {
					deleteFiles(toRemove, nextIndex);
				}
			}
		} catch (Throwable e) {

			Debug.out(e);
		}
	}

	private void deleteNoCheck(TranscodeFile file) {
		TranscodeJob job = file.getJob();

		if (job != null) {

			try{
				job.remove();

			}catch( TranscodeActionVetoException e ){

				UIFunctionsManager.getUIFunctions().forceNotify(
						UIFunctions.STATUSICON_WARNING,
						MessageText.getString( "globalmanager.download.remove.veto" ),
						e.getMessage(), null, null, -1 );

				return;
			}
		}

		try {
			file.delete(file.getCacheFileIfExists() != null);
		} catch (TranscodeException e) {
			Debug.out(e);
		}
	}

	private void
	createDragDrop(
		final TableViewSWT<?>		table )
	{
		try {

			Transfer[] types = new Transfer[] { TextTransfer.getInstance() };

			DragSource dragSource = table.createDragSource(
					DND.DROP_MOVE | DND.DROP_COPY);
			if (dragSource != null) {
				dragSource.setTransfer(types);
				dragSource.addDragListener(new DragSourceAdapter() {
					private String eventData;

					@Override
					public void dragStart(DragSourceEvent event) {
						TableRowCore[] rows = table.getSelectedRows();
						if (rows.length != 0) {
							event.doit = true;
							// System.out.println("DragStart");
							drag_drop_line_start = rows[0].getIndex();
							drag_drop_rows = rows;
						} else {
							event.doit = false;
							drag_drop_line_start = -1;
							drag_drop_rows = null;
						}

						// Build eventData here because on OSX, selection gets cleared
						// by the time dragSetData occurs

						java.util.List selectedFiles = table.getSelectedDataSources();

						eventData="TranscodeFile\n";

						for ( Object o: selectedFiles ){

							TranscodeFile file = (TranscodeFile)o;

							if ( file.isComplete()){

								try{
									eventData += file.getTargetFile().getFile( true ).getAbsolutePath() + "\n";

								}catch( Throwable e ){

								}
							}
						}
					}

					@Override
					public void dragSetData(DragSourceEvent event) {
						// System.out.println("DragSetData");
						event.data = eventData;
					}
				});
			}

			DropTarget dropTarget = table.createDropTarget(
					DND.DROP_DEFAULT | DND.DROP_MOVE | DND.DROP_COPY | DND.DROP_LINK
							| DND.DROP_TARGET_MOVE);
			if (dropTarget != null) {
				dropTarget.setTransfer(new Transfer[] { FixedHTMLTransfer.getInstance(),
						FixedURLTransfer.getInstance(), FileTransfer.getInstance(),
						TextTransfer.getInstance() });

				dropTarget.addDropListener(new DropTargetAdapter() {
					@Override
					public void dropAccept(DropTargetEvent event) {
						event.currentDataType = FixedURLTransfer.pickBestType(event.dataTypes,
								event.currentDataType);
					}

					@Override
					public void dragEnter(DropTargetEvent event) {
						// no event.data on dragOver, use drag_drop_line_start to determine
						// if ours
						if (drag_drop_line_start < 0) {
							if (event.detail != DND.DROP_COPY) {
								if ((event.operations & DND.DROP_LINK) > 0)
									event.detail = DND.DROP_LINK;
								else if ((event.operations & DND.DROP_COPY) > 0)
									event.detail = DND.DROP_COPY;
							}
						} else if (TextTransfer.getInstance().isSupportedType(
								event.currentDataType)) {
							event.detail = event.item == null ? DND.DROP_NONE : DND.DROP_MOVE;
							event.feedback = DND.FEEDBACK_SCROLL | DND.FEEDBACK_INSERT_BEFORE;
						}
					}

					@Override
					public void dragOver(DropTargetEvent event) {
						if (drag_drop_line_start >= 0) {
							event.detail = event.item == null ? DND.DROP_NONE : DND.DROP_MOVE;
							event.feedback = DND.FEEDBACK_SCROLL | DND.FEEDBACK_INSERT_BEFORE;
						}
					}

					@Override
					public void drop(DropTargetEvent event) {
						try{
							if ( 	event.data instanceof String &&
									((String) event.data).startsWith("TranscodeFile\n")) {

									// todo: support drag and drop reordering of xcode queue?

								return;
							}

							event.detail = DND.DROP_NONE;

							DeviceManagerUI.handleDrop((TranscodeTarget)device, event.data );

						}finally{

							drag_drop_line_start = -1;
							drag_drop_rows = null;
						}
					}
				});
			}

		} catch (Throwable t) {
			Debug.out( "failed to init drag-n-drop", t);
		}
	}

	public void updateSelectedContent() {
		TableView tv = tvFiles!=null?tvFiles:tvDevices;
		Object[] dataSources = tv.getSelectedDataSources(true);
		List<SelectedContent> listSelected = new ArrayList<>(dataSources.length);
		for (Object ds : dataSources) {
			if (ds instanceof DiskManagerFileInfo) {
				DiskManagerFileInfo fileInfo = (DiskManagerFileInfo) ds;
				listSelected.add(new SelectedContent(fileInfo.getDownloadManager(),
						fileInfo.getIndex()));
			}else if ( ds instanceof TranscodeFile ){
				TranscodeFile tf = (TranscodeFile)ds;
				
				listSelected.add( new SelectedContent( tf.getName()));
			}
		}
		SelectedContent[] sc = listSelected.toArray(new SelectedContent[0]);
		SelectedContentManager.changeCurrentlySelectedContent(tv.getTableID(), sc, tv);
	}
}
