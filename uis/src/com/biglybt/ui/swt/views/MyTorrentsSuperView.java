/*
 * Copyright (C) Azureus Software, Inc, All Rights Reserved.
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 */
package com.biglybt.ui.swt.views;

import java.util.Map;

import org.eclipse.swt.SWT;
import org.eclipse.swt.events.*;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.layout.*;
import org.eclipse.swt.widgets.*;

import com.biglybt.core.Core;
import com.biglybt.core.CoreFactory;
import com.biglybt.core.CoreRunningListener;
import com.biglybt.core.config.COConfigurationManager;
import com.biglybt.core.download.DownloadManager;
import com.biglybt.core.internat.MessageText;
import com.biglybt.core.util.*;
import com.biglybt.ui.common.ToolBarItem;
import com.biglybt.ui.common.table.TableColumnCore;
import com.biglybt.ui.common.table.TableView;
import com.biglybt.ui.common.table.impl.TableColumnManager;
import com.biglybt.ui.selectedcontent.ISelectedContent;
import com.biglybt.ui.selectedcontent.SelectedContentListener;
import com.biglybt.ui.selectedcontent.SelectedContentManager;
import com.biglybt.ui.swt.DelayedListenerMultiCombiner;
import com.biglybt.ui.swt.Messages;
import com.biglybt.ui.swt.Utils;
import com.biglybt.ui.swt.components.BubbleTextBox;
import com.biglybt.ui.swt.mdi.MdiEntrySWT;
import com.biglybt.ui.swt.pif.UISWTView;
import com.biglybt.ui.swt.pif.UISWTViewEvent;
import com.biglybt.ui.swt.pifimpl.UISWTViewBuilderCore;
import com.biglybt.ui.swt.pifimpl.UISWTViewCoreEventListener;
import com.biglybt.ui.swt.pifimpl.UISWTViewImpl;
import com.biglybt.ui.swt.views.skin.SB_Transfers;
import com.biglybt.ui.swt.views.table.TableViewSWT;
import com.biglybt.ui.swt.views.table.impl.TableViewSWT_TabsCommon;
import com.biglybt.ui.swt.views.table.utils.TableColumnCreator;
import com.biglybt.util.MapUtils;

import com.biglybt.pif.ui.UIPluginViewToolBarListener;
import com.biglybt.pif.ui.tables.TableManager;

/**
 * Wraps a "Incomplete" torrent list and a "Complete" torrent list into
 * one view
 */
public class MyTorrentsSuperView
	implements UISWTViewCoreEventListener,
	AEDiagnosticsEvidenceGenerator, UIPluginViewToolBarListener
{
	private static int SASH_WIDTH = 5;

		// 0: incomplete top, complete bottom
		// 1: incomplete left, complete right
		// 2: complete top, incomplete bottom
		// 3: complete left, incomplete right
		// 4: combined
	
	private static int	SPLIT_MODE = 0;	

	static{
		COConfigurationManager.addAndFireParameterListener(
			"Library.TorrentViewSplitMode",
			(name)->{ 
				SPLIT_MODE= COConfigurationManager.getIntParameter( name );
				if ( SPLIT_MODE == 4 ){	// shouldn't happen in general
					SPLIT_MODE = 0;
				}
			});
	}
	
	private MyTorrentsView torrentview;
	private MyTorrentsView seedingview;

	private Composite form;

	private MyTorrentsView lastSelectedView;


	private Composite child1;


	private Composite child2;


	private final BubbleTextBox filterBox;

	private final Object initialDS;
	
	private Object ds;


	private UISWTView swtView;


	private MyTorrentsView viewWhenDeactivated;

	public MyTorrentsSuperView(BubbleTextBox filterBox, Object initialDS) {
		this.filterBox = filterBox;
		this.initialDS = initialDS;

		CoreFactory.addCoreRunningListener(core -> Utils.execSWTThread(() -> {
			TableColumnManager tcManager = TableColumnManager.getInstance();
			tcManager.addColumns(getCompleteColumns());
			tcManager.addColumns(getIncompleteColumns());
		}));
	}

  public Composite getComposite() {
    return form;
  }

  public void initialize(final Composite parent, Object dataSource) {
    if (form != null) {
      return;
    }

  	form = new Composite(parent, SWT.NONE);
  	FormLayout flayout = new FormLayout();
  	flayout.marginHeight = 0;
  	flayout.marginWidth = 0;
  	form.setLayout(flayout);
  	GridData gridData;
  	gridData = new GridData(GridData.FILL_BOTH);
  	form.setLayoutData(gridData);


  	GridLayout layout;

  	child1 = new Composite(form,SWT.NONE);
  	layout = new GridLayout();
  	layout.numColumns = 1;
  	layout.horizontalSpacing = 0;
  	layout.verticalSpacing = 0;
  	layout.marginHeight = 0;
  	layout.marginWidth = 0;
  	child1.setLayout(layout);

  	boolean split_horizontally = SPLIT_MODE == 0 || SPLIT_MODE == 2;
  	
  	final Sash sash = Utils.createSash( form, SASH_WIDTH, split_horizontally?SWT.HORIZONTAL:SWT.VERTICAL );

    child2 = new Composite(form,SWT.NULL);
    layout = new GridLayout();
    layout.numColumns = 1;
    layout.horizontalSpacing = 0;
    layout.verticalSpacing = 0;
    layout.marginHeight = 0;
    layout.marginWidth = 0;
    child2.setLayout(layout);

    FormData formData;

    // More precision, times by 100
    int weight = (int) (COConfigurationManager.getFloatParameter("MyTorrents.SplitAt"));
    if (weight > 10000) {
    	weight = 10000;
    } else if (weight < 100) {
    	weight *= 100;
    }
    // Min/max of 5%/95%
    if (weight < 500) {
    	weight = 500;
    } else if (weight > 9000) {
    	weight = 9000;
    }
    double pct = (float)weight / 10000;
    sash.setData("PCT", new Double(pct));

    if ( split_horizontally ){
	    // FormData for table child1
	    formData = new FormData();
	    formData.left = new FormAttachment(0, 0);
	    formData.right = new FormAttachment(100, 0);
	    formData.top = new FormAttachment(0, 0);
	    formData.bottom = new FormAttachment((int) (pct * 100), 0);
	    child1.setLayoutData(formData);
	    final FormData child1Data = formData;
	
	    // sash
	    formData = new FormData();
	    formData.left = new FormAttachment(0, 0);
	    formData.right = new FormAttachment(100, 0);
	    formData.top = new FormAttachment(child1);
	    formData.height = SASH_WIDTH;
	    sash.setLayoutData(formData);
	
	    // child2
	    formData = new FormData();
	    formData.left = new FormAttachment(0, 0);
	    formData.right = new FormAttachment(100, 0);
	    formData.bottom = new FormAttachment(100, 0);
	    formData.top = new FormAttachment(sash);
	
	    child2.setLayoutData(formData);
	
	
	    // Listeners to size the folder
	    sash.addSelectionListener(new SelectionAdapter() {
	    	@Override
	    	public void widgetSelected(SelectionEvent e) {
	    		final boolean FASTDRAG = true;
	
	    		if (FASTDRAG && e.detail == SWT.DRAG)
	    			return;
	
	    		child1Data.height = e.y + e.height - SASH_WIDTH;
	    		form.layout();
	
	    		Double l = new Double((double) child1.getBounds().height
	    				/ form.getBounds().height);
	    		sash.setData("PCT", l);
	    		if (e.detail != SWT.DRAG) {
	    			int i = (int) (l.doubleValue() * 10000);
	    			COConfigurationManager.setParameter("MyTorrents.SplitAt", i);
	    		}
	    	}
	    });
	
	    form.addListener(SWT.Resize, new DelayedListenerMultiCombiner() {
	    	@Override
	    	public void handleDelayedEvent(Event e) {
	    		if ( sash.isDisposed()){
	    			return;
	    		}
	    		Double l = (Double) sash.getData("PCT");
	    		if (l == null) {
	    			return;
	    		}
	    		int newHeight = (int) (form.getBounds().height * l.doubleValue());
	    		if (child1Data.height != newHeight || child1Data.bottom != null) {
	    			child1Data.bottom = null;
	    			child1Data.height = newHeight;
	    			form.layout();
	    		}
	    	}
	    });
    }else{
        // FormData for table child1
	    formData = new FormData();
	    formData.left = new FormAttachment(0, 0);
	    formData.bottom = new FormAttachment(100, 0);
	    formData.top = new FormAttachment(0, 0);
	    formData.right = new FormAttachment((int) (pct * 100), 0);
	    child1.setLayoutData(formData);
	    final FormData child1Data = formData;
	
	    // sash
	    formData = new FormData();
	    formData.top = new FormAttachment(0, 0);
	    formData.bottom = new FormAttachment(100, 0);
	    formData.left = new FormAttachment(child1);
	    formData.width = SASH_WIDTH;
	    sash.setLayoutData(formData);
	
	    // child2
	    formData = new FormData();
	    formData.top = new FormAttachment(0, 0);
	    formData.right = new FormAttachment(100, 0);
	    formData.bottom = new FormAttachment(100, 0);
	    formData.left = new FormAttachment(sash);
	
	    child2.setLayoutData(formData);
	
	    // Listeners to size the folder
	    sash.addSelectionListener(new SelectionAdapter() {
	    	@Override
	    	public void widgetSelected(SelectionEvent e) {
	    		final boolean FASTDRAG = true;
	
	    		if (FASTDRAG && e.detail == SWT.DRAG)
	    			return;
	
	    		child1Data.width = e.x + e.width - SASH_WIDTH;
	    		form.layout();
	
	    		Double l = new Double((double) child1.getBounds().width
	    				/ form.getBounds().width);
	    		sash.setData("PCT", l);
	    		if (e.detail != SWT.DRAG) {
	    			int i = (int) (l.doubleValue() * 10000);
	    			COConfigurationManager.setParameter("MyTorrents.SplitAt", i);
	    		}
	    	}
	    });
	
	    form.addListener(SWT.Resize, new DelayedListenerMultiCombiner() {
	    	@Override
	    	public void handleDelayedEvent(Event e) {
	    		if ( sash.isDisposed()){
	    			return;
	    		}
	    		Double l = (Double) sash.getData("PCT");
	    		if (l == null) {
	    			return;
	    		}
	    		int newWidth = (int) (form.getBounds().width * l.doubleValue());
	    		if (child1Data.width != newWidth || child1Data.right != null) {
	    			child1Data.right = null;
	    			child1Data.width = newWidth;
	    			form.layout();
	    		}
	    	}
	    });
    }
    
    CoreFactory.addCoreRunningListener(new CoreRunningListener() {
    	@Override
    	public void coreRunning(final Core core) {
    		Utils.execSWTThread(new AERunnable() {
    			@Override
    			public void runSupport() {
    				initializeWithCore(core, parent, dataSource);
    			}

    		});
    	}
    });

  }

  private void initializeWithCore(Core core, Composite parent, Object dataSource ) {

	boolean split_horizontally 	= SPLIT_MODE == 0 || SPLIT_MODE == 2;
	boolean switch_kids			= SPLIT_MODE == 2 || SPLIT_MODE == 3;

    torrentview = createTorrentView(core,
				SB_Transfers.getTableIdFromDataSource( TableManager.TABLE_MYTORRENTS_INCOMPLETE, dataSource ), false, getIncompleteColumns(),
				switch_kids?child2:child1);
		KeyListener keyListenerTV = filterBox != null ? filterBox.getKeyListener() : null;

    seedingview = createTorrentView(core,
    		SB_Transfers.getTableIdFromDataSource( TableManager.TABLE_MYTORRENTS_COMPLETE, dataSource) , true, getCompleteColumns(),
				switch_kids?child1:child2);
    KeyListener keyListenerSV = filterBox != null ? filterBox.getKeyListener() : null;

    if ( split_horizontally ){
    	
    		// the sub-tabs are shared by both leecher/seeding views so we have to inform them which is
    		// currently active so they accept selected-content change events correctly
    	
    	MyTorrentsView topView 		= switch_kids?seedingview:torrentview;
    	MyTorrentsView bottomView 	= switch_kids?torrentview:seedingview;
    	
    	topView.getComposite().addListener(SWT.FocusIn, new Listener() {
				@Override
				public void handleEvent(Event event) {
					bottomView.getTableView().getTabsCommon().setTvOverride(topView.getTableView());
				}
			});
	
    	bottomView.getComposite().addListener(SWT.FocusIn, new Listener() {
				@Override
				public void handleEvent(Event event) {
					bottomView.getTableView().getTabsCommon().setTvOverride(null);
				}
			});
    }

		if (filterBox != null) {
			filterBox.setKeyListener(new KeyListener() {
				@Override
				public void keyPressed(KeyEvent e) {
					MyTorrentsView currentView = getCurrentView();
					if (currentView == seedingview && keyListenerSV != null) {
						keyListenerSV.keyPressed(e);
					}
					if (currentView == torrentview && keyListenerTV != null) {
						keyListenerTV.keyPressed(e);
					}
				}

				@Override
				public void keyReleased(KeyEvent e) {
					MyTorrentsView currentView = getCurrentView();
					if (currentView == seedingview && keyListenerSV != null) {
						keyListenerSV.keyReleased(e);
					}
					if (currentView == torrentview && keyListenerTV != null) {
						keyListenerTV.keyReleased(e);
					}
				}
			});
		}
    	// delegate selections from the incomplete view to the sub-tabs owned by the seeding view

		SelectedContentManager.addCurrentlySelectedContentListener(
				new SelectedContentListener() {
					@Override
					public void currentlySelectedContentChanged(
							ISelectedContent[] currentContent, String viewId) {
						if (form.isDisposed() || torrentview == null
								|| seedingview == null) {

							SelectedContentManager.removeCurrentlySelectedContentListener(
									this);
							return;

						}

						TableView<?> selected_tv = SelectedContentManager.getCurrentlySelectedTableView();

						TableViewSWT<?> incomp_tv = torrentview.getTableView();
						TableViewSWT<?> comp_tv = seedingview.getTableView();

						if (incomp_tv == null || comp_tv == null
								|| (selected_tv != incomp_tv && selected_tv != comp_tv)) {
							return;
						}

						TableViewSWT_TabsCommon target_tabs;

						if (split_horizontally) {

							target_tabs = switch_kids?incomp_tv.getTabsCommon():comp_tv.getTabsCommon();

						}else{

							target_tabs = ((TableViewSWT<?>)selected_tv).getTabsCommon();
						}

						if (target_tabs == null) {
							return;
						}

						if (selected_tv.getSelectedRowsSize() == 0) {
							MyTorrentsView currentView = getCurrentView();
							if (currentView == torrentview) {
								seedingview.getTableView().requestFocus(1);
							} else {
								torrentview.getTableView().requestFocus(1);
							}
						}

						Utils.execSWTThread(() -> {
							TableView<?> selectedTV = SelectedContentManager.getCurrentlySelectedTableView();
							if (selectedTV == null) {
								return;
							}
							target_tabs.triggerTabViewsDataSourceChanged(selectedTV);
						});
					}
				});

  	initializeDone();
  }

  public void initializeDone() {
	}


  public void updateLanguage() {
  	// no super call, the views will do their own

    if (getComposite() == null || getComposite().isDisposed())
      return;

    if (seedingview != null) {
    	seedingview.updateLanguage();
    }
    if (torrentview != null) {
    	torrentview.updateLanguage();
    }
	}

	public String getFullTitle() {
    return MessageText.getString("MyTorrentsView.mytorrents");
  }

  // XXX: Is there an easier way to find out what has the focus?
  private MyTorrentsView getCurrentView() {
    // wrap in a try, since the controls may be disposed
    try {
      if (torrentview != null && torrentview.getTableView().isTableSelected()) {
        lastSelectedView = torrentview;
      } else if (seedingview != null &&seedingview.getTableView().isTableSelected()) {
      	lastSelectedView = seedingview;
      }
    } catch (Exception ignore) {/*ignore*/}

    return lastSelectedView;
  }

	private UIPluginViewToolBarListener getActiveToolbarListener() {
		MyTorrentsView[] viewsToCheck = {
			getCurrentView(),
			torrentview,
			seedingview
		};
		for (int i = 0; i < viewsToCheck.length; i++) {
			MyTorrentsView view = viewsToCheck[i];
			if (view != null) {
				MdiEntrySWT activeSubView = view.getTableView().getTabsCommon().getActiveSubView();
				if (activeSubView != null) {
					UIPluginViewToolBarListener toolBarListener = activeSubView.getToolBarListener();
					if (toolBarListener != null && toolBarListener.isActive()) {
						return toolBarListener;
					}
				}
				if (i == 0 && view.isTableFocus()) {
					return view;
				}
			}
		}

		return null;
	}

  @Override
  public void refreshToolBarItems(Map<String, Long> list) {
  	UIPluginViewToolBarListener currentView = getActiveToolbarListener();
    if (currentView != null) {
      currentView.refreshToolBarItems(list);
    }
  }

  @Override
  public boolean toolBarItemActivated(ToolBarItem item, long activationType, Object datasource) {
  	UIPluginViewToolBarListener currentView = getActiveToolbarListener();
    if (currentView != null) {
      if (currentView.toolBarItemActivated(item, activationType, datasource)) {
      	return true;
      }
    }
    MyTorrentsView currentView2 = getCurrentView();
    if (currentView2 != currentView && currentView2 != null) {
      if (currentView2.toolBarItemActivated(item, activationType, datasource)) {
      	return true;
      }
    }
    return false;
  }

  public DownloadManager[] getSelectedDownloads() {
	  MyTorrentsView currentView = getCurrentView();
	  if (currentView == null) {return null;}
	  return currentView.getSelectedDownloads();
  }

  @Override
  public void
  generate(
	IndentWriter	writer )
  {

	  try{
		  writer.indent();

		  writer.println( "Downloading" );

		  writer.indent();

		  torrentview.generate( writer );

	  }finally{

		  writer.exdent();

		  writer.exdent();
	  }

	  try{
		  writer.indent();

		  writer.println( "Seeding" );

		  writer.indent();

		  seedingview.generate( writer );

	  }finally{

		  writer.exdent();

		  writer.exdent();
	  }
  }

	private Image obfuscatedImage(Image image) {
		if (torrentview != null) {
			torrentview.obfuscatedImage(image);
		}
		if (seedingview != null) {
			seedingview.obfuscatedImage(image);
		}
		return image;
	}

	public Menu getPrivateMenu() {
		return null;
	}

	public void viewActivated() {
		SelectedContentManager.clearCurrentlySelectedContent();

		if (viewWhenDeactivated != null) {
			viewWhenDeactivated.getComposite().setFocus();
			viewWhenDeactivated.updateSelectedContent(true);
		} else {
			MyTorrentsView currentView = getCurrentView();
			if (currentView != null ) {
				currentView.updateSelectedContent();
			}
		}
	}

	public void viewDeactivated() {
		viewWhenDeactivated = getCurrentView();
    /*
    MyTorrentsView currentView = getCurrentView();
    if (currentView == null) {return;}
    String ID = currentView.getShortTitle();
    if (currentView instanceof MyTorrentsView) {
    	ID = ((MyTorrentsView)currentView).getTableView().getTableID();
    }

    TableView tv = null;
    if (currentView instanceof MyTorrentsView) {
    	tv = ((MyTorrentsView) currentView).getTableView();
    }
    //SelectedContentManager.clearCurrentlySelectedContent();
    SelectedContentManager.changeCurrentlySelectedContent(ID, null, tv);
    */
	}

	/**
	 * Returns the set of columns for the incomplete torrents view
	 * Subclasses my override to return a different set of columns
	 * @return
	 */
	protected TableColumnCore[] getIncompleteColumns(){
		return TableColumnCreator.createIncompleteDM( SB_Transfers.getTableIdFromDataSource( TableManager.TABLE_MYTORRENTS_INCOMPLETE, initialDS ));
	}

	/**
	 * Returns the set of columns for the completed torrents view
	 * Subclasses my override to return a different set of columns
	 * @return
	 */
	protected TableColumnCore[] getCompleteColumns(){
		return TableColumnCreator.createCompleteDM( SB_Transfers.getTableIdFromDataSource( TableManager.TABLE_MYTORRENTS_COMPLETE, initialDS ));
	}


	/**
	 * Returns an instance of <code>MyTorrentsView</code>
	 * Subclasses my override to return a different instance of MyTorrentsView
	 * @param _core
	 * @param isSeedingView
	 * @param columns
	 * @param c
	 * @return
	 */
	protected MyTorrentsView
	createTorrentView(
		Core 				_core,
		String				tableID,
		boolean 			isSeedingView,
		TableColumnCore[] 	columns,
		Composite 			c )
	{
		boolean split_horizontally 	= SPLIT_MODE == 0 || SPLIT_MODE == 2;
		boolean switch_kids			= SPLIT_MODE == 2 || SPLIT_MODE == 3;

		boolean support_tabs;
		
		if ( split_horizontally ){
			
			if ( switch_kids ){
				
				support_tabs = !isSeedingView;
				
			}else{
				
				support_tabs = isSeedingView;
			}
		}else{
			
			support_tabs = true;
		}
						
		MyTorrentsView view = new MyTorrentsView( _core, tableID, isSeedingView, columns, filterBox, support_tabs  );

		try {
			UISWTViewBuilderCore builder = new UISWTViewBuilderCore(
				tableID, null, view).setInitialDatasource(ds);
			UISWTViewImpl swtView = new UISWTViewImpl(builder, true);
			swtView.setDestroyOnDeactivate(false);

			swtView.setDelayInitializeToFirstActivate(false);
			swtView.initialize(c);
		} catch (Exception e) {
			Debug.out(e);
		}

		c.layout();
		
		return view;
	}


	public MyTorrentsView getTorrentview() {
		return torrentview;
	}


	public MyTorrentsView getSeedingview() {
		return seedingview;
	}

	public void dataSourceChanged(Object newDataSource) {
		ds = newDataSource;
	}

	@Override
	public boolean eventOccurred(UISWTViewEvent event) {
		switch (event.getType()) {
			case UISWTViewEvent.TYPE_CREATE:
				swtView = (UISWTView) event.getData();
				swtView.setToolBarListener(this);
				swtView.setTitle(getFullTitle());
				break;

			case UISWTViewEvent.TYPE_DESTROY:
				break;

			case UISWTViewEvent.TYPE_INITIALIZE:
				initialize((Composite) event.getData(), event.getView().getInitialDataSource());
				return true;

			case UISWTViewEvent.TYPE_LANGUAGEUPDATE:
				swtView.setTitle(getFullTitle());
				Messages.updateLanguageForControl(getComposite());
				break;

			case UISWTViewEvent.TYPE_DATASOURCE_CHANGED:
				dataSourceChanged(event.getData());
				break;

			case UISWTViewEvent.TYPE_REFRESH:
				break;

			case UISWTViewEvent.TYPE_OBFUSCATE:
				Object data = event.getData();
				if (data instanceof Map) {
					obfuscatedImage((Image) MapUtils.getMapObject((Map) data, "image",
							null, Image.class));
				}
				break;
		}

		if (seedingview != null) {
    	try {
    		seedingview.getSWTView().triggerEvent(event.getType(), event.getData());
    	} catch (Exception e) {
    		Debug.out(e);
    	}
		}

		if (torrentview != null) {
    	try {
    		torrentview.getSWTView().triggerEvent(event.getType(), event.getData());
    	} catch (Exception e) {
    		Debug.out(e);
    	}
		}

		// both subviews will get focusgained, resulting in the last one grabbing
		// "focus".  We restore last used focus, but only after the subviews are
		// done being greedy
		switch (event.getType()) {
			case UISWTViewEvent.TYPE_FOCUSGAINED:
				viewActivated();
				break;

			case UISWTViewEvent.TYPE_FOCUSLOST:
				viewDeactivated();
				break;
		}

		return true;
	}

	public UISWTView getSWTView() {
		return swtView;
	}
}
