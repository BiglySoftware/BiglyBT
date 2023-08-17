/*
 * File    : MySharesView.java
 * Created : 18-Jan-2004
 * By      : parg
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
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details ( see the LICENSE file ).
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 */


package com.biglybt.ui.swt.views;

import java.io.File;
import java.util.List;
import java.util.*;

import org.eclipse.swt.SWT;
import org.eclipse.swt.dnd.*;
import org.eclipse.swt.widgets.*;

import com.biglybt.core.Core;
import com.biglybt.core.CoreFactory;
import com.biglybt.core.CoreRunningListener;
import com.biglybt.core.category.Category;
import com.biglybt.core.category.CategoryManager;
import com.biglybt.core.download.DownloadManager;
import com.biglybt.core.internat.MessageText;
import com.biglybt.core.logging.LogAlert;
import com.biglybt.core.logging.Logger;
import com.biglybt.core.tag.*;
import com.biglybt.core.util.Debug;
import com.biglybt.core.util.TorrentUtils;
import com.biglybt.pifimpl.local.PluginInitializer;
import com.biglybt.pifimpl.local.torrent.TorrentManagerImpl;
import com.biglybt.ui.UIFunctions;
import com.biglybt.ui.UIFunctions.TagReturner;
import com.biglybt.ui.UIFunctionsManager;
import com.biglybt.ui.common.ToolBarItem;
import com.biglybt.ui.common.table.*;
import com.biglybt.ui.common.table.impl.TableColumnManager;
import com.biglybt.ui.common.viewtitleinfo.ViewTitleInfo2;
import com.biglybt.ui.mdi.MdiEntry;
import com.biglybt.ui.mdi.MdiEntryDropListener;
import com.biglybt.ui.mdi.MultipleDocumentInterface;
import com.biglybt.ui.swt.*;
import com.biglybt.ui.swt.mainwindow.TorrentOpener;
import com.biglybt.ui.swt.pifimpl.UISWTViewCoreEventListener;
import com.biglybt.ui.swt.sharing.ShareUtils;
import com.biglybt.ui.swt.utils.TagUIUtilsV3;
import com.biglybt.ui.swt.views.table.TableViewSWT;
import com.biglybt.ui.swt.views.table.TableViewSWTMenuFillListener;
import com.biglybt.ui.swt.views.table.impl.TableViewFactory;
import com.biglybt.ui.swt.views.table.impl.TableViewTab;
import com.biglybt.ui.swt.views.tableitems.myshares.*;
import com.biglybt.ui.swt.views.utils.CategoryUIUtils;
import com.biglybt.ui.swt.views.utils.TagUIUtils;

import com.biglybt.pif.PluginInterface;
import com.biglybt.pif.download.Download;
import com.biglybt.pif.sharing.*;
import com.biglybt.pif.torrent.Torrent;
import com.biglybt.pif.torrent.TorrentAttribute;
import com.biglybt.pif.tracker.Tracker;
import com.biglybt.pif.tracker.TrackerTorrent;
import com.biglybt.pif.ui.UIPluginViewToolBarListener;
import com.biglybt.pif.ui.tables.TableManager;
import com.biglybt.pif.ui.toolbar.UIToolBarItem;

/**
 * @author parg
 * @author TuxPaper
 *         2004/Apr/20: Remove need for tableItemToObject
 *         2004/Apr/21: extends TableView instead of IAbstractView
 */
public class MySharesView
extends TableViewTab<ShareResource>
implements ShareManagerListener,
		TableLifeCycleListener, TableViewSWTMenuFillListener,
        TableRefreshListener, TableSelectionListener, ViewTitleInfo2,
		UIPluginViewToolBarListener, UISWTViewCoreEventListener
{
	private static final TableColumnCore[] basicItems = {
			new NameItem(),
			new TypeItem(),
			new CategoryItem(),
			new TagsItem(),
			new PersistentItem()
	};

	static{
		TableColumnManager tcManager = TableColumnManager.getInstance();

		tcManager.setDefaultColumnNames( TableManager.TABLE_MYSHARES, basicItems );
	}
	
	protected static final TorrentAttribute	category_attribute =
		TorrentManagerImpl.getSingleton().getAttribute( TorrentAttribute.TA_CATEGORY );

	//private Menu			menuCategory;

	private TableViewSWT<ShareResource> tv;

	public
	MySharesView()
	{
		super("MySharesView");
		tv = TableViewFactory.createTableViewSWT(ShareResource.class, TableManager.TABLE_MYSHARES,
				getTextPrefixID(), basicItems, "name", SWT.MULTI | SWT.FULL_SELECTION
						| SWT.BORDER | SWT.VIRTUAL);

		tv.addSelectionListener(new TableSelectionAdapter() {
			@Override
			public void defaultSelected(TableRowCore[] rows, int stateMask) {
				MySharesView.this.defaultSelected(rows);
			}

		}, false);

		tv.addLifeCycleListener(this);
		tv.addMenuFillListener(this);
		tv.addRefreshListener(this, false);
		tv.addSelectionListener(this, false);
	}

	@Override
	public TableViewSWT initYourTableView() {
  	return tv;
  }

	private void defaultSelected(TableRowCore[] rows) {
		ShareResource share = (ShareResource) tv.getFirstSelectedDataSource();
		if (share == null) {
			return;
		}

		// if a row was selected that means it was added, which
		// required a core, so we assume there's a core here

		List dms = CoreFactory.getSingleton().getGlobalManager().getDownloadManagers();

		for (int i = 0; i < dms.size(); i++) {
			DownloadManager dm = (DownloadManager) dms.get(i);

			try {
				byte[] share_hash = null;

				if (share.getType() == ShareResource.ST_DIR) {

					share_hash = ((ShareResourceDir) share).getItem().getTorrent().getHash();

				} else if (share.getType() == ShareResource.ST_FILE) {

					share_hash = ((ShareResourceFile) share).getItem().getTorrent().getHash();
				}

				if (Arrays.equals(share_hash, dm.getTorrent().getHash())) {

					UIFunctions uiFunctions = UIFunctionsManager.getUIFunctions();
					if (uiFunctions != null) {
						uiFunctions.getMDI().showEntryByID(
								MultipleDocumentInterface.SIDEBAR_SECTION_TORRENT_DETAILS, dm);
					}

					break;
				}
			} catch (Throwable e) {
				Debug.printStackTrace(e);
			}
		}
	}

	@Override
	public void tableLifeCycleEventOccurred(TableView tv, int eventType, Map<String, Object> data) {
		switch (eventType) {
			case EVENT_TABLELIFECYCLE_INITIALIZED:
				tableViewInitialized();
				break;
			case EVENT_TABLELIFECYCLE_DESTROYED:
				tableViewDestroyed();
				break;
		}
	}


	private void tableViewInitialized() {
		CoreFactory.addCoreRunningListener(new CoreRunningListener() {
			@Override
			public void coreRunning(Core core) {
				createRows(core);
			}
		});

		DropTarget dropTarget = tv.createDropTarget(DND.DROP_DEFAULT | DND.DROP_MOVE
			| DND.DROP_COPY | DND.DROP_LINK | DND.DROP_TARGET_MOVE);
		if (dropTarget != null) {
			dropTarget.setTransfer(FixedHTMLTransfer.getInstance(),
					FixedURLTransfer.getInstance(), FileTransfer.getInstance(),
					TextTransfer.getInstance());

			dropTarget.addDropListener(new DropTargetAdapter() {
				@Override
				public void drop(DropTargetEvent event) {
					if (!share(event.data)) {
						TorrentOpener.openDroppedTorrents(event, true);
					}
				}
			});
		}
	}

	protected boolean share(Object eventData) {
		boolean shared = false;
		if (eventData instanceof String[] || eventData instanceof String) {
			final String[] sourceNames = (eventData instanceof String[])
					? (String[]) eventData : new String[] {
						(String) eventData
					};
			if (sourceNames == null) {
				return false;
			}
			for (int i = 0; (i < sourceNames.length); i++) {
				final File source = new File(sourceNames[i]);
				String filename = source.getAbsolutePath();
				try {
					if (source.isFile() && !TorrentUtils.isTorrentFile(filename)) {
						ShareUtils.shareFile(filename);
						shared = true;
					} else if (source.isDirectory()) {
						ShareUtils.shareDir(filename);
						shared = true;
					}
				} catch (Exception e) {
				}
			}
		}
		return shared;
	}

	private void tableViewDestroyed() {
		try {
			PluginInitializer.getDefaultInterface().getShareManager().removeListener(
					this);
		} catch (ShareException e) {
			Debug.printStackTrace(e);
		} catch (Throwable ignore) {
		}
	}

  private void createRows(Core core) {
		try{

			ShareManager	sm = core.getPluginManager().getDefaultPluginInterface().getShareManager();

			ShareResource[]	shares = sm.getShares();

			for (int i=0;i<shares.length;i++){

				resourceAdded(shares[i]);
			}

			sm.addListener(this);

		}catch( ShareException e ){

			Debug.printStackTrace( e );
		}
	}

  @Override
  public void
  fillMenu(
  	String sColumnName, final Menu menu)
  {
		Menu menuTags = new Menu( menu.getShell(), SWT.DROP_DOWN);
	    final MenuItem itemTags = new MenuItem(menu, SWT.CASCADE);
	    Messages.setLanguageText(itemTags, "label.tags" ); 
	  
	    itemTags.setMenu(menuTags);

		MenuItem itemAddTag = new MenuItem( menuTags, SWT.PUSH);

		Messages.setLanguageText(itemAddTag, "label.add.tag");
		itemAddTag.addListener(SWT.Selection, new Listener() {
			@Override
			public void handleEvent(Event event) {

				TagUIUtils.createManualTag(new UIFunctions.TagReturner() {
					@Override
					public void returnedTags(Tag[] tags) {
						
						if ( tags != null ){
							
						    tv.runForSelectedRows(new TableGroupRowRunner() {
							      @Override
							      public void run(TableRowCore row) {
							      
							      	ShareResource sr = ((ShareResource)row.getDataSource(true));
							      	
							      	Map<String,String> props = sr.getProperties();
							      	
							      	props = props==null?new HashMap<>():new HashMap<>( props );
							      		
									String tag_str = props.get( ShareManager.PR_TAGS );

									Set<String>	existing = new HashSet<>();
									
									if ( tag_str == null ){
										
										tag_str = "";
										
									}else{
										
										String[] bits = tag_str.split( "," );
										
										for ( String bit: bits ){
											
											existing.add( bit.trim());
										}
									}
									
									for ( Tag t: tags ){
											
										String uid = String.valueOf( t.getTagUID());
												
										if ( !existing.contains( uid )){
											
											tag_str += (tag_str.isEmpty()?"":",") + uid;
										}
									}
									
							      	props.put( ShareManager.PR_TAGS, tag_str );
							      	
							      	sr.setProperties( props, false );
							      }
							    });
						}
					}
				});
			}
		});
	    
	    
	    MenuItem itemTagsSelect = new MenuItem(menuTags, SWT.PUSH);
	    itemTagsSelect.setText( MessageText.getString( "label.select.tags" ) + "..." );
	    
	    itemTagsSelect.addListener( SWT.Selection, new Listener(){
			@Override
			public void
			handleEvent(Event event)
			{
				TagManager tagManager = TagManagerFactory.getTagManager();
				
				TagType tt = tagManager.getTagType(TagType.TT_DOWNLOAD_MANUAL);
				
				List<Tag> all_tags = new ArrayList<>( tt.getTags());
				
				List<Object> selected = tv.getSelectedDataSources();
				
				List<Tag> selected_tags = new ArrayList<>();
				
				if ( selected.size() == 1 && selected.get(0) instanceof ShareResource ){
					
					Map<String,String> properties = ((ShareResource)selected.get(0)).getProperties();
					
					if ( properties != null ){
						
						String tags = properties.get( ShareManager.PR_TAGS );
						
						if ( tags != null ){
							
							String[] bits = tags.split( "," );
							
							for ( String bit: bits ){
								bit = bit.trim();
								
								if (!bit.isEmpty()){
									try{
										long uid = Long.parseLong( bit );
										
										Tag tag = tagManager.lookupTagByUID( uid );
										
										if ( tag != null ){
											selected_tags.add( tag );
										}
										
									}catch( Throwable e ){
										Debug.out(e);
									}
								}
							}
						}
					}
				}
				
				TagUIUtilsV3.showTagSelectionDialog( 
					all_tags, 
					selected_tags,
					new TagUIUtilsV3.TagSelectionListener()
					{
						@Override
						public void selected(List<Tag> tags){
							
							String tags_str = "";
							
							for ( Tag t: tags ){
								
								tags_str += (tags_str.isEmpty()?"":",") + t.getTagUID();
							}
							
							final String f = tags_str;
							
						    tv.runForSelectedRows(new TableGroupRowRunner() {
							      @Override
							      public void run(TableRowCore row) {
							      
							      	ShareResource sr = ((ShareResource)row.getDataSource(true));
							      	
							      	Map<String,String> props = sr.getProperties();
							      	
							      	props = props==null?new HashMap<>():new HashMap<>( props );
							      			
							      	props.put( ShareManager.PR_TAGS, f );
							      	
							      	sr.setProperties( props, false );
							      }
							    });
						}
					});
			}
		});	
	    
  		Menu menuCategory = new Menu( menu.getShell(), SWT.DROP_DOWN);
	    final MenuItem itemCategory = new MenuItem(menu, SWT.CASCADE);
	    Messages.setLanguageText(itemCategory, "MyTorrentsView.menu.setCategory"); 
	  
	    itemCategory.setMenu(menuCategory);

	    addCategorySubMenu( menuCategory );    
	    
	    
	    new MenuItem(menu, SWT.SEPARATOR);

	   final MenuItem itemRemove = new MenuItem(menu, SWT.PUSH);
	   Messages.setLanguageText(itemRemove, "MySharesView.menu.remove"); //$NON-NLS-1$
	   Utils.setMenuItemImage(itemRemove, "delete");


	   Object[] shares = tv.getSelectedDataSources().toArray();

	   itemRemove.setEnabled(shares.length > 0);

	   itemRemove.addListener(SWT.Selection, new Listener() {
		 @Override
		 public void handleEvent(Event e) {
		   removeSelectedShares();
		 }
	   });

    new MenuItem(menu, SWT.SEPARATOR);
	}

	@Override
	public void resourceAdded(ShareResource resource) {
	  tv.addDataSource(resource);
	}

	@Override
	public void resourceModified(ShareResource old_resource, ShareResource new_resource) {
		tv.removeDataSource( old_resource );
		tv.addDataSource( new_resource );
	}

	@Override
	public void resourceDeleted(ShareResource resource) {
	  tv.removeDataSource(resource);
	}

	@Override
	public void reportProgress(final int percent_complete) {	}

	@Override
	public void	reportCurrentTask(final String task_description) { }

	@Override
	public void tableRefresh() {
	  	UIFunctions uiFunctions = UIFunctionsManager.getUIFunctions();
 	  	if (uiFunctions != null) {
 	  		uiFunctions.refreshIconBar();
 	  	}
	}

	 private void addCategorySubMenu( Menu menuCategory ) {
	    MenuItem[] items = menuCategory.getItems();
	    int i;
	    for (i = 0; i < items.length; i++) {
	      items[i].dispose();
	    }

	    Category[] categories = CategoryManager.getCategories();
	    Arrays.sort(categories);

	    if (categories.length > 0) {
	      Category catUncat = CategoryManager.getCategory(Category.TYPE_UNCATEGORIZED);
	      if (catUncat != null) {
	        final MenuItem itemCategory = new MenuItem(menuCategory, SWT.PUSH);
	        Messages.setLanguageText(itemCategory, catUncat.getName());
	        itemCategory.setData("Category", catUncat);
	        itemCategory.addListener(SWT.Selection, new Listener() {
	          @Override
	          public void handleEvent(Event event) {
	            MenuItem item = (MenuItem)event.widget;
	            assignSelectedToCategory((Category)item.getData("Category"));
	          }
	        });

	        new MenuItem(menuCategory, SWT.SEPARATOR);
	      }

	      for (i = 0; i < categories.length; i++) {
	        if (categories[i].getType() == Category.TYPE_USER) {
	          final MenuItem itemCategory = new MenuItem(menuCategory, SWT.PUSH);
	          itemCategory.setText(categories[i].getName());
	          itemCategory.setData("Category", categories[i]);

	          TagUIUtils.setMenuIcon( itemCategory, categories[i] ); 
	          itemCategory.addListener(SWT.Selection, new Listener() {
	            @Override
	            public void handleEvent(Event event) {
	              MenuItem item = (MenuItem)event.widget;
	              assignSelectedToCategory((Category)item.getData("Category"));
	            }
	          });
	        }
	      }

	      new MenuItem(menuCategory, SWT.SEPARATOR);
	    }

	    final MenuItem itemAddCategory = new MenuItem(menuCategory, SWT.PUSH);
	    Messages.setLanguageText(itemAddCategory,
	                             "MyTorrentsView.menu.setCategory.add");

	    itemAddCategory.addListener(SWT.Selection, new Listener() {
	      @Override
	      public void handleEvent(Event event) {
	        addCategory();
	      }
	    });

	  }

	  private void addCategory() {
		  CategoryUIUtils.showCreateCategoryDialog(new TagReturner() {
			  @Override
			  public void returnedTags(Tag[] tags) {
				  if (tags.length == 1 && tags[0] instanceof Category) {
					  assignSelectedToCategory((Category) tags[0]);
				  }
			  }
		  });
	  }

	  private void assignSelectedToCategory(final Category category) {
	    tv.runForSelectedRows(new TableGroupRowRunner() {
	      @Override
	      public void run(TableRowCore row) {
	      	String value;

	      	if ( category == null ){

	      		value = null;

	      	}else if ( category == CategoryManager.getCategory(Category.TYPE_UNCATEGORIZED)){

	      		value = null;

	      	}else{

	      		value = category.getName();
	      	}

	        ((ShareResource)row.getDataSource(true)).setAttribute( category_attribute, value );
	      }
	    });
	  }

	@Override
	public void refreshToolBarItems(Map<String, Long> list) {

	  	boolean start = false, stop = false, remove = false;

    if (!CoreFactory.isCoreRunning()) {
    	return;
    }

	List	items = getSelectedItems();

    if (items.size() > 0) {

  	  PluginInterface pi = PluginInitializer.getDefaultInterface();

  	  com.biglybt.pif.download.DownloadManager	dm 		= pi.getDownloadManager();

      remove = true;

      for (int i=0; i < items.size(); i++){

    	ShareItem	item = (ShareItem)items.get(i);

        try{
    		Torrent	t = item.getTorrent();

    		Download	download = dm.getDownload( t );

    		if ( download == null ){

    			continue;
    		}

    		int	dl_state = download.getState();

    		if ( 	dl_state == Download.ST_ERROR ){

    		}else if ( dl_state != Download.ST_STOPPED ){

    			stop = true;

    		}else{

    			start = true;
    		}
    	}catch( Throwable e ){

    		Debug.printStackTrace(e);
    	}
      }
    }

  	list.put("start", start ? UIToolBarItem.STATE_ENABLED : 0);
  	list.put("stop", stop ? UIToolBarItem.STATE_ENABLED : 0);
  	list.put("remove", remove ? UIToolBarItem.STATE_ENABLED : 0);
  }


	@Override
	public boolean toolBarItemActivated(ToolBarItem item, long activationType,
	                                    Object datasource) {
		String itemKey = item.getID();

    if(itemKey.equals("remove")){
      removeSelectedShares();
      return true;
    }else if ( itemKey.equals( "stop" )){
    	stopSelectedShares();
    	return true;
    }else if ( itemKey.equals( "start" )){
    	startSelectedShares();
    	return true;
    }
		return false;
  }

  private List
  getSelectedItems()
  {
	  Object[] shares = tv.getSelectedDataSources().toArray();

	  List	items = new ArrayList();

	  if ( shares.length > 0 ){

	      for (int i=0; i < shares.length; i++){

	        ShareResource	share = (ShareResource)shares[i];

	        int	type = share.getType();

	        if ( type == ShareResource.ST_DIR ){

	        	ShareResourceDir	sr = (ShareResourceDir)share;

	        	items.add( sr.getItem());

	        }else if ( type == ShareResource.ST_FILE ){

	        	ShareResourceFile	sr = (ShareResourceFile)share;

	        	items.add( sr.getItem());

	        }else{

	        	ShareResourceDirContents	cont = (ShareResourceDirContents)share;

	        	List	entries = new ArrayList();

	        	getEntries( entries, cont );

	        	for (int j=0;j<entries.size();j++){

	    	        share = (ShareResource)entries.get(j);

	    	        type = share.getType();

	    	        if ( type == ShareResource.ST_DIR ){

	    	        	ShareResourceDir	sr = (ShareResourceDir)share;

	    	        	items.add( sr.getItem());

	    	        }else if ( type == ShareResource.ST_FILE ){

	    	        	ShareResourceFile	sr = (ShareResourceFile)share;

	    	        	items.add( sr.getItem());
	    	        }
	        	}
	        }
	      }
	  }

	  return( items );
  }

  private void
  getEntries(
	List						entries,
	ShareResourceDirContents	cont )
  {
	  ShareResource[]	kids = cont.getChildren();

	  for ( int i=0;i<kids.length;i++){

		  ShareResource	share = kids[i];

		  int	type  = share.getType();

		  if ( type == ShareResource.ST_DIR_CONTENTS ){

			  getEntries( entries, (ShareResourceDirContents)share );

		  }else{

			  entries.add( share );
		  }
	  }
  }

  private void
  startStopSelectedShares(
	boolean	do_stop )
  {
	  List	items = getSelectedItems();
	  if (items.size() == 0) {
	  	return;
	  }

	  PluginInterface pi = PluginInitializer.getDefaultInterface();

	  com.biglybt.pif.download.DownloadManager	dm 		= pi.getDownloadManager();

	  Tracker			tracker = pi.getTracker();


      for (int i=0;i<items.size();i++){

    	  ShareItem	item = (ShareItem)items.get(i);

        	try{
        		Torrent	t = item.getTorrent();

        		TrackerTorrent	tracker_torrent = tracker.getTorrent( t );

        		Download	download = dm.getDownload( t );

        		if ( download == null ){

        			continue;
        		}

        		int	dl_state = download.getState();

        		if ( 	dl_state == Download.ST_ERROR ){

        		}else if ( dl_state != Download.ST_STOPPED ){

        			if ( do_stop ){

        				try{
        					download.stop();
        				}catch( Throwable e ){
        				}

        				if ( tracker_torrent != null ){
	        				try{
	        					tracker_torrent.stop();
	        				}catch( Throwable e ){
	        				}
        				}
        			}

        		}else{

        			if ( !do_stop ){

        				try{
        					download.restart();
        				}catch( Throwable e ){
        				}

        				if ( tracker_torrent != null ){
	        				try{
	        					tracker_torrent.start();
	        				}catch( Throwable e ){
	        				}
        				}
        			}
        		}
        	}catch( Throwable e ){

        		Debug.printStackTrace(e);
        	}
        }
  }

  private void
  startSelectedShares()
  {
	  startStopSelectedShares( false );
  }

  private void
  stopSelectedShares()
  {
	  startStopSelectedShares( true );
  }

  private void
  removeSelectedShares()
  {
	stopSelectedShares();
    Object[] shares = tv.getSelectedDataSources().toArray();
    for (int i = 0; i < shares.length; i++) {
    	try{
    		((ShareResource)shares[i]).delete();

    	}catch( Throwable e ){

				Logger.log(new LogAlert(shares[i], false,
						"{globalmanager.download.remove.veto}", e));
    	}
    }
  }

	@Override
	public void addThisColumnSubMenu(String columnName, Menu menuThisColumn) {
	}

	// @see TableSelectionListener#defaultSelected(TableRowCore[], int)
	@Override
	public void defaultSelected(TableRowCore[] rows, int stateMask) {
	}

	// @see TableSelectionListener#deselected(TableRowCore[])
	@Override
	public void deselected(TableRowCore[] rows) {
  	UIFunctions uiFunctions = UIFunctionsManager.getUIFunctions();
  	if (uiFunctions != null) {
  		uiFunctions.refreshIconBar();
  	}
	}

	// @see TableSelectionListener#focusChanged(TableRowCore)
	@Override
	public void focusChanged(TableRowCore focus) {
  	UIFunctions uiFunctions = UIFunctionsManager.getUIFunctions();
  	if (uiFunctions != null) {
  		uiFunctions.refreshIconBar();
  	}
	}

	// @see TableSelectionListener#mouseEnter(TableRowCore)
	@Override
	public void mouseEnter(TableRowCore row) {
	}

	// @see TableSelectionListener#mouseExit(TableRowCore)
	@Override
	public void mouseExit(TableRowCore row) {
	}

	// @see TableSelectionListener#selected(TableRowCore[])
	@Override
	public void selected(TableRowCore[] row) {
  	UIFunctions uiFunctions = UIFunctionsManager.getUIFunctions();
  	if (uiFunctions != null) {
  		uiFunctions.refreshIconBar();
  	}
	}
	// @see ViewTitleInfo#getTitleInfoProperty(int)
	@Override
	public Object getTitleInfoProperty(int propertyID) {
		return null;
	}

	// @see ViewTitleInfo2#titleInfoLinked(MultipleDocumentInterface, MdiEntry)
	@Override
	public void titleInfoLinked(MultipleDocumentInterface mdi, MdiEntry mdiEntry) {
		mdiEntry.addListener(new MdiEntryDropListener() {
			@Override
			public boolean mdiEntryDrop(MdiEntry entry, Object droppedObject) {
				return share(droppedObject);
			}
		});
	}
}
