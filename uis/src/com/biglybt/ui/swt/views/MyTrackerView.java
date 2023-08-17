/*
 * File    : MyTrackerView.java
 * Created : 30-Oct-2003
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

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URL;
import java.util.Arrays;
import java.util.Map;

import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Listener;
import org.eclipse.swt.widgets.Menu;
import org.eclipse.swt.widgets.MenuItem;

import com.biglybt.core.Core;
import com.biglybt.core.CoreFactory;
import com.biglybt.core.CoreRunningListener;
import com.biglybt.core.category.Category;
import com.biglybt.core.category.CategoryManager;
import com.biglybt.core.category.CategoryManagerListener;
import com.biglybt.core.download.DownloadManager;
import com.biglybt.core.logging.LogAlert;
import com.biglybt.core.logging.Logger;
import com.biglybt.core.tag.Tag;
import com.biglybt.core.torrent.TOTorrent;
import com.biglybt.core.tracker.host.TRHostListener;
import com.biglybt.core.tracker.host.TRHostTorrent;
import com.biglybt.core.tracker.host.TRHostTorrentRemovalVetoException;
import com.biglybt.core.util.AERunnable;
import com.biglybt.core.util.AsyncController;
import com.biglybt.core.util.Debug;
import com.biglybt.core.util.TorrentUtils;
import com.biglybt.pif.torrent.TorrentAttribute;
import com.biglybt.pif.tracker.TrackerTorrent;
import com.biglybt.pif.ui.UIPluginViewToolBarListener;
import com.biglybt.pif.ui.tables.TableManager;
import com.biglybt.pif.ui.toolbar.UIToolBarItem;
import com.biglybt.pifimpl.local.torrent.TorrentManagerImpl;
import com.biglybt.ui.UIFunctions;
import com.biglybt.ui.UIFunctions.TagReturner;
import com.biglybt.ui.UIFunctionsManager;
import com.biglybt.ui.common.ToolBarItem;
import com.biglybt.ui.common.table.*;
import com.biglybt.ui.common.table.impl.TableColumnManager;
import com.biglybt.ui.mdi.MultipleDocumentInterface;
import com.biglybt.ui.swt.Messages;
import com.biglybt.ui.swt.Utils;
import com.biglybt.ui.swt.mainwindow.Colors;
import com.biglybt.ui.swt.pifimpl.UISWTViewCoreEventListener;
import com.biglybt.ui.swt.shells.CoreWaiterSWT;
import com.biglybt.ui.swt.views.table.TableRowSWT;
import com.biglybt.ui.swt.views.table.TableViewSWT;
import com.biglybt.ui.swt.views.table.TableViewSWTMenuFillListener;
import com.biglybt.ui.swt.views.table.impl.TableViewFactory;
import com.biglybt.ui.swt.views.table.impl.TableViewTab;
import com.biglybt.ui.swt.views.tableitems.mytracker.*;
import com.biglybt.ui.swt.views.utils.CategoryUIUtils;
import com.biglybt.ui.swt.views.utils.TagUIUtils;


/**
 * @author parg
 * @author TuxPaper
 *         2004/Apr/20: Remove need for tableItemToObject
 *         2004/Apr/21: extends TableView instead of IAbstractView
 */

public class MyTrackerView
	extends TableViewTab<TRHostTorrent>
	implements TRHostListener, CategoryManagerListener, TableLifeCycleListener,
        TableSelectionListener, TableViewSWTMenuFillListener, TableRefreshListener,
	UIPluginViewToolBarListener, UISWTViewCoreEventListener
{
	protected static final TorrentAttribute	category_attribute =
			TorrentManagerImpl.getSingleton().getAttribute( TorrentAttribute.TA_CATEGORY );
	
	private static TableColumnCore[] basicItems =  new TableColumnCore[] {
		new NameItem(),
		new TrackerItem(),
		new StatusItem(),
		new CategoryItem(),
		new PassiveItem(),
		new ExternalItem(),
		new PersistentItem(),
		new SeedCountItem(),
		new PeerCountItem(),
		new BadNATCountItem(),
		new AnnounceCountItem(),
		new ScrapeCountItem(),
		new CompletedCountItem(),
		new UploadedItem(),
		new DownloadedItem(),
		new LeftItem(),
		new TotalBytesInItem(),
		new AverageBytesInItem(),
		new TotalBytesOutItem(),
		new AverageBytesOutItem(),
		new DateAddedItem(),
	};
	
	static{
		TableColumnManager tcManager = TableColumnManager.getInstance();

		tcManager.setDefaultColumnNames( TableManager.TABLE_MYTRACKER, basicItems );
	}
	
	private static TableViewSWT.ColorRequester	color_requester = ()-> 20;

	private Menu			menuCategory;

	private TableViewSWT<TRHostTorrent> tv;

	public MyTrackerView() {
		super("MyTrackerView");
	
		tv = TableViewFactory.createTableViewSWT(TrackerTorrent.class,
				TableManager.TABLE_MYTRACKER, getTextPrefixID(), basicItems, "name",
				SWT.MULTI | SWT.FULL_SELECTION | SWT.BORDER | SWT.VIRTUAL);
		tv.addLifeCycleListener(this);
		tv.addSelectionListener(this, false);
		tv.addMenuFillListener(this);
		tv.addRefreshListener(this, false);
	}

	@Override
	public TableViewSWT<TRHostTorrent> initYourTableView() {
		return tv;
	}

	@Override
	public void tableLifeCycleEventOccurred(TableView tv, int eventType,
			Map<String, Object> data) {
		switch (eventType) {
			case EVENT_TABLELIFECYCLE_INITIALIZED:
				CoreFactory.addCoreRunningListener(new CoreRunningListener() {
					@Override
					public void coreRunning(Core core) {
						core.getTrackerHost().addListener(MyTrackerView.this);
					}
				});
				break;

			case EVENT_TABLELIFECYCLE_DESTROYED:
				try {
					CoreFactory.getSingleton().getTrackerHost().removeListener(this);
				} catch (Exception ignore) {
				}
				break;
		}
	}

	// @see TableSelectionListener#defaultSelected(TableRowCore[])
	@Override
	public void defaultSelected(TableRowCore[] rows, int stateMask) {
		final TRHostTorrent torrent = (TRHostTorrent) tv.getFirstSelectedDataSource();
		if (torrent == null)
			return;
		CoreWaiterSWT.waitForCoreRunning(new CoreRunningListener() {

			@Override
			public void coreRunning(Core core) {
				DownloadManager dm = core.getGlobalManager().getDownloadManager(
						torrent.getTorrent());
				if (dm != null) {
					UIFunctions uiFunctions = UIFunctionsManager.getUIFunctions();
					if (uiFunctions != null) {
						uiFunctions.getMDI().showEntryByID(
								MultipleDocumentInterface.SIDEBAR_SECTION_TORRENT_DETAILS, dm);
					}
				}
			}
		});
	}

  @Override
  public void fillMenu(String sColumnName, final Menu menu) {
	    menuCategory = new Menu(getComposite().getShell(), SWT.DROP_DOWN);
	    final MenuItem itemCategory = new MenuItem(menu, SWT.CASCADE);
	    Messages.setLanguageText(itemCategory, "MyTorrentsView.menu.setCategory"); //$NON-NLS-1$
	    //itemCategory.setImage(ImageRepository.getImage("speed"));
	    itemCategory.setMenu(menuCategory);

	    addCategorySubMenu();

	    new MenuItem(menu, SWT.SEPARATOR);

	   final MenuItem itemStart = new MenuItem(menu, SWT.PUSH);
	   Messages.setLanguageText(itemStart, "MyTorrentsView.menu.start"); //$NON-NLS-1$
	   Utils.setMenuItemImage(itemStart, "start");

	   final MenuItem itemStop = new MenuItem(menu, SWT.PUSH);
	   Messages.setLanguageText(itemStop, "MyTorrentsView.menu.stop"); //$NON-NLS-1$
	   Utils.setMenuItemImage(itemStop, "stop");

	   final MenuItem itemRemove = new MenuItem(menu, SWT.PUSH);
	   Messages.setLanguageText(itemRemove, "MyTorrentsView.menu.remove"); //$NON-NLS-1$
	   Utils.setMenuItemImage(itemRemove, "delete");

	   Object[] hostTorrents = tv.getSelectedDataSources().toArray();

	   itemStart.setEnabled(false);
	   itemStop.setEnabled(false);
	   itemRemove.setEnabled(false);

	   if (hostTorrents.length > 0) {

			boolean	start_ok 	= true;
			boolean	stop_ok		= true;
			boolean	remove_ok	= true;

			for (int i = 0; i < hostTorrents.length; i++) {

				TRHostTorrent	host_torrent = (TRHostTorrent)hostTorrents[i];

				int	status = host_torrent.getStatus();

				if ( status != TRHostTorrent.TS_STOPPED ){

					start_ok	= false;

				}

				if ( status != TRHostTorrent.TS_STARTED ){

					stop_ok = false;
				}

				/*
				try{

					host_torrent.canBeRemoved();

				}catch( TRHostTorrentRemovalVetoException f ){

					remove_ok = false;
				}
				*/
			}
	   		itemStart.setEnabled(start_ok);
		 	itemStop.setEnabled(stop_ok);
		 	itemRemove.setEnabled(remove_ok);
	   }

	   itemStart.addListener(SWT.Selection, new Listener() {
		 @Override
		 public void handleEvent(Event e) {
		   startSelectedTorrents();
		 }
	   });

	   itemStop.addListener(SWT.Selection, new Listener() {
		 @Override
		 public void handleEvent(Event e) {
		   stopSelectedTorrents();
		 }
	   });

	   itemRemove.addListener(SWT.Selection, new Listener() {
		 @Override
		 public void handleEvent(Event e) {
		   removeSelectedTorrents();
		 }
	   });

    new MenuItem(menu, SWT.SEPARATOR);
  }

  // @see com.biglybt.ui.swt.views.TableViewSWTMenuFillListener#addThisColumnSubMenu(java.lang.String, org.eclipse.swt.widgets.Menu)
  @Override
  public void addThisColumnSubMenu(String columnName, Menu menuThisColumn) {
  }

	@Override
	public void
	torrentAdded(
		TRHostTorrent		host_torrent )
	{
	  tv.addDataSource(host_torrent);
	}

	@Override
	public void torrentChanged(TRHostTorrent t) { }

	@Override
	public void
	torrentRemoved(
		TRHostTorrent		host_torrent )
	{
	  tv.removeDataSource(host_torrent);
	}

	@Override
	public boolean
	handleExternalRequest(
		InetSocketAddress	client,
		String				user,
		String				url,
		URL					absolute_url,
		String				header,
		InputStream			is,
		OutputStream		os,
		AsyncController		async )

		throws IOException
	{
		return( false );
	}

	// @see TableRefreshListener#tableRefresh()
	@Override
	public void tableRefresh() {
		if (getComposite() == null || getComposite().isDisposed()){

			return;
	   	}

  	UIFunctions uiFunctions = UIFunctionsManager.getUIFunctions();
  	if (uiFunctions != null) {
  		uiFunctions.refreshIconBar();
  	}

		// Store values for columns that are calculate from peer information, so
		// that we only have to do one loop.  (As opposed to each cell doing a loop)
		// Calculate code copied from TrackerTableItem
		TableRowCore[] rows = tv.getRows();
		for (int x = 0; x < rows.length; x++) {
		  TableRowSWT row = (TableRowSWT)rows[x];

		  if (row == null){
		    continue;
		  }

		  TRHostTorrent	host_torrent = (TRHostTorrent)rows[x].getDataSource(true);

		  if (host_torrent == null){
			  continue;
		  }

		  long	uploaded	= host_torrent.getTotalUploaded();
		  long	downloaded	= host_torrent.getTotalDownloaded();
		  long	left		= host_torrent.getTotalLeft();

		  int		seed_count	= host_torrent.getSeedCount();

		  host_torrent.setData("GUI_PeerCount", new Long(host_torrent.getLeecherCount()));
		  host_torrent.setData("GUI_SeedCount", new Long(seed_count));
		  host_torrent.setData("GUI_BadNATCount", new Long(host_torrent.getBadNATCount()));
		  host_torrent.setData("GUI_Uploaded", new Long(uploaded));
		  host_torrent.setData("GUI_Downloaded", new Long(downloaded));
		  host_torrent.setData("GUI_Left", new Long(left));

		  if ( seed_count != 0 ){
			  Color fg = row.getForeground();

			  if (fg != null && fg.equals(Colors.blues[Colors.BLUES_MIDDARK])) {
				  row.requestForegroundColor( color_requester, Colors.blues[Colors.BLUES_MIDDARK]);
			  }
		  }
		}
	}

	@Override
	public void refreshToolBarItems(Map<String, Long> list) {
		boolean start = false, stop = false, remove = false;
    Object[] hostTorrents = tv.getSelectedDataSources().toArray();
    if (hostTorrents.length > 0) {
      remove = true;
      for (int i = 0; i < hostTorrents.length; i++) {
        TRHostTorrent	host_torrent = (TRHostTorrent)hostTorrents[i];

        int	status = host_torrent.getStatus();

        if ( status == TRHostTorrent.TS_STOPPED ){
          start	= true;
        }

        if ( status == TRHostTorrent.TS_STARTED ){
          stop = true;
        }

        /*
        try{
        	host_torrent.canBeRemoved();

        }catch( TRHostTorrentRemovalVetoException f ){

        	remove = false;
        }
        */
      }
    }

    list.put("start", start ? UIToolBarItem.STATE_ENABLED : 0);
    list.put("stop", stop ? UIToolBarItem.STATE_ENABLED : 0);
    list.put("remove", remove ? UIToolBarItem.STATE_ENABLED : 0);
  }


	// @see com.biglybt.ui.swt.views.table.impl.TableViewTab#toolBarItemActivated(ToolBarItem, long, java.lang.Object)
	@Override
	public boolean toolBarItemActivated(ToolBarItem item, long activationType,
	                                    Object datasource) {
		String itemKey = item.getID();

    if(itemKey.equals("start")) {
      startSelectedTorrents();
      return true;
    }
    if(itemKey.equals("stop")){
      stopSelectedTorrents();
      return true;
    }
    if(itemKey.equals("remove")){
      removeSelectedTorrents();
      return true;
    }

		return false;
	}

  private void stopSelectedTorrents() {
    tv.runForSelectedRows(new TableGroupRowRunner() {
      @Override
      public void run(TableRowCore row) {
        TRHostTorrent	torrent = (TRHostTorrent)row.getDataSource(true);
        if (torrent.getStatus() == TRHostTorrent.TS_STARTED)
          torrent.stop();
      }
    });
  }

  private void startSelectedTorrents() {
    tv.runForSelectedRows(new TableGroupRowRunner() {
      @Override
      public void run(TableRowCore row) {
        TRHostTorrent	torrent = (TRHostTorrent)row.getDataSource(true);
        if (torrent.getStatus() == TRHostTorrent.TS_STOPPED)
          torrent.start();
      }
    });
  }

  private void removeSelectedTorrents() {
    tv.runForSelectedRows(new TableGroupRowRunner() {
      @Override
      public void run(TableRowCore row) {
        TRHostTorrent	torrent = (TRHostTorrent)row.getDataSource(true);
      	try{
      		torrent.remove();

      	}catch( TRHostTorrentRemovalVetoException f ){

  				Logger.log(new LogAlert(torrent, false,
  						"{globalmanager.download.remove.veto}", f));
      	}
      }
    });
  }

  private void addCategorySubMenu() {
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

  @Override
  public void
  categoryAdded(Category category)
  {
  	Utils.execSWTThread(
	  		new AERunnable()
			{
	  			@Override
				  public void
				runSupport()
	  			{
	  				addCategorySubMenu();
	  			}
			});
  }

  @Override
  public void
  categoryRemoved(
  	Category category)
  {
  	Utils.execSWTThread(
  		new AERunnable()
		{
  			@Override
			  public void
			runSupport()
  			{
  				addCategorySubMenu();
  			}
		});
  }

  @Override
  public void categoryChanged(Category category) {
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
		CoreWaiterSWT.waitForCoreRunning(new CoreRunningListener() {
			@Override
			public void coreRunning(final Core core) {
				assignSelectedToCategory(core, category);
			}
		});
  }

  private void assignSelectedToCategory(final Core core,
			final Category category) {
    tv.runForSelectedRows(new TableGroupRowRunner() {
      @Override
      public void run(TableRowCore row) {

	    TRHostTorrent	tr_torrent = (TRHostTorrent)row.getDataSource(true);

		final TOTorrent	torrent = tr_torrent.getTorrent();

		DownloadManager dm = core.getGlobalManager().getDownloadManager( torrent );

		if ( dm != null ){

			dm.getDownloadState().setCategory( category );

		}else{

	     	String cat_str;

	      	if ( category == null ){

				cat_str = null;

	      	}else if ( category == CategoryManager.getCategory(Category.TYPE_UNCATEGORIZED)){

				cat_str = null;

	      	}else{

				cat_str = category.getName();
	      	}
				// bit of a hack-alert here

			TorrentUtils.setPluginStringProperty( torrent, "azcoreplugins.category", cat_str );

			try{
				TorrentUtils.writeToFile( torrent );

			}catch( Throwable e ){

				Debug.printStackTrace( e );
			}
		}
      }
    });
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

	// @see TableSelectionListener#selected(TableRowCore[])
	@Override
	public void selected(TableRowCore[] rows) {
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
}
