/*
 * Created on May 10, 2013
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

package com.biglybt.ui.swt.views.skin;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import com.biglybt.pif.ui.UIInputReceiver;
import com.biglybt.pif.ui.UIInputReceiverListener;
import com.biglybt.pif.ui.UIInstance;
import com.biglybt.pif.ui.UIManager;
import com.biglybt.pif.ui.UIManagerListener;
import com.biglybt.pif.ui.tables.TableColumn;
import com.biglybt.pifimpl.local.PluginInitializer;
import com.biglybt.ui.UIFunctionsManager;
import com.biglybt.ui.common.ToolBarItem;
import com.biglybt.ui.common.table.*;
import com.biglybt.ui.common.table.impl.TableColumnManager;
import com.biglybt.ui.common.table.impl.TableViewImpl;
import com.biglybt.ui.selectedcontent.SelectedContentManager;
import com.biglybt.ui.swt.columns.tag.*;
import com.biglybt.ui.swt.mdi.MdiEntrySWT;
import com.biglybt.ui.swt.skin.SWTSkinObjectTextbox;
import com.biglybt.ui.swt.utils.TagUIUtilsV3;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.*;
import com.biglybt.core.config.COConfigurationManager;
import com.biglybt.core.internat.MessageText;
import com.biglybt.core.util.Constants;
import com.biglybt.core.util.Debug;
import com.biglybt.pif.ui.UIPluginViewToolBarListener;
import com.biglybt.pif.ui.tables.TableColumnCreationListener;
import com.biglybt.pif.ui.toolbar.UIToolBarItem;
import com.biglybt.ui.swt.Messages;
import com.biglybt.ui.swt.SimpleTextEntryWindow;
import com.biglybt.ui.swt.Utils;
import com.biglybt.ui.swt.mainwindow.MenuFactory;
import com.biglybt.ui.swt.pif.UISWTInstance;
import com.biglybt.ui.swt.pifimpl.UISWTViewCore;
import com.biglybt.ui.swt.shells.MessageBoxShell;
import com.biglybt.ui.swt.views.MyTorrentsSubView;
import com.biglybt.ui.swt.views.TagSettingsView;
import com.biglybt.ui.swt.views.table.TableViewSWT;
import com.biglybt.ui.swt.views.table.TableViewSWTMenuFillListener;
import com.biglybt.ui.swt.views.table.impl.TableViewFactory;
import com.biglybt.ui.swt.views.table.impl.TableViewSWT_TabsCommon;
import com.biglybt.ui.swt.views.utils.TagUIUtils;

import com.biglybt.core.tag.*;
import com.biglybt.core.util.RegExUtil;
import com.biglybt.ui.UIFunctions;
import com.biglybt.ui.UserPrompterResultListener;
import com.biglybt.ui.common.updater.UIUpdatable;
import com.biglybt.ui.swt.UIFunctionsManagerSWT;
import com.biglybt.ui.swt.UIFunctionsSWT;
import com.biglybt.ui.swt.skin.SWTSkinButtonUtility;
import com.biglybt.ui.swt.skin.SWTSkinButtonUtility.ButtonListenerAdapter;
import com.biglybt.ui.swt.skin.SWTSkinObject;
import com.biglybt.ui.swt.skin.SWTSkinObjectButton;

/**
 * @author TuxPaper
 * @created May 10, 2013
 *
 */
public class SBC_TagsOverview
	extends SkinView
	implements 	UIUpdatable, UIPluginViewToolBarListener, TableViewFilterCheck<Tag>, TagManagerListener, TagTypeListener,
				TableViewSWTMenuFillListener, TableSelectionListener
{

	private static final String TABLE_TAGS = "TagsView";

	TableViewSWT<Tag> tv;

	private Text txtFilter;

	private Composite table_parent;

	private boolean columnsAdded = false;

	private boolean tm_listener_added;

	private boolean registeredCoreSubViews;

	private Object datasource;

	// @see com.biglybt.pif.ui.toolbar.UIToolBarActivationListener#toolBarItemActivated(ToolBarItem, long, java.lang.Object)
	@Override
	public boolean toolBarItemActivated(ToolBarItem item, long activationType,
	                                    Object datasource) {
		// Send to active view.  mostly works
		// except MyTorrentsSubView always takes focus after tag is selected..
	  boolean isTableSelected = false;
	  if (tv instanceof TableViewImpl) {
	  	isTableSelected = ((TableViewImpl) tv).isTableSelected();
	  }
	  if (!isTableSelected) {
  		UISWTViewCore active_view = getActiveView();
  		if (active_view != null) {
  			UIPluginViewToolBarListener l = active_view.getToolBarListener();
  			if (l != null && l.toolBarItemActivated(item, activationType, datasource)) {
  				return true;
  			}
  		}
  		return false;
	  }

	  if ( tv == null || !tv.isVisible()){
			return( false );
		}
		if (item.getID().equals("remove")) {


			Object[] datasources = tv.getSelectedDataSources().toArray();

			if ( datasources.length > 0 ){

				for (Object object : datasources) {
					if (object instanceof Tag) {
						final Tag tag = (Tag) object;
						if (tag.getTagType().getTagType() != TagType.TT_DOWNLOAD_MANUAL) {
							continue;
						}

						MessageBoxShell mb =
								new MessageBoxShell(
									MessageText.getString("message.confirm.delete.title"),
									MessageText.getString("message.confirm.delete.text",
											new String[] {
												tag.getTagName(true)
											}),
									new String[] {
										MessageText.getString("Button.yes"),
										MessageText.getString("Button.no")
									},
									1 );

							mb.open(new UserPrompterResultListener() {
								@Override
								public void prompterClosed(int result) {
									if (result == 0) {
										tag.removeTag();
									}
								}
							});

					}
				}

				return true;
			}
		}

		return false;
	}

	private MdiEntrySWT getActiveView() {
		TableViewSWT_TabsCommon tabsCommon = tv.getTabsCommon();
		if (tabsCommon != null) {
			return tabsCommon.getActiveSubView();
		}
		return null;
	}


	// @see TableViewFilterCheck#filterSet(java.lang.String)
	@Override
	public void filterSet(String filter) {
	}

	// @see com.biglybt.pif.ui.UIPluginViewToolBarListener#refreshToolBarItems(java.util.Map)
	@Override
	public void refreshToolBarItems(Map<String, Long> list) {
		if ( tv == null || !tv.isVisible()){
			return;
		}

		boolean canEnable = false;
		Object[] datasources = tv.getSelectedDataSources().toArray();

		if ( datasources.length > 0 ){

			for (Object object : datasources) {
				if (object instanceof Tag) {
					Tag tag = (Tag) object;
					if (tag.getTagType().getTagType() == TagType.TT_DOWNLOAD_MANUAL) {
						canEnable = true;
						break;
					}
				}
			}
		}

		list.put("remove", canEnable ? UIToolBarItem.STATE_ENABLED : 0);
	}

	// @see UIUpdatable#updateUI()
	@Override
	public void updateUI() {
		if (tv != null) {
			tv.refreshTable(false);
		}
	}

	// @see UIUpdatable#getUpdateUIName()
	@Override
	public String getUpdateUIName() {
		return "TagsView";
	}

	// @see SkinView#skinObjectInitialShow(SWTSkinObject, java.lang.Object)
	@Override
	public Object skinObjectInitialShow(SWTSkinObject skinObject, Object params) {
		initColumns();

		SWTSkinObjectButton soAddTagButton = (SWTSkinObjectButton) getSkinObject("add-tag");
		if (soAddTagButton != null) {
			soAddTagButton.addSelectionListener(new ButtonListenerAdapter() {
				// @see SWTSkinButtonUtility.ButtonListenerAdapter#pressed(SWTSkinButtonUtility, SWTSkinObject, int)
				@Override
				public void pressed(SWTSkinButtonUtility buttonUtility,
				                    SWTSkinObject skinObject, int stateMask) {
					TagUIUtilsV3.showCreateTagDialog(null);
				}
			});
		}

		new InfoBarUtil(skinObject, "tagsview.infobar", false,
				"tags.infobar", "tags.view.infobar") {
			@Override
			public boolean allowShow() {
				return true;
			}
		};

		return null;
	}

	protected void initColumns() {
		synchronized (SBC_TagsOverview.class) {

			if (columnsAdded) {

				return;
			}

			columnsAdded = true;
		}

		TableColumnManager tableManager = TableColumnManager.getInstance();

		tableManager.registerColumn(Tag.class, ColumnTagCount.COLUMN_ID,
				new TableColumnCreationListener() {
					@Override
					public void tableColumnCreated(TableColumn column) {
						new ColumnTagCount(column);
					}
				});
		tableManager.registerColumn(Tag.class, ColumnTagColor.COLUMN_ID,
				new TableColumnCreationListener() {
					@Override
					public void tableColumnCreated(TableColumn column) {
						new ColumnTagColor(column);
					}
				});
		tableManager.registerColumn(Tag.class, ColumnTagName.COLUMN_ID,
				new TableColumnCreationListener() {
					@Override
					public void tableColumnCreated(TableColumn column) {
						new ColumnTagName(column);
					}
				});
		tableManager.registerColumn(Tag.class, ColumnTagType.COLUMN_ID,
				new TableColumnCreationListener() {
					@Override
					public void tableColumnCreated(TableColumn column) {
						new ColumnTagType(column);
					}
				});

		tableManager.registerColumn(Tag.class, ColumnTagPublic.COLUMN_ID,
				new TableColumnCreationListener() {
					@Override
					public void tableColumnCreated(TableColumn column) {
						new ColumnTagPublic(column);
					}
				});

		tableManager.registerColumn(Tag.class, ColumnTagUpRate.COLUMN_ID,
				new TableColumnCreationListener() {
					@Override
					public void tableColumnCreated(TableColumn column) {
						new ColumnTagUpRate(column);
					}
				});

		tableManager.registerColumn(Tag.class, ColumnTagDownRate.COLUMN_ID,
				new TableColumnCreationListener() {
					@Override
					public void tableColumnCreated(TableColumn column) {
						new ColumnTagDownRate(column);
					}
				});

		tableManager.registerColumn(Tag.class, ColumnTagUpLimit.COLUMN_ID,
				new TableColumnCreationListener() {
					@Override
					public void tableColumnCreated(TableColumn column) {
						new ColumnTagUpLimit(column);
					}
				});

		tableManager.registerColumn(Tag.class, ColumnTagDownLimit.COLUMN_ID,
				new TableColumnCreationListener() {
					@Override
					public void tableColumnCreated(TableColumn column) {
						new ColumnTagDownLimit(column);
					}
				});

		tableManager.registerColumn(Tag.class, ColumnTagUpSession.COLUMN_ID,
				new TableColumnCreationListener() {
					@Override
					public void tableColumnCreated(TableColumn column) {
						new ColumnTagUpSession(column);
					}
				});

		tableManager.registerColumn(Tag.class, ColumnTagDownSession.COLUMN_ID,
				new TableColumnCreationListener() {
					@Override
					public void tableColumnCreated(TableColumn column) {
						new ColumnTagDownSession(column);
					}
				});

		tableManager.registerColumn(Tag.class, ColumnTagUpTotal.COLUMN_ID,
				new TableColumnCreationListener() {
					@Override
					public void tableColumnCreated(TableColumn column) {
						new ColumnTagUpTotal(column);
					}
				});

		tableManager.registerColumn(Tag.class, ColumnTagDownTotal.COLUMN_ID,
				new TableColumnCreationListener() {
					@Override
					public void tableColumnCreated(TableColumn column) {
						new ColumnTagDownTotal(column);
					}
				});

		tableManager.registerColumn(Tag.class, ColumnTagRSSFeed.COLUMN_ID,
				new TableColumnCreationListener() {
					@Override
					public void tableColumnCreated(TableColumn column) {
						new ColumnTagRSSFeed(column);
					}
				});

		tableManager.registerColumn(Tag.class, ColumnTagUploadPriority.COLUMN_ID,
				new TableColumnCreationListener() {
					@Override
					public void tableColumnCreated(TableColumn column) {
						new ColumnTagUploadPriority(column);
					}
				});

		tableManager.registerColumn(Tag.class, ColumnTagMinSR.COLUMN_ID,
				new TableColumnCreationListener() {
					@Override
					public void tableColumnCreated(TableColumn column) {
						new ColumnTagMinSR(column);
					}
				});

		tableManager.registerColumn(Tag.class, ColumnTagMaxSR.COLUMN_ID,
				new TableColumnCreationListener() {
					@Override
					public void tableColumnCreated(TableColumn column) {
						new ColumnTagMaxSR(column);
					}
				});

		tableManager.registerColumn(Tag.class, ColumnTagAggregateSR.COLUMN_ID,
				new TableColumnCreationListener() {
					@Override
					public void tableColumnCreated(TableColumn column) {
						new ColumnTagAggregateSR(column);
					}
				});

		tableManager.registerColumn(Tag.class, ColumnTagAggregateSRMax.COLUMN_ID,
				new TableColumnCreationListener() {
					@Override
					public void tableColumnCreated(TableColumn column) {
						new ColumnTagAggregateSRMax(column);
					}
				});

		tableManager.registerColumn(Tag.class, ColumnTagXCode.COLUMN_ID,
				new TableColumnCreationListener() {
					@Override
					public void tableColumnCreated(TableColumn column) {
						new ColumnTagXCode(column);
					}
				});

		tableManager.registerColumn(Tag.class, ColumnTagInitialSaveLocation.COLUMN_ID,
				new TableColumnCreationListener() {
					@Override
					public void tableColumnCreated(TableColumn column) {
						new ColumnTagInitialSaveLocation(column);
					}
				});

		tableManager.registerColumn(Tag.class, ColumnTagMoveOnComp.COLUMN_ID,
				new TableColumnCreationListener() {
					@Override
					public void tableColumnCreated(TableColumn column) {
						new ColumnTagMoveOnComp(column);
					}
				});

		tableManager.registerColumn(Tag.class, ColumnTagCopyOnComp.COLUMN_ID,
				new TableColumnCreationListener() {
					@Override
					public void tableColumnCreated(TableColumn column) {
						new ColumnTagCopyOnComp(column);
					}
				});

		tableManager.registerColumn(Tag.class, ColumnTagMoveOnRemove.COLUMN_ID,
				new TableColumnCreationListener() {
					@Override
					public void tableColumnCreated(TableColumn column) {
						new ColumnTagMoveOnRemove(column);
					}
				});

		tableManager.registerColumn(Tag.class, ColumnTagProperties.COLUMN_ID,
				new TableColumnCreationListener() {
					@Override
					public void tableColumnCreated(TableColumn column) {
						new ColumnTagProperties(column);
					}
				});

		tableManager.registerColumn(Tag.class, ColumnTagVisible.COLUMN_ID,
				new TableColumnCreationListener() {
					@Override
					public void tableColumnCreated(TableColumn column) {
						new ColumnTagVisible(column);
					}
				});

		tableManager.registerColumn(Tag.class, ColumnTagGroup.COLUMN_ID,
				new TableColumnCreationListener() {
					@Override
					public void tableColumnCreated(TableColumn column) {
						new ColumnTagGroup(column);
					}
				});

		tableManager.registerColumn(Tag.class, ColumnTagLimits.COLUMN_ID,
				new TableColumnCreationListener() {
					@Override
					public void tableColumnCreated(TableColumn column) {
						new ColumnTagLimits(column);
					}
				});

		tableManager.registerColumn(Tag.class, ColumnTagIcon.COLUMN_ID,
				new TableColumnCreationListener() {
					@Override
					public void tableColumnCreated(TableColumn column) {
						new ColumnTagIcon(column);
					}
				});
		
		tableManager.registerColumn(Tag.class, ColumnTagIconSortOrder.COLUMN_ID,
				new TableColumnCreationListener() {
					@Override
					public void tableColumnCreated(TableColumn column) {
						new ColumnTagIconSortOrder(column);
					}
				});

		tableManager.registerColumn(Tag.class, ColumnTagStatus.COLUMN_ID,
				new TableColumnCreationListener() {
					@Override
					public void tableColumnCreated(TableColumn column) {
						new ColumnTagStatus(column);
					}
				});

		tableManager.registerColumn(Tag.class, ColumnTagDependsOn.COLUMN_ID,
				new TableColumnCreationListener() {
					@Override
					public void tableColumnCreated(TableColumn column) {
						new ColumnTagDependsOn(column);
					}
				});

		
		tableManager.setDefaultColumnNames(TABLE_TAGS,
				new String[] {
					ColumnTagColor.COLUMN_ID,
					ColumnTagName.COLUMN_ID,
					ColumnTagCount.COLUMN_ID,
					ColumnTagType.COLUMN_ID,
					ColumnTagPublic.COLUMN_ID,
					ColumnTagUpRate.COLUMN_ID,
					ColumnTagDownRate.COLUMN_ID,
					ColumnTagUpLimit.COLUMN_ID,
					ColumnTagDownLimit.COLUMN_ID,
					ColumnTagStatus.COLUMN_ID,
				});

		tableManager.setDefaultSortColumnName(TABLE_TAGS, ColumnTagName.COLUMN_ID);
	}

	// @see SkinView#skinObjectHidden(SWTSkinObject, java.lang.Object)
	@Override
	public Object skinObjectHidden(SWTSkinObject skinObject, Object params) {

		if (tv != null) {

			tv.delete();

			tv = null;
		}

		Utils.disposeSWTObjects(new Object[] {
			table_parent,
		});

		TagManager tagManager = TagManagerFactory.getTagManager();
		if (tagManager != null) {
			List<TagType> tagTypes = tagManager.getTagTypes();
			for (TagType tagType : tagTypes) {
				tagType.removeTagTypeListener(this);
			}
			tagManager.removeTagManagerListener(this);

			tm_listener_added = false;
		}


		return super.skinObjectHidden(skinObject, params);
	}

	@Override
	public Object
	skinObjectShown(SWTSkinObject skinObject, Object params)
	{
		super.skinObjectShown(skinObject, params);

		SWTSkinObjectTextbox soFilter = (SWTSkinObjectTextbox)getSkinObject( "filterbox" );

		if ( soFilter != null ){

			txtFilter = soFilter.getTextControl();
		}

		SWTSkinObject so_list = getSkinObject("tags-list");

		if (so_list != null) {

			initTable((Composite) so_list.getControl());

		}else{

			return null;
		}

		if ( tv == null ){

			return null;
		}

		TagManager tagManager = TagManagerFactory.getTagManager();

		if (tagManager != null) {

			if ( !tm_listener_added ){

				tm_listener_added = true;

				tagManager.addTagManagerListener(this, true);
			}
		}

		return null;
	}

	// @see SkinView#skinObjectDestroyed(SWTSkinObject, java.lang.Object)
	@Override
	public Object
	skinObjectDestroyed(
		SWTSkinObject skinObject,
		Object params)
	{
		if ( tm_listener_added ){

			tm_listener_added = false;

			TagManager tagManager = TagManagerFactory.getTagManager();

			tagManager.removeTagManagerListener( this );

			for ( TagType tt: tagManager.getTagTypes()){

				tt.removeTagTypeListener( this );
			}
		}

		return super.skinObjectDestroyed(skinObject, params);
	}

	/**
	 * @param control
	 *
	 * @since 4.6.0.5
	 */
	private void initTable(Composite control) {

		UIFunctionsSWT uiFunctions = UIFunctionsManagerSWT.getUIFunctionsSWT();
		if (uiFunctions != null) {
			UISWTInstance pluginUI = uiFunctions.getUISWTInstance();

			registerPluginViews( pluginUI );
		}

		if ( tv == null ){

			tv = TableViewFactory.createTableViewSWT(Tag.class, TABLE_TAGS, TABLE_TAGS,
					new TableColumnCore[0], ColumnTagName.COLUMN_ID, SWT.MULTI
							| SWT.FULL_SELECTION | SWT.VIRTUAL);

			if ( txtFilter != null ){
				tv.enableFilterCheck(txtFilter, this);
			}
			tv.setRowDefaultHeightEM(1);
			tv.setEnableTabViews(true, true, null);

			table_parent = new Composite(control, SWT.BORDER);
			table_parent.setLayoutData(Utils.getFilledFormData());
			GridLayout layout = new GridLayout();
			layout.marginHeight = layout.marginWidth = layout.verticalSpacing = layout.horizontalSpacing = 0;
			table_parent.setLayout(layout);

			table_parent.addListener(SWT.Activate, new Listener() {
				@Override
				public void handleEvent(Event event) {
					//viewActive = true;
					updateSelectedContent();
				}
			});
			/*
			table_parent.addListener(SWT.Deactivate, new Listener() {
				public void handleEvent(Event event) {
					//viewActive = false;
					// don't updateSelectedContent() because we may have switched
					// to a button or a text field, and we still want out content to be
					// selected
				}
			})
			*/

			tv.addMenuFillListener( this );
			tv.addSelectionListener(this, false);

			tv.initialize(table_parent);

			tv.addCountChangeListener(new TableCountChangeListener() {

				@Override
				public void rowRemoved(TableRowCore row) {
				}

				@Override
				public void rowAdded(TableRowCore row) {
					if (datasource == row.getDataSource()) {
						tv.setSelectedRows(new TableRowCore[] { row });
					}
				}
			});
		}

		control.layout(true);
	}

	private void registerPluginViews(final UISWTInstance pluginUI) {
		if (registeredCoreSubViews) {
			return;
		}

		pluginUI.addView(TABLE_TAGS, "TagSettingsView", TagSettingsView.class,
				null);
		pluginUI.addView(TABLE_TAGS, "MyTorrentsSubView", MyTorrentsSubView.class,
				null);

		registeredCoreSubViews = true;

		final UIManager uiManager = PluginInitializer.getDefaultInterface().getUIManager();
		uiManager.addUIListener(new UIManagerListener() {

			@Override
			public void UIAttached(UIInstance instance) {

			}

			@Override
			public void UIDetached(UIInstance instance) {
				if (!(instance instanceof UISWTInstance)) {
					return;
				}

				registeredCoreSubViews = false;
				pluginUI.removeViews(TABLE_TAGS, "TagSettingsView");
				pluginUI.removeViews(TABLE_TAGS, "MyTorrentsSubView");
				uiManager.removeUIListener(this);
			}
		});

	}

	@Override
	public void
	fillMenu(
		String 	sColumnName,
		Menu 	menu )
	{
		List<Object>	ds = tv.getSelectedDataSources();

		List<Tag>	tags 			= new ArrayList<>();

		final List<TagFeatureRateLimit>	tags_su 	= new ArrayList<>();
		final List<TagFeatureRateLimit>	tags_sd 	= new ArrayList<>();

		for ( Object obj: ds ){

			if ( obj instanceof Tag ){

				Tag tag = (Tag)obj;

				tags.add( tag );

				if ( tag instanceof TagFeatureRateLimit ){

					TagFeatureRateLimit rl = (TagFeatureRateLimit)tag;

					if (rl.supportsTagRates()){

						long[] up = rl.getTagSessionUploadTotal();

						if ( up != null ){

							tags_su.add( rl );
						}

						long[] down = rl.getTagSessionDownloadTotal();

						if ( down != null ){

							tags_sd.add( rl );
						}
					}
				}
			}
		}

		if ( sColumnName != null ){
			
			if ( 	( sColumnName.equals( ColumnTagUpSession.COLUMN_ID ) && tags_su.size() > 0 ) ||
					( sColumnName.equals( ColumnTagDownSession.COLUMN_ID ) && tags_sd.size() > 0 )){
	
				final boolean is_up = sColumnName.equals( ColumnTagUpSession.COLUMN_ID );
	
				MenuItem mi = new MenuItem(menu, SWT.PUSH);
	
				Messages.setLanguageText(mi, "menu.reset.session.stats");
	
				mi.addListener(SWT.Selection, new Listener() {
					@Override
					public void handleEvent(Event event) {
						for ( TagFeatureRateLimit rl: is_up?tags_su:tags_sd ){
	
							if ( is_up ){
								rl.resetTagSessionUploadTotal();
							}else{
								rl.resetTagSessionDownloadTotal();
							}
						}
					}
				});
	
				MenuFactory.addSeparatorMenuItem(menu);
			}
	
			if ( 	( sColumnName.equals( ColumnTagIcon.COLUMN_ID )) ||
					( sColumnName.equals( ColumnTagIconSortOrder.COLUMN_ID ))){
	
				MenuItem mi = new MenuItem(menu, SWT.PUSH);
	
				Messages.setLanguageText(mi, "menu.set.icon.sort");
	
				mi.addListener(SWT.Selection,
					(e)->{
						SimpleTextEntryWindow entryWindow = new SimpleTextEntryWindow(
								"tag.sort.order.title",
								"tag.sort.order.text");
						
						entryWindow.allowEmptyInput( true );
						
						entryWindow.setWidthHint( 450 );
						
						entryWindow.prompt(new UIInputReceiverListener() {
							@Override
							public void 
							UIInputReceiverClosed(UIInputReceiver entryWindow) 
							{
								if (!entryWindow.hasSubmittedInput()){
									
									return;
								}
								
								String sReturn = entryWindow.getSubmittedInput().trim();
								
								if ( sReturn.isEmpty()){
									
									for ( Tag t: tags ){
										
										t.setImageSortOrder( -1 );
									}
								}else{
									
									int start = 0;
									
									try{
										start = Integer.valueOf(sReturn).intValue();
										
									} catch (NumberFormatException er) {
										// Ignore
									}
									
									for ( Tag t: tags ){
										
										t.setImageSortOrder( start++ );
									}
								}
							}});
					});	
	
				MenuFactory.addSeparatorMenuItem(menu);
			}
		}
		
		if ( tags.size() == 1 ){
			TagUIUtils.createSideBarMenuItems( menu, tags.get(0) );
		}else{
			TagUIUtils.createSideBarMenuItems( menu, tags );
		}
	}

	@Override
	public void
	addThisColumnSubMenu(
		String 	sColumnName,
		Menu	menuThisColumn )
	{
	}

	@Override
	public void
	selected(
		TableRowCore[] row )
	{
		updateSelectedContent();
  	UIFunctions uiFunctions = UIFunctionsManager.getUIFunctions();
  	if (uiFunctions != null) {
  		uiFunctions.refreshIconBar();
  	}
	}

	@Override
	public void
	deselected(
		TableRowCore[] rows )
	{
		updateSelectedContent();
  	UIFunctions uiFunctions = UIFunctionsManager.getUIFunctions();
  	if (uiFunctions != null) {
  		uiFunctions.refreshIconBar();
  	}
	}

	@Override
	public void
	focusChanged(
		TableRowCore focus )
	{
		updateSelectedContent();
  	UIFunctions uiFunctions = UIFunctionsManager.getUIFunctions();
  	if (uiFunctions != null) {
  		uiFunctions.refreshIconBar();
  	}
	}

	@Override
	public void
	defaultSelected(
		TableRowCore[] 	rows,
		int 			stateMask )
	{
		if ( rows.length == 1 ){

			Object obj = rows[0].getDataSource();

			if ( obj instanceof Tag ){

				Tag tag = (Tag)obj;

				UIFunctions uiFunctions = UIFunctionsManager.getUIFunctions();

				if ( uiFunctions != null ){

					if ( !COConfigurationManager.getBooleanParameter("Library.TagInSideBar")){

						COConfigurationManager.setParameter("Library.TagInSideBar", true );
					}

					if ( !tag.isVisible()){

						tag.setVisible( true );
					}

					String id = "Tag." + tag.getTagType().getTagType() + "." + tag.getTagID();
					uiFunctions.getMDI().showEntryByID(id, tag);
				}
			}
		}
	}

	public void updateSelectedContent() {
		updateSelectedContent( false );
	}

	public void updateSelectedContent( boolean force ) {
		if (table_parent == null || table_parent.isDisposed()) {
			return;
		}
			// if we're not active then ignore this update as we don't want invisible components
			// updating the toolbar with their invisible selection. Note that unfortunately the
			// call we get here when activating a view does't yet have focus

		if ( !isVisible()){
			if ( !force ){
				return;
			}
		}
		SelectedContentManager.clearCurrentlySelectedContent();
		SelectedContentManager.changeCurrentlySelectedContent(tv.getTableID(), null, tv);
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
	public boolean filterCheck(Tag ds, String filter, boolean regex) {
		String name = ds.getTagName( true );

		String s = regex ? filter : "\\Q" + filter.replaceAll("\\s*[|;]\\s*", "\\\\E|\\\\Q") + "\\E";

		boolean	match_result = true;

		if ( regex && s.startsWith( "!" )){

			s = s.substring(1);

			match_result = false;
		}

		Pattern pattern = RegExUtil.getCachedPattern( "tagsoverview:search", s, Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE );

		return( pattern.matcher(name).find() == match_result );
	}

	@Override
	public void tagTypeAdded(TagManager manager, TagType tag_type) {
		if ( Constants.IS_CVS_VERSION || tag_type.getTagType() != TagType.TT_DOWNLOAD_INTERNAL ){
			
			tag_type.addTagTypeListener(this, true);
		}
	}

	@Override
	public void tagTypeRemoved(TagManager manager, TagType tag_type) {
		tag_type.removeTagTypeListener(this);
	}

	@Override
	public void tagTypeChanged(TagType tag_type) {
		tv.tableInvalidate();
	}

	@Override
	public void tagEventOccurred(TagEvent event ) {
		int	type = event.getEventType();
		Tag	tag = event.getTag();
		if ( type == TagEvent.ET_TAG_ADDED ){
			tagAdded( tag );
		}else if ( type == TagEvent.ET_TAG_MEMBERSHIP_CHANGED || type == TagEvent.ET_TAG_METADATA_CHANGED ){
			tagChanged( tag );
		}else if ( type == TagEvent.ET_TAG_REMOVED ){
			tagRemoved( tag );
		}
	}

	public void tagAdded(Tag tag) {
		tv.addDataSource(tag);

		handleProps( tag );
	}

	public void tagChanged(Tag tag) {
		if (tv == null || tv.isDisposed()) {
			return;
		}
		TableRowCore row = tv.getRow(tag);
		if (row != null) {
			row.invalidate(true);
		}

		handleProps( tag );
	}

	private void
	handleProps(
		Tag		tag )
	{
		Boolean b = (Boolean)tag.getTransientProperty( Tag.TP_SETTINGS_REQUESTED );

		if ( b != null && b ){

			tag.setTransientProperty( Tag.TP_SETTINGS_REQUESTED, null );

			tv.processDataSourceQueueSync();

			TableRowCore row = tv.getRow(tag);

			if ( row == null ){

				Debug.out( "Can't select settings view for " + tag.getTagName( true ) + " as row not found" );

			}else{

				tv.setSelectedRows(new TableRowCore[] { row });
			}
		}
	}

	// @see com.biglybt.core.tag.TagTypeListener#tagRemoved(com.biglybt.core.tag.Tag)
	public void tagRemoved(Tag tag) {
		tv.removeDataSource(tag);
	}

	// @see SWTSkinObjectAdapter#dataSourceChanged(SWTSkinObject, java.lang.Object)
	@Override
	public Object dataSourceChanged(SWTSkinObject skinObject, Object params) {
		if (params instanceof Tag) {
			if (tv != null) {
				TableRowCore row = tv.getRow((Tag) params);
				if (row != null) {
					tv.setSelectedRows(new TableRowCore[] { row });
				}
			}
		}
		datasource = params;
		return null;
	}

	// @see SWTSkinObjectAdapter#skinObjectSelected(SWTSkinObject, java.lang.Object)
	@Override
	public Object skinObjectSelected(SWTSkinObject skinObject, Object params) {
		updateSelectedContent();
		return super.skinObjectSelected(skinObject, params);
	}
}
