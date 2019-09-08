/*
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

package com.biglybt.ui.swt.subscriptions;

import java.net.URL;
import java.util.ArrayList;
import java.util.Map;

import com.biglybt.pif.ui.UIInstance;
import com.biglybt.ui.common.table.*;
import com.biglybt.ui.swt.columns.subscriptions.*;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.KeyEvent;
import org.eclipse.swt.events.KeyListener;
import org.eclipse.swt.graphics.Font;
import org.eclipse.swt.graphics.FontData;
import org.eclipse.swt.layout.FormAttachment;
import org.eclipse.swt.layout.FormData;
import org.eclipse.swt.layout.FormLayout;
import org.eclipse.swt.widgets.*;
import com.biglybt.core.config.COConfigurationManager;
import com.biglybt.core.internat.MessageText;
import com.biglybt.core.util.ByteFormatter;
import com.biglybt.core.util.Constants;
import com.biglybt.core.util.Debug;
import com.biglybt.pif.PluginInterface;
import com.biglybt.pif.ui.UIManager;
import com.biglybt.pif.ui.UIPluginViewToolBarListener;
import com.biglybt.pif.ui.menus.*;
import com.biglybt.pif.ui.tables.TableContextMenuItem;
import com.biglybt.pif.ui.tables.TableManager;
import com.biglybt.pif.ui.toolbar.UIToolBarItem;
import com.biglybt.pifimpl.local.PluginInitializer;
import com.biglybt.ui.swt.Messages;
import com.biglybt.ui.swt.Utils;
import com.biglybt.ui.swt.pif.UISWTView;
import com.biglybt.ui.swt.pif.UISWTViewEvent;
import com.biglybt.ui.swt.pifimpl.UISWTViewCoreEventListener;
import com.biglybt.ui.swt.shells.MessageBoxShell;
import com.biglybt.ui.swt.views.table.TableViewSWT;
import com.biglybt.ui.swt.views.table.impl.TableViewFactory;

import com.biglybt.core.metasearch.Engine;
import com.biglybt.core.subs.Subscription;
import com.biglybt.core.subs.SubscriptionManagerFactory;
import com.biglybt.core.subs.SubscriptionManagerListener;
import com.biglybt.core.vuzefile.VuzeFile;
import com.biglybt.core.vuzefile.VuzeFileComponent;
import com.biglybt.core.vuzefile.VuzeFileHandler;
import com.biglybt.ui.UIFunctionsManager;
import com.biglybt.ui.UserPrompterResultListener;
import com.biglybt.ui.common.ToolBarItem;
import com.biglybt.ui.common.table.impl.TableColumnManager;
import com.biglybt.ui.mdi.MultipleDocumentInterface;
import com.biglybt.ui.selectedcontent.ISelectedContent;
import com.biglybt.ui.selectedcontent.SelectedContentManager;
import com.biglybt.ui.swt.imageloader.ImageLoader;
import com.biglybt.ui.swt.utils.ColorCache;

public class SubscriptionsView
	implements SubscriptionManagerListener, UIPluginViewToolBarListener,
	UISWTViewCoreEventListener
{
	protected static final String TABLE_ID = "subscriptions";

	private TableViewSWT view;

	private Composite viewComposite;

	private Font textFont1;

	private Font textFont2;

	private UISWTView swtView;

	public SubscriptionsView() {

	}


	/* (non-Javadoc)
	 * @see com.biglybt.core.subs.SubscriptionManagerListener#associationsChanged(byte[])
	 */
	@Override
	public void associationsChanged(byte[] association_hash) {
		// TODO Auto-generated method stub

	}

	/* (non-Javadoc)
	 * @see com.biglybt.core.subs.SubscriptionManagerListener#subscriptionSelected(com.biglybt.core.subs.Subscription)
	 */
	@Override
	public void
	subscriptionSelected(
		Subscription subscription )
	{
	}

	@Override
	public void
	subscriptionRequested(
		URL					url,
		Map<String,Object>	options )
	{
	}

	/* (non-Javadoc)
	 * @see com.biglybt.core.subs.SubscriptionManagerListener#subscriptionAdded(com.biglybt.core.subs.Subscription)
	 */
	@Override
	public void subscriptionAdded(Subscription subscription) {
		if ( subscription.isSubscribed()){
			view.addDataSource(subscription);
		}
	}

	/* (non-Javadoc)
	 * @see com.biglybt.core.subs.SubscriptionManagerListener#subscriptionRemoved(com.biglybt.core.subs.Subscription)
	 */
	@Override
	public void subscriptionRemoved(Subscription subscription) {
		view.removeDataSource(subscription);

	}

	/* (non-Javadoc)
	 * @see com.biglybt.core.subs.SubscriptionManagerListener#subscriptionChanged(com.biglybt.core.subs.Subscription)
	 */
	@Override
	public void subscriptionChanged(Subscription subscription) {
		if ( !subscription.isSubscribed()){
			subscriptionRemoved(subscription);
		}else if ( view.getRow(subscription) == null ){
			subscriptionAdded( subscription );
		}else{
			view.refreshTable(true);
		}
	}

	/* (non-Javadoc)
	 * @see com.biglybt.pif.ui.UIPluginViewToolBarListener#refreshToolBarItems(java.util.Map)
	 */
	@Override
	public void refreshToolBarItems(Map<String, Long> list) {
		if ( view == null ){
			return;	// can happen on first selection it seems
		}
		int numRows = view.getSelectedRowsSize();
		list.put("remove", numRows > 0 ? UIToolBarItem.STATE_ENABLED : 0);
		list.put("share", numRows == 1 ? UIToolBarItem.STATE_ENABLED : 0);
	}

	/* (non-Javadoc)
	 * @see com.biglybt.pif.ui.toolbar.UIToolBarActivationListener#toolBarItemActivated(ToolBarItem, long, java.lang.Object)
	 */
	@Override
	public boolean toolBarItemActivated(ToolBarItem item, long activationType,
	                                    Object datasource) {
		if("remove".equals(item.getID()) ) {
			removeSelected();
			return true;
		}
		return false;
	}


	private void removeSelected() {
		TableRowCore[] rows = view.getSelectedRows();
		Subscription[] subs = new Subscription[rows.length];
		int i = 0;
		for (Subscription subscription : subs) {
			subs[i] = (Subscription) rows[i++].getDataSource();
		}
		removeSubs(subs, 0);
	}

	private void removeSubs(final Subscription[] toRemove, final int startIndex) {
		if (toRemove == null || startIndex >= toRemove.length) {
			return;
		}

		if (toRemove[startIndex] == null) {
			int nextIndex = startIndex + 1;
			if (nextIndex < toRemove.length) {
				removeSubs(toRemove, nextIndex);
			}
			return;
		}

		MessageBoxShell mb = new MessageBoxShell(
				MessageText.getString("message.confirm.delete.title"),
				MessageText.getString("message.confirm.delete.text", new String[] {
					toRemove[startIndex].getName()
				}));

		if (startIndex == toRemove.length - 1) {
			mb.setButtons(0, new String[] {
				MessageText.getString("Button.yes"),
				MessageText.getString("Button.no"),
			}, new Integer[] { 0, 1 });
		} else {
			mb.setButtons(1, new String[] {
				MessageText.getString("Button.removeAll"),
				MessageText.getString("Button.yes"),
				MessageText.getString("Button.no"),
			}, new Integer[] { 2, 0, 1 });
		}

		mb.open(new UserPrompterResultListener() {
			@Override
			public void prompterClosed(int result) {
				if (result == 0) {
					toRemove[startIndex].remove();
				} else if (result == 2) {
					for (int i = startIndex; i < toRemove.length; i++) {
						if (toRemove[i] != null) {
							toRemove[i].remove();
						}
					}
					return;
				}

				int nextIndex = startIndex + 1;
				if (nextIndex < toRemove.length) {
					removeSubs(toRemove, nextIndex);
				}
			}
		});
	}


	private void delete() {
		if ( view != null && !view.isDisposed()){
			view.delete();
		}
		if (viewComposite != null && !viewComposite.isDisposed()) {
			viewComposite.dispose();
		}
		if(textFont1 != null && ! textFont1.isDisposed()) {
			textFont1.dispose();
		}
		if(textFont2 != null && ! textFont2.isDisposed()) {
			textFont2.dispose();
		}
	}

	private Composite getComposite() {
		return viewComposite;
	}

	private String getFullTitle() {
		return MessageText.getString("subscriptions.overview");
	}

	private void initialize(Composite parent) {

		viewComposite = new Composite(parent,SWT.NONE);
		viewComposite.setLayout(new FormLayout());

		TableColumnCore[] columns = new TableColumnCore[] {
				new ColumnSubscriptionNew(TABLE_ID),
				new ColumnSubscriptionName(TABLE_ID),
				new ColumnSubscriptionNbNewResults(TABLE_ID),
				new ColumnSubscriptionNbResults(TABLE_ID),
				new ColumnSubscriptionAutoDownload(TABLE_ID),

				new ColumnSubscriptionMaxResults(TABLE_ID),
				new ColumnSubscriptionLastChecked(TABLE_ID),
				new ColumnSubscriptionSubscribers(TABLE_ID),
				new ColumnSubscriptionEnabled(TABLE_ID),
				new ColumnSubscriptionCategory(TABLE_ID),
				new ColumnSubscriptionTag(TABLE_ID),
				new ColumnSubscriptionParent(TABLE_ID),
				new ColumnSubscriptionError(TABLE_ID),
				new ColumnSubscriptionNewestDate(TABLE_ID),

		};

		TableColumnManager tcm = TableColumnManager.getInstance();

		tcm.setDefaultColumnNames(TABLE_ID, columns);
		tcm.setDefaultSortColumnName(TABLE_ID, "name");
				
		view = TableViewFactory.createTableViewSWT(Subscription.class, TABLE_ID, TABLE_ID,
				columns, "name", SWT.MULTI | SWT.FULL_SELECTION | SWT.VIRTUAL);

		view.addLifeCycleListener(new TableLifeCycleListener() {
			@Override
			public void tableLifeCycleEventOccurred(TableView tv, int eventType, Map<String, Object> data) {
				switch (eventType) {
					case EVENT_TABLELIFECYCLE_INITIALIZED:
						SubscriptionManagerFactory.getSingleton().addListener( SubscriptionsView.this );

						view.addDataSources(SubscriptionManagerFactory.getSingleton().getSubscriptions( true ));
						break;

					case EVENT_TABLELIFECYCLE_DESTROYED:
						SubscriptionManagerFactory.getSingleton().removeListener( SubscriptionsView.this );

						break;
				}
			}

		});


		view.addSelectionListener(new TableSelectionAdapter() {

			PluginInterface pi = PluginInitializer.getDefaultInterface();
			UIManager uim = pi.getUIManager();

			MenuManager  menu_manager 	= uim.getMenuManager();
			TableManager table_manager 	= uim.getTableManager();

			ArrayList<TableContextMenuItem>	menu_items = new ArrayList<>();

			SubscriptionManagerUI.MenuCreator menu_creator =
					new SubscriptionManagerUI.MenuCreator()
					{
						@Override
						public com.biglybt.pif.ui.menus.MenuItem
						createMenu(
							String 	resource_id )
						{
							TableContextMenuItem menu =
								table_manager.addContextMenuItem( TABLE_ID, resource_id );
							menu.setDisposeWithUIDetach(UIInstance.UIT_SWT);

							menu_items.add( menu );

							return( menu );
						}

						@Override
						public void refreshView()
						{

						}
					};

			@Override
			public void defaultSelected(TableRowCore[] rows, int stateMask) {
				if(rows.length == 1) {
					TableRowCore row = rows[0];

					Subscription sub = (Subscription) row.getDataSource();
					if(sub == null) {
						return;
					}

					if (sub.isSearchTemplate()) {

						try{
							VuzeFile vf = sub.getSearchTemplateVuzeFile();

							if ( vf != null ){

								sub.setSubscribed( true );

								VuzeFileHandler.getSingleton().handleFiles( new VuzeFile[]{ vf }, VuzeFileComponent.COMP_TYPE_NONE );


								for ( VuzeFileComponent comp: vf.getComponents()){

									Engine engine = (Engine)comp.getData( Engine.VUZE_FILE_COMPONENT_ENGINE_KEY );

									if ( 	engine != null &&
											( 	engine.getSelectionState() == Engine.SEL_STATE_DESELECTED ||
												engine.getSelectionState() == Engine.SEL_STATE_FORCE_DESELECTED )){

										engine.setSelectionState( Engine.SEL_STATE_MANUAL_SELECTED );
									}
								}
							}
						}catch( Throwable e ){

							Debug.out( e );
						}
				} else {

						String key = "Subscription_" + ByteFormatter.encodeString(sub.getPublicKey());
						MultipleDocumentInterface mdi = UIFunctionsManager.getUIFunctions().getMDI();
						if (mdi != null) {
							mdi.showEntryByID(key);
						}
					}
				}

			}

			@Override
			public void selected(TableRowCore[] rows) {
				rows = view.getSelectedRows();
				ISelectedContent[] sels = new ISelectedContent[rows.length];

				java.util.List<Subscription> subs = new ArrayList<>();

				for (int i=0;i<rows.length;i++){

					Subscription sub = (Subscription)rows[i].getDataSource();

					sels[i] = new SubscriptionSelectedContent( sub );

					if ( sub != null ){

						subs.add( sub );
					}
				}

				SelectedContentManager.changeCurrentlySelectedContent(view.getTableID(), sels, view);

				for ( TableContextMenuItem mi: menu_items ){

					mi.remove();
				}

				if ( subs.size() > 0 ){

					SubscriptionManagerUI.createMenus( menu_manager, menu_creator, subs.toArray( new Subscription[0] ));
				}
			}

		}, false) ;

		view.addKeyListener(new KeyListener() {
			@Override
			public void keyPressed(KeyEvent event) {

			}

			@Override
			public void keyReleased(KeyEvent event) {
				if(event.keyCode == SWT.DEL) {
					removeSelected();
				}
			}
		});

		view.setRowDefaultHeightEM(1.4f);

		view.initialize(viewComposite);

		final Composite composite = new Composite(viewComposite,SWT.BORDER);
		composite.setBackground(ColorCache.getColor(composite.getDisplay(), "#F1F9F8"));

		Font font = composite.getFont();
		FontData fDatas[] = font.getFontData();
		for(int i = 0 ; i < fDatas.length ; i++) {
			fDatas[i].setHeight(150 * fDatas[i].getHeight() / 100);
			if(Constants.isWindows) {
				fDatas[i].setStyle(SWT.BOLD);
			}
		}

		textFont1 = new Font(composite.getDisplay(),fDatas);

		fDatas = font.getFontData();
		for(int i = 0 ; i < fDatas.length ; i++) {
			fDatas[i].setHeight(120 * fDatas[i].getHeight() / 100);
		}

		textFont2 = new Font(composite.getDisplay(),fDatas);

		Label preText = new Label(composite,SWT.NONE);
		preText.setForeground(ColorCache.getColor(composite.getDisplay(), "#6D6F6E"));
		preText.setFont(textFont1);
		preText.setText(MessageText.getString("subscriptions.view.help.1"));

		Label image = new Label(composite,SWT.NONE);
		ImageLoader.getInstance().setLabelImage(image, "btn_rss_add");

		Link postText = new Link(composite,SWT.NONE);
		postText.setForeground(ColorCache.getColor(composite.getDisplay(), "#6D6F6E"));
		postText.setFont(textFont2);
		postText.setText(MessageText.getString("subscriptions.view.help.2"));

		postText.addListener(SWT.Selection, new Listener() {
			@Override
			public void handleEvent(Event event) {
				if(event.text != null &&  ( event.text.startsWith("http://") || event.text.startsWith("https://") ) ) {
					Utils.launch(event.text);
				}
			}
		});

		Label close = new Label(composite,SWT.NONE);
		ImageLoader.getInstance().setLabelImage(close, "image.dismissX");
		close.setCursor(composite.getDisplay().getSystemCursor(SWT.CURSOR_HAND));
		close.addListener(SWT.MouseUp, new Listener() {
			@Override
			public void handleEvent(Event arg0) {
				COConfigurationManager.setParameter("subscriptions.view.showhelp", false);
				composite.setVisible(false);
				FormData data = (FormData) view.getComposite().getLayoutData();
				data.bottom = new FormAttachment(100,0);
				viewComposite.layout(true);
			}
		});

		FormLayout layout = new FormLayout();
		composite.setLayout(layout);

		FormData data;

		data = new FormData();
		data.left = new FormAttachment(0,15);
		data.top = new FormAttachment(0,20);
		data.bottom = new FormAttachment(postText,-5);
		preText.setLayoutData(data);

		data = new FormData();
		data.left = new FormAttachment(preText,5);
		data.top = new FormAttachment(preText,0,SWT.CENTER);
		image.setLayoutData(data);

		data = new FormData();
		data.left = new FormAttachment(preText,0,SWT.LEFT);
		//data.top = new FormAttachment(preText,5);
		data.bottom = new FormAttachment(100,-20);
		postText.setLayoutData(data);

		data = new FormData();
		data.right = new FormAttachment(100,-10);
		data.top = new FormAttachment(0,10);
		close.setLayoutData(data);

		data = new FormData();
		data.left = new FormAttachment(0,0);
		data.right = new FormAttachment(100,0);
		data.top = new FormAttachment(0,0);
		data.bottom = new FormAttachment(composite,0);
		viewComposite.setLayoutData(data);

		data = new FormData();
		data.left = new FormAttachment(0,0);
		data.right = new FormAttachment(100,0);
		data.bottom = new FormAttachment(100,0);
		composite.setLayoutData(data);

		COConfigurationManager.setBooleanDefault("subscriptions.view.showhelp", true);
		if(!COConfigurationManager.getBooleanParameter("subscriptions.view.showhelp")) {
			composite.setVisible(false);
			data = (FormData) viewComposite.getLayoutData();
			data.bottom = new FormAttachment(100,0);
			viewComposite.layout(true);
		}
	}

	private void refresh() {
		if ( view != null ){
			view.refreshTable(false);
		}
	}

	@Override
	public boolean eventOccurred(UISWTViewEvent event) {
    switch (event.getType()) {
      case UISWTViewEvent.TYPE_CREATE:
      	swtView = (UISWTView)event.getData();
      	swtView.setTitle(getFullTitle());
        break;

      case UISWTViewEvent.TYPE_DESTROY:
        delete();
        break;

      case UISWTViewEvent.TYPE_INITIALIZE:
        initialize((Composite)event.getData());
        break;

      case UISWTViewEvent.TYPE_LANGUAGEUPDATE:
      	Messages.updateLanguageForControl(getComposite());
      	swtView.setTitle(getFullTitle());
        break;

      case UISWTViewEvent.TYPE_DATASOURCE_CHANGED:
      	//dataSourceChanged(event.getData());
        break;

      case UISWTViewEvent.TYPE_FOCUSGAINED:
      	break;

      case UISWTViewEvent.TYPE_REFRESH:
        refresh();
        break;
    }

    return true;
  }

}
