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

import org.eclipse.swt.SWT;
import org.eclipse.swt.dnd.*;
import org.eclipse.swt.events.KeyEvent;
import org.eclipse.swt.events.KeyListener;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.*;

import com.biglybt.core.CoreFactory;
import com.biglybt.core.category.Category;
import com.biglybt.core.config.COConfigurationManager;
import com.biglybt.core.config.ConfigKeys;
import com.biglybt.core.config.ParameterListener;
import com.biglybt.core.download.DownloadManager;
import com.biglybt.core.global.GlobalManager;
import com.biglybt.core.global.GlobalManagerEvent;
import com.biglybt.core.global.GlobalManagerEventListener;
import com.biglybt.core.internat.MessageText;
import com.biglybt.core.tag.*;
import com.biglybt.core.util.*;
import com.biglybt.ui.UIFunctions;
import com.biglybt.ui.UIFunctionsManager;
import com.biglybt.ui.common.ToolBarItem;
import com.biglybt.ui.common.table.*;
import com.biglybt.ui.common.table.impl.TableColumnManager;
import com.biglybt.ui.common.table.impl.TableViewImpl;
import com.biglybt.ui.common.updater.UIUpdatable;
import com.biglybt.ui.config.ConfigSectionInterfaceTags;
import com.biglybt.ui.mdi.MultipleDocumentInterface;
import com.biglybt.ui.selectedcontent.SelectedContentManager;
import com.biglybt.ui.swt.Messages;
import com.biglybt.ui.swt.SimpleTextEntryWindow;
import com.biglybt.ui.swt.Utils;
import com.biglybt.ui.swt.columns.tag.*;
import com.biglybt.ui.swt.components.BubbleTextBox;
import com.biglybt.ui.swt.mainwindow.MenuFactory;
import com.biglybt.ui.swt.mdi.MdiEntrySWT;
import com.biglybt.ui.swt.mdi.MultipleDocumentInterfaceSWT;
import com.biglybt.ui.swt.pif.UISWTView;
import com.biglybt.ui.swt.pifimpl.UISWTViewBuilderCore;
import com.biglybt.ui.swt.pifimpl.UISWTViewCore;
import com.biglybt.ui.swt.skin.*;
import com.biglybt.ui.swt.skin.SWTSkinButtonUtility.ButtonListenerAdapter;
import com.biglybt.ui.swt.utils.DragDropUtils;
import com.biglybt.ui.swt.utils.TagUIUtilsV3;
import com.biglybt.ui.swt.views.*;
import com.biglybt.ui.swt.views.table.TableViewSWT;
import com.biglybt.ui.swt.views.table.TableViewSWTMenuFillListener;
import com.biglybt.ui.swt.views.table.impl.TableViewFactory;
import com.biglybt.ui.swt.views.table.impl.TableViewSWT_TabsCommon;
import com.biglybt.ui.swt.views.utils.TagUIUtils;
import com.biglybt.pif.ui.UIInputReceiver;
import com.biglybt.pif.ui.UIInputReceiverListener;
import com.biglybt.pif.ui.UIPluginViewToolBarListener;
import com.biglybt.pif.ui.tables.TableColumn;
import com.biglybt.pif.ui.tables.TableColumnCreationListener;
import com.biglybt.pif.ui.toolbar.UIToolBarItem;

/**
 * @author TuxPaper
 * @created May 10, 2013
 *
 */
public class SBC_TagsOverview
	extends SkinView
	implements UIUpdatable, UIPluginViewToolBarListener, TableViewFilterCheck<Tag>, TagManagerListener, TagTypeListener,
	TableViewSWTMenuFillListener, TableSelectionListener, KeyListener, ParameterListener, GlobalManagerEventListener {

	private static final String TABLE_TAGS = "TagsView";
	// TODO: This should be com.biglybt.pif.tag.Tag but we'd have to
	// code some PluginCoreUtils.convert additions and other stuff
	public static final Class<Tag> PLUGIN_DS_TYPE = Tag.class;

	TableViewSWT<Tag> tv;

	private Composite table_parent;

	private boolean columnsAdded = false;

	private boolean tm_listener_added;
	private boolean gm_listener_added;
	
	private Object datasource;

	private boolean show_swarm_tags;
	
	private GlobalManager global_manager;
	
	{
		try{
			global_manager = CoreFactory.getSingleton().getGlobalManager();
			
		}catch( Throwable e ){
			
			Debug.out( e );
		}
	}
	
	public
	SBC_TagsOverview()
	{
		COConfigurationManager.addAndFireParameterListener( ConfigKeys.Tag.BCFG_TAG_SHOW_SWARM_TAGS_IN_OVERVIEW, this );
	}
	
	@Override
	public void 
	parameterChanged(
		String parameterName)
	{
		show_swarm_tags = COConfigurationManager.getBooleanParameter(parameterName);
		
		if ( tv != null ){
			
			tv.refilter();
		}
	}
	
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

		if ("remove".equals(item.getID())) {
			Object[] datasources = tv.getSelectedDataSources().toArray();
			List<Tag> tags = new ArrayList<>();
			for (Object tag : datasources) {
				if (tag instanceof Tag) {
					tags.add((Tag) tag);
				}
			}
			if (tags.size() > 0) {
				return TagUIUtils.removeTags(tags);
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
					if (TagUIUtils.canDeleteTag(tag)) {
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

		SWTSkinObjectContainer	soTopArea 	= (SWTSkinObjectContainer)getSkinObject("tag-top-area");
		SWTSkinObjectText	 	soTitle 	= (SWTSkinObjectText)getSkinObject("title");
		
		for (Control comp: new Control[]{ soTopArea.getComposite(), soTitle.getControl()}){
			
			Menu menu = new Menu(comp);
			comp.setMenu( menu );
			
			MenuItem mi = new MenuItem( menu, SWT.PUSH );
	
			mi.setText( MessageText.getString( "menu.tag.options"));
	
			mi.addListener( SWT.Selection, (ev)->{
				UIFunctions uif = UIFunctionsManager.getUIFunctions();
	
				if ( uif != null ){
	
					uif.getMDI().showEntryByID(
							MultipleDocumentInterface.SIDEBAR_SECTION_CONFIG,
							ConfigSectionInterfaceTags.SECTION_ID );
				}
			});
		}
		
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
		
		tableManager.registerColumn(Tag.class, ColumnTagMoveOnAssign.COLUMN_ID,
				new TableColumnCreationListener() {
					@Override
					public void tableColumnCreated(TableColumn column) {
						new ColumnTagMoveOnAssign(column);
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

		tableManager.registerColumn(Tag.class, ColumnTagFilter.COLUMN_ID,
				new TableColumnCreationListener() {
					@Override
					public void tableColumnCreated(TableColumn column) {
						new ColumnTagFilter(column);
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

		tableManager.registerColumn(Tag.class, ColumnTagEOA.COLUMN_ID,
				new TableColumnCreationListener() {
					@Override
					public void tableColumnCreated(TableColumn column) {
						new ColumnTagEOA(column);
					}
				});
		
		tableManager.registerColumn(Tag.class, ColumnTagSortAutoApply.COLUMN_ID,
				new TableColumnCreationListener() {
					@Override
					public void tableColumnCreated(TableColumn column) {
						new ColumnTagSortAutoApply(column);
					}
				});
		
		tableManager.registerColumn(Tag.class, ColumnTagHideWhenEmpty.COLUMN_ID,
				new TableColumnCreationListener() {
					@Override
					public void tableColumnCreated(TableColumn column) {
						new ColumnTagHideWhenEmpty(column);
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

		if ( global_manager != null ){
			
			global_manager.removeEventListener( this );
		
			gm_listener_added = false;
		}
		
		return super.skinObjectHidden(skinObject, params);
	}

	@Override
	public Object
	skinObjectShown(SWTSkinObject skinObject, Object params)
	{
		super.skinObjectShown(skinObject, params);

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

		if ( global_manager != null && !gm_listener_added ){
			
			global_manager.addEventListener( this );
		
			gm_listener_added = true;
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

		COConfigurationManager.removeParameterListener( ConfigKeys.Tag.BCFG_TAG_SHOW_SWARM_TAGS_IN_OVERVIEW, this );
		
		return super.skinObjectDestroyed(skinObject, params);
	}

	/**
	 * @param control
	 *
	 * @since 4.6.0.5
	 */
	private void initTable(Composite control) {

		registerPluginViews();

		if ( tv == null ){

			tv = TableViewFactory.createTableViewSWT(PLUGIN_DS_TYPE, TABLE_TAGS,
					TABLE_TAGS, new TableColumnCore[0], ColumnTagName.COLUMN_ID,
					SWT.MULTI | SWT.FULL_SELECTION | SWT.VIRTUAL);

			SWTSkinObjectTextbox soFilter = (SWTSkinObjectTextbox) getSkinObject(
					"filterbox");
			if (soFilter != null) {
				BubbleTextBox filter = soFilter.getBubbleTextBox();
				tv.enableFilterCheck(filter, this);
				
				String tooltip = MessageText.getString("filter.tt.start");
				tooltip += MessageText.getString("tagsoverview.filter.tt.line1");
				tooltip += MessageText.getString("tagsoverview.filter.tt.line2");
				
				filter.setTooltip( tooltip );
				
				filter.setMessage( MessageText.getString( "Button.search2" ) );
			}

			tv.setRowDefaultHeightEM(1);

			table_parent = Utils.createSkinnedComposite(control, SWT.BORDER,Utils.getFilledFormData());
		
			GridLayout layout = new GridLayout();
			layout.marginHeight = layout.marginWidth = layout.verticalSpacing = layout.horizontalSpacing = 0;
			table_parent.setLayout(layout);

			/*
			 * 05/2024. Removed this because re-activation is causing selected content to be lost. In this case select a tag, select the Torrents sub-view
			 * and select a torrent. Hit F2 for example (to get a change-name dialog) and then hit escape to close the dialog. With this code enabled it causes
			 * selection to return to the Tag above rather than the torrent
			 * 
			table_parent.addListener(SWT.Activate, new Listener() {
				@Override
				public void handleEvent(Event event) {
					//viewActive = true;
					updateSelectedContent();
				}
			});
			*/
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
			tv.addKeyListener(this);

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
			
			DragSource dragSource = tv.createDragSource(DND.DROP_COPY | DND.DROP_MOVE);
			if (dragSource != null) {
				dragSource.setTransfer(TextTransfer.getInstance());
				dragSource.addDragListener(new DragSourceListener() {
					@Override
					public void dragStart(DragSourceEvent event) {
						List<Object> dataSources = tv.getSelectedDataSources();
						for (Object dataSource : dataSources) {
							if ((dataSource instanceof Tag)
									&& (((Tag) dataSource).getTagType().getTagType() == TagType.TT_DOWNLOAD_MANUAL)) {
								return;
							}
						}
						event.doit = false;
						event.detail = DND.DROP_NONE;
					}

					@Override
					public void dragSetData(DragSourceEvent event) {
						StringBuilder sb = new StringBuilder(DragDropUtils.DROPDATA_PREFIX_TAG_UID);
						boolean hasTags = false;
						List<Object> dataSources = tv.getSelectedDataSources();
						for (Object dataSource : dataSources) {
							if ((dataSource instanceof Tag)
									&& (((Tag) dataSource).getTagType().getTagType() == TagType.TT_DOWNLOAD_MANUAL)) {
								hasTags = true;
								sb.append('\n');
								sb.append(((Tag) dataSource).getTagUID());
							}
						}
						
						if (hasTags) {
							event.data = sb.toString();
						}
					}

					@Override
					public void dragFinished(DragSourceEvent event) {

					}
				});
			}

			DropTarget dropTarget = tv.createDropTarget(DND.DROP_DEFAULT | DND.DROP_MOVE
				| DND.DROP_COPY);
			if (dropTarget != null) {
				dropTarget.setTransfer(TextTransfer.getInstance());
				dropTarget.addDropListener(new DropTargetAdapter() {
					@Override
					public void dragOver(DropTargetEvent event) {
						List<DownloadManager> dms = DragDropUtils.getDownloadsFromDropData(
							event.data == null ? DragDropUtils.getLastDraggedObject()
								: event.data,
							true);
						if (dms.isEmpty()) {
							event.detail = DND.DROP_NONE;
							return;
						}

						TableRowCore row = tv.getRow(event);
						if (row == null) {
							event.detail = DND.DROP_NONE;
							return;
						}
						Object dataSource = row.getDataSource();
						if (!(dataSource instanceof Tag)) {
							event.detail = DND.DROP_NONE;
							return;
						}
						Tag tag = (Tag) dataSource;

						boolean doAdd = false;
						for (DownloadManager dm : dms) {
							if (!tag.hasTaggable(dm)) {
								doAdd = true;
								break;
							}
						}

						boolean[] auto = tag.isTagAuto();
						if (auto.length < 2 || (doAdd && auto[0])
							|| (!doAdd && auto[0] && auto[1])) {
							event.detail = DND.DROP_NONE;
							return;
						}
						
						event.detail = doAdd ? DND.DROP_COPY : DND.DROP_MOVE;
					}

					@Override
					public void drop(DropTargetEvent event) {
						List<DownloadManager> dms = DragDropUtils.getDownloadsFromDropData(
								event.data == null ? DragDropUtils.getLastDraggedObject()
										: event.data,
								true);
						if (dms.isEmpty()) {
							return;
						}

						TableRowCore row = tv.getRow(event);
						if (row == null) {
							return;
						}
						Object dataSource = row.getDataSource();
						if (!(dataSource instanceof Tag)) {
							return;
						}
						Tag tag = (Tag) dataSource;

						boolean doAdd = false;
						for (DownloadManager dm : dms) {
							if (!tag.hasTaggable(dm)) {
								doAdd = true;
								break;
							}
						}

						boolean[] auto = tag.isTagAuto();
						if (auto.length < 2 || (doAdd && auto[0])
								|| (!doAdd && auto[0] && auto[1])) {
							return;
						}

						try{
							tag.addTaggableBatch( true );
						
							for (DownloadManager dm : dms) {
								if ( doAdd ){
									tag.addTaggable( dm );
								}else{
									tag.removeTaggable( dm );
								}
							}
						}finally{
							
							tag.addTaggableBatch( false );
						}
					}
				});
			}
		}

		control.layout(true);
	}

	private static void registerPluginViews() {
		ViewManagerSWT vm = ViewManagerSWT.getInstance();
		if (vm.areCoreViewsRegistered(PLUGIN_DS_TYPE)) {
			return;
		}

		vm.registerView(PLUGIN_DS_TYPE, new UISWTViewBuilderCore(
			TagSettingsView.VIEW_ID, null, TagSettingsView.class));

		vm.registerView(PLUGIN_DS_TYPE, new UISWTViewBuilderCore(
			MyTorrentsSubView.MSGID_PREFIX, null, MyTorrentsSubView.class));

		vm.registerView(PLUGIN_DS_TYPE, new UISWTViewBuilderCore(
			FilesView.MSGID_PREFIX, null, FilesView.class));

		vm.setCoreViewsRegistered(PLUGIN_DS_TYPE);
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

	public void
	eventOccurred(
		GlobalManagerEvent		event )
	{
		if ( tv != null && event.getEventType() == GlobalManagerEvent.ET_REQUEST_ATTENTION ){

			DownloadManager dm = event.getDownload();
			
			List<Object>	ds = tv.getSelectedDataSources();

			boolean hit = false;
			
			for ( Object obj: ds ){

				if ( obj instanceof Tag ){

					Tag tag = (Tag)obj;
					
					if ( tag.getTagged().contains( dm )){
						
						hit = true;
						
						break;
					}
				}
			}

			if ( hit ){
					
				MultipleDocumentInterfaceSWT tabbed_mdi = tv.getTabsCommon().getMDI();
	
				tabbed_mdi.showEntryByID( MyTorrentsSubView.MSGID_PREFIX );
				
				MdiEntrySWT view = tabbed_mdi.getEntry( MyTorrentsSubView.MSGID_PREFIX );
				
				if ( view != null ){
				
					((MyTorrentsView)view.getEventListener()).requestAttention( dm );
				}
			}
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

				int tt = tag.getTagType().getTagType();
				
				if ( tt == TagType.TT_DOWNLOAD_INTERNAL || tt == TagType.TT_SWARM_TAG ){
					
					return;
				}
				
				UIFunctions uiFunctions = UIFunctionsManager.getUIFunctions();

				if ( uiFunctions != null ){

					if ( tag instanceof Category ){
						
						if ( !COConfigurationManager.getBooleanParameter("Library.CatInSideBar")){
							
							COConfigurationManager.setParameter("Library.CatInSideBar", true );
						}
					}else{
						
						if ( !COConfigurationManager.getBooleanParameter("Library.TagInSideBar")){
	
							COConfigurationManager.setParameter("Library.TagInSideBar", true );
						}
					}
					
					if ( !tag.isVisible()){

						tag.setVisible( true );
					}

					if ( tag instanceof Category ){
						
						String name = ((Category)tag).getName();
						
						String id = "Cat." + Base32.encode(name.getBytes());
						
						uiFunctions.getMDI().showEntryByID(id, tag);
						
					}else{
						
						String id = "Tag." + tag.getTagType().getTagType() + "." + tag.getTagID();
						
						uiFunctions.getMDI().showEntryByID(id, tag);
					}
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
			// call we get here when activating a view doesn't yet have focus

		if ( !isVisible()){
			if ( !force ){
				return;
			}
		}
		
		SelectedContentManager.clearCurrentlySelectedContent();

		if ( tv != null ){
			SelectedContentManager.changeCurrentlySelectedContent(tv.getTableID(), null, tv);
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
	public boolean 
	filterCheck(
		Tag 		tag, 
		String 		filter, 
		boolean 	regex,
		boolean		confusable )
	{
		if ( confusable ){
			
			return( false );
		}
		
		int tt = tag.getTagType().getTagType();
		
		if ( tt == TagType.TT_SWARM_TAG && !show_swarm_tags){
			
			return( false );
		}
		
		String name;
		
		if ( filter.startsWith( "g:" )){
			
			filter = filter.substring( 2 );
			
			name = tag.getGroup();
			
			if ( name == null ){
				
				name = "";
			}
		}else if ( filter.startsWith( "p:" )){
			
			filter = filter.substring( 2 );
			
			if ( tag instanceof TagFeatureProperties ){
				
				name = ((TagFeatureProperties)tag).getPropertiesString();
				
			}else{
				
				name = "";
			}
		}else{
		
			name = tag.getTagName( true );
		}
		
		String s = regex ? filter : RegExUtil.splitAndQuote( filter, "\\s*[|;]\\s*" );

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
				
				AEThread2.createAndStartDaemon(
					"feh",
					()->{
						long start = SystemTime.getMonotonousTime();
						
						while( tv.hasChangesPending()){
							
							if ( SystemTime.getMonotonousTime() - start > 20*1000 ){
								
								break;
							}
							
							try{
								Thread.sleep(100);
							}catch( Throwable e ){
								
							}
						}
							
						Utils.execSWTThread(()->{
							tv.getTabsCommon().getMDI().showEntryByID( TagSettingsView.VIEW_ID );
							tv.showRow( row ); 
						});
					});
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

	@Override
	public void keyPressed(KeyEvent e) {
		if (e.keyCode == SWT.F2 && (e.stateMask & SWT.MODIFIER_MASK) == 0) {
			Object[] selectedDataSources = tv.getSelectedDataSources(true);
			if (selectedDataSources.length == 1 && (selectedDataSources[0] instanceof Tag)) {
				Tag tag = (Tag) selectedDataSources[0];
				if (!tag.getTagType().isTagTypeAuto()) {
					TagUIUtils.openRenameTagDialog(tag);
					e.doit = false;
				}
			}
		}
	}

	@Override
	public void keyReleased(KeyEvent e) {

	}
}
