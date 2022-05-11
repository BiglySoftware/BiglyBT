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
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;

import com.biglybt.core.Core;
import com.biglybt.ui.swt.Messages;
import com.biglybt.ui.swt.mainwindow.Colors;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.StackLayout;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.Font;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.layout.FormAttachment;
import org.eclipse.swt.layout.FormData;
import org.eclipse.swt.layout.FormLayout;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Link;
import org.eclipse.swt.widgets.Listener;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.TabFolder;
import org.eclipse.swt.widgets.TabItem;
import org.eclipse.swt.widgets.Table;
import org.eclipse.swt.widgets.TableColumn;
import org.eclipse.swt.widgets.TableItem;
import org.eclipse.swt.widgets.Text;
import com.biglybt.core.config.COConfigurationManager;
import com.biglybt.core.download.DownloadManager;
import com.biglybt.core.internat.MessageText;
import com.biglybt.core.util.AENetworkClassifier;
import com.biglybt.core.util.AERunnable;
import com.biglybt.core.util.ByteFormatter;
import com.biglybt.core.util.Debug;
import com.biglybt.pif.ui.UIManager;
import com.biglybt.pif.ui.tables.TableCell;
import com.biglybt.pif.ui.tables.TableCellAddedListener;
import com.biglybt.pif.ui.tables.TableCellRefreshListener;
import com.biglybt.pif.ui.tables.TableColumnCreationListener;
import com.biglybt.pif.ui.tables.TableManager;
import com.biglybt.pif.utils.subscriptions.SubscriptionManager;
import com.biglybt.pifimpl.local.PluginInitializer;
import com.biglybt.ui.swt.Utils;
import com.biglybt.ui.swt.components.shell.ShellFactory;
import com.biglybt.ui.swt.views.table.TableCellSWT;
import com.biglybt.ui.swt.views.table.TableCellSWTPaintListener;
import com.biglybt.ui.swt.views.table.TableViewSWT;
import com.biglybt.ui.swt.views.table.impl.TableViewFactory;

import com.biglybt.core.CoreFactory;
import com.biglybt.core.CoreRunningListener;
import com.biglybt.core.subs.Subscription;
import com.biglybt.core.subs.SubscriptionHistory;
import com.biglybt.core.subs.SubscriptionManagerFactory;
import com.biglybt.core.subs.SubscriptionUtils;
import com.biglybt.core.subs.SubscriptionUtils.SubscriptionDownloadDetails;
import com.biglybt.ui.UIFunctionsManager;
import com.biglybt.ui.common.table.TableColumnCore;
import com.biglybt.ui.common.table.TableRowCore;
import com.biglybt.ui.common.table.TableSelectionListener;
import com.biglybt.ui.common.table.impl.TableColumnManager;
import com.biglybt.ui.common.updater.UIUpdatable;
import com.biglybt.ui.mdi.MultipleDocumentInterface;
import com.biglybt.ui.swt.imageloader.ImageLoader;
import com.biglybt.ui.swt.uiupdater.UIUpdaterSWT;
import com.biglybt.ui.swt.utils.ColorCache;
import com.biglybt.ui.swt.utils.FontUtils;

public class SubscriptionWizard {

	private static final int MODE_OPT_IN = 1;
	public static final int MODE_SUBSCRIBE = 2;
	public static final int MODE_CREATE_SEARCH = 3;
	public static final int MODE_CREATE_RSS = 4;
	public static final int MODE_CREATE_TEMPLATE = 5;

	private static final int RANK_COLUMN_WIDTH = 85;
	private static final String TABLE_SUB_WIZ = "SubscriptionWizard";

	private final String TITLE_OPT_IN = MessageText.getString("Wizard.Subscription.optin.title");
	private final String TITLE_SUBSCRIBE = MessageText.getString("Wizard.Subscription.subscribe.title");
	private final String TITLE_CREATE_RSS = MessageText.getString("Wizard.Subscription.create.title");
	private final String TITLE_CREATE_TEMPLATE = MessageText.getString("Wizard.Subscription.template.title");

	int defaultMode = MODE_SUBSCRIBE;
	
	Display display;
	Shell shell;

	Image rankingBars;
	Color rankingBorderColor;

	Label title;

	Button cancelButton;
	Button searchButton;
	Button saveButton;
	Button yesButton;
	Button addButton;
	Button availableButton;
	Button createButton;

	Font boldFont;
	Font titleFont;
	Font subTitleFont;

	Composite main;
	StackLayout mainLayout;
	Composite optinComposite;
	Composite createComposite;
	TabFolder createTabFolder;
	TabItem   createRSSTabItem;
	TabItem   createSearchTabItem;
	TabItem   createTemplateTabItem;
	Composite availableSubscriptionComposite;

	Table libraryTable;
	Listener saveListener;
	Listener searchListener;

	Text searchInput;
	Text feedUrl;
	Text subsName;
	Text templateName;
	Button anonCheck;

	int		mode;
	String 	subs_name_default;

	SubscriptionDownloadDetails[] availableSubscriptions;
	Subscription[] subscriptions;

	DownloadManager download;
	URL				rss_feed_url;
	boolean			anon_default = false;
	int				frequency	= 0;
	
	private ImageLoader imageLoader;
	private TableViewSWT<Subscription> tvSubscriptions;
	private static boolean columnsAdded = false;

	public SubscriptionWizard() {
		init();
	}

	public SubscriptionWizard( int mode) {
		defaultMode = mode;
		if ( defaultMode != MODE_OPT_IN ){
			COConfigurationManager.setParameter( "subscriptions.opted_in", true );
		}
		init();
	}

	public
	SubscriptionWizard(
		URL					url,
		Map<String,Object>	options )
	{
		rss_feed_url	= url;

		Boolean anon = (Boolean)options.get(SubscriptionManager.SO_ANONYMOUS );

		anon_default = anon != null && anon;

		subs_name_default = (String)options.get(SubscriptionManager.SO_NAME );

		Number freq = (Number)options.get(SubscriptionManager.SO_FREQUENCY );

		if ( freq != null ){
			
			frequency = freq.intValue();
		}
		
		init();
	}

	public SubscriptionWizard(
		DownloadManager _download)
	{
		download = _download;

		init();
	}

	protected void
	init()
	{
		COConfigurationManager.setParameter( "subscriptions.wizard.shown", true );

		CoreFactory.addCoreRunningListener(new CoreRunningListener() {
			@Override
			public void coreRunning(Core core) {
				init(core);
			}
		});
	}

	protected void init(Core core) {
		imageLoader = ImageLoader.getInstance();

		/*SubscriptionDownloadDetails[] allSubscriptions = SubscriptionUtils.getAllCachedDownloadDetails();
		List notYetSubscribed = new ArrayList(allSubscriptions.length);
		for(int i = 0 ; i < allSubscriptions.length ; i++) {
			Subscription[] subs = allSubscriptions[i].getSubscriptions();
			boolean subscribedToAll = true;
			for(int j = 0 ; j < subs.length ; j++) {
				subscribedToAll = subscribedToAll && subs[j].isSubscribed();
			}
			if(!subscribedToAll) {
				notYetSubscribed.add(allSubscriptions[i]);
			}
		}
		availableSubscriptions = (SubscriptionDownloadDetails[]) notYetSubscribed.toArray(new SubscriptionDownloadDetails[notYetSubscribed.size()]);*/
		availableSubscriptions = SubscriptionUtils.getAllCachedDownloadDetails(core);
		Arrays.sort(availableSubscriptions,new Comparator<SubscriptionDownloadDetails>() {
			@Override
			public int compare(SubscriptionDownloadDetails o1, SubscriptionDownloadDetails o2) {
				if (o1 == null || o2 == null) return 0;
				return o1.getDownload().getDisplayName().compareTo(o2.getDownload().getDisplayName());
			}
		});


		shell = ShellFactory.createMainShell(SWT.TITLE | SWT.CLOSE | SWT.RESIZE);
		shell.setSize(650,400);
		Utils.centreWindow(shell);

		shell.setMinimumSize(550,400);

		display = shell.getDisplay();

		Utils.setShellIcon(shell);

		rankingBars = imageLoader.getImage("ranking_bars");
		rankingBorderColor = new Color(display,200,200,200);

		createFonts();

		shell.setText(MessageText.getString("Wizard.Subscription.title"));

		shell.addListener(SWT.Dispose, new Listener() {
			@Override
			public void handleEvent(Event event) {
				imageLoader.releaseImage("ranking_bars");
				imageLoader.releaseImage("wizard_header_bg");
				imageLoader.releaseImage("icon_rss");

				if(titleFont != null && !titleFont.isDisposed()) {
					titleFont.dispose();
				}

				if(boldFont != null && !boldFont.isDisposed()) {
					boldFont.dispose();
				}

				if(subTitleFont != null && !subTitleFont.isDisposed()) {
					subTitleFont.dispose();
				}

				if(rankingBorderColor != null && !rankingBorderColor.isDisposed()) {
					rankingBorderColor.dispose();
				}

			}
		});

		Composite header = new Composite(shell, SWT.NONE);
		header.setBackgroundImage(imageLoader.getImage("wizard_header_bg"));
		Label topSeparator = new Label(shell,SWT.SEPARATOR |SWT.HORIZONTAL);
		main = new Composite(shell, SWT.NONE);
		Label bottomSeparator = new Label(shell,SWT.SEPARATOR |SWT.HORIZONTAL);
		Composite footer = new Composite(shell, SWT.NONE);

		FormLayout layout = new FormLayout();
		shell.setLayout(layout);

		FormData data;

		data = new FormData();
		data.top = new FormAttachment(0,0);
		data.left = new FormAttachment(0,0);
		data.right = new FormAttachment(100,0);
		//data.height = 50;
		header.setLayoutData(data);

		data = new FormData();
		data.top = new FormAttachment(header,0);
		data.left = new FormAttachment(0,0);
		data.right = new FormAttachment(100,0);
		topSeparator.setLayoutData(data);

		data = new FormData();
		data.top = new FormAttachment(topSeparator,0);
		data.left = new FormAttachment(0,0);
		data.right = new FormAttachment(100,0);
		data.bottom = new FormAttachment(bottomSeparator,0);
		main.setLayoutData(data);

		data = new FormData();
		data.left = new FormAttachment(0,0);
		data.right = new FormAttachment(100,0);
		data.bottom = new FormAttachment(footer,0);
		bottomSeparator.setLayoutData(data);

		data = new FormData();
		data.bottom = new FormAttachment(100,0);
		data.left = new FormAttachment(0,0);
		data.right = new FormAttachment(100,0);
		//data.height = 100;
		footer.setLayoutData(data);

		populateHeader(header);
		populateFooter(footer);

		mainLayout = new StackLayout();
		main.setLayout(mainLayout);

		optinComposite = createOptInComposite(main);
		createComposite = createCreateComposite(main);
		availableSubscriptionComposite = createAvailableSubscriptionComposite(main);


		setDefaultAvailableMode();

		shell.layout();
		shell.open();

		setInitialViews();
	}

	protected void
	setInitialViews()
	{
		if ( availableSubscriptions != null ){

			for (int i=0;i<availableSubscriptions.length;i++){

				SubscriptionDownloadDetails details = availableSubscriptions[i];

				if ( details.getDownload()== download ){

					final int f_i = i;

					Utils.execSWTThread(
						new Runnable()
						{
							@Override
							public void
							run()
							{
								libraryTable.setTopIndex( f_i );
							}
						});
				}
			}
		}
	}
	private void populateHeader(Composite header) {
		title = new Label(header, SWT.WRAP);

		title.setFont(titleFont);

		FillLayout layout = new FillLayout();
		layout.marginHeight = 10;
		layout.marginWidth = 10;
		header.setLayout(layout);

	}

	private Composite createOptInComposite(Composite parent) {
		Composite composite = new Composite(parent,SWT.NONE);

		Label description = new Label(composite,SWT.WRAP);
		description.setFont(boldFont);
		description.setText(MessageText.getString("Wizard.Subscription.optin.description"));

		/*Label descLibraryIcon = new Label(composite, SWT.NONE);
		descLibraryIcon.setImage(ImageRepository.getImage("btn_rss_add"));

		Label descLibraryText = new Label(composite, SWT.NONE);
		descLibraryText.setText(MessageText.getString("Wizard.Subscription.optin.description.library"));

		Label descSidebarIcon = new Label(composite, SWT.NONE);
		descSidebarIcon.setImage(ImageRepository.getImage("btn_sidebar_add"));

		Label descSidebarText = new Label(composite, SWT.NONE);
		descSidebarText.setText(MessageText.getString("Wizard.Subscription.optin.description.sidebar"));

		Label help = new Label(composite, SWT.NONE);
		help.setFont(boldFont);
		help.setText(MessageText.getString("Wizard.Subscription.optin.help"));*/

		FormLayout layout = new FormLayout();
		composite.setLayout(layout);

		FormData data;

		data = new FormData();
		data.top = new FormAttachment(0,40);
		data.left = new FormAttachment(0,50);
		data.right= new FormAttachment(100,-50);
		description.setLayoutData(data);

		/*data = new FormData();
		data.top = new FormAttachment(description,10);
		data.left = new FormAttachment(0,50);
		descLibraryIcon.setLayoutData(data);

		data = new FormData();
		//data.top = new FormAttachment(description,10);
		data.left = new FormAttachment(descLibraryIcon,10);
		data.right= new FormAttachment(100,-50);
		data.bottom= new FormAttachment(descLibraryIcon,-3,SWT.BOTTOM);
		descLibraryText.setLayoutData(data);

		data = new FormData();
		data.top = new FormAttachment(descLibraryText,10);
		//data.left = new FormAttachment(descLibraryIcon,-10,SWT.CENTER);
		data.left = new FormAttachment(0,50);
		descSidebarIcon.setLayoutData(data);

		data = new FormData();
		//data.top = new FormAttachment(descLibraryText,10);
		data.left = new FormAttachment(descLibraryText,0,SWT.LEFT);
		data.right= new FormAttachment(100);
		data.bottom= new FormAttachment(descSidebarIcon,-3,SWT.BOTTOM);
		descSidebarText.setLayoutData(data);

		data = new FormData();
		data.right= new FormAttachment(100,-20);
		data.bottom= new FormAttachment(100,-10);
		help.setLayoutData(data);*/

		return composite;
	}

	private Composite createCreateComposite(Composite parent) {
		Composite composite = new Composite(parent,SWT.NONE);

		FillLayout layout = new FillLayout();
		layout.marginHeight = 8;
		layout.marginWidth  = 8;

		composite.setLayout(layout);

		createTabFolder = new TabFolder(composite,SWT.NONE);
		createTabFolder.setFont(subTitleFont);

		createSearchTabItem = new TabItem(createTabFolder,SWT.NONE);
		createSearchTabItem.setText(MessageText.getString("Wizard.Subscription.create.search"));
		createSearchTabItem.setControl(createCreateSearchComposite(createTabFolder));

		createRSSTabItem = new TabItem(createTabFolder,SWT.NONE);
		createRSSTabItem.setText("  " + MessageText.getString("Wizard.Subscription.create.rss"));
		createRSSTabItem.setControl(createCreateRSSComposite(createTabFolder));

		createTemplateTabItem = new TabItem(createTabFolder,SWT.NONE);
		createTemplateTabItem.setText("  " + MessageText.getString("Wizard.Subscription.create.template"));
		createTemplateTabItem.setControl(createCreateTemplateComposite(createTabFolder));

		createTabFolder.addListener(SWT.Selection, new Listener() {
			@Override
			public void handleEvent(Event event) {
				TabItem[] selectedItems = createTabFolder.getSelection();
				if(selectedItems.length != 1) {
					return;
				}
				TabItem selectedItem = selectedItems[0];
				if(selectedItem == createRSSTabItem) {
					setMode(MODE_CREATE_RSS);
				} else if ( selectedItem == createSearchTabItem ){
					setMode(MODE_CREATE_SEARCH);
				}else{
					setMode(MODE_CREATE_TEMPLATE);
				}
			}
		});

		return composite;
	}

	private Composite createCreateRSSComposite(Composite parent) {
		Composite composite = new Composite(parent,SWT.NONE);

		Label subTitle1 = new Label(composite,SWT.WRAP);
		subTitle1.setFont(subTitleFont);
		subTitle1.setText(MessageText.getString("Wizard.Subscription.rss.subtitle1"));

		Composite cSearchInput = new Composite(composite, SWT.NONE);
		cSearchInput.setLayout(new FormLayout());
		imageLoader.setBackgroundImage(cSearchInput, "search_bg");
		Rectangle imageBounds = cSearchInput.getBackgroundImage().getBounds();

		feedUrl = new Text(cSearchInput, SWT.SINGLE);
		FontUtils.fontToWidgetHeight(feedUrl);
		feedUrl.setText("http://");
//		feedUrl.setData("visited",new Boolean(false));
//
//		feedUrl.addListener(SWT.FocusIn, new Listener() {
//			public void handleEvent(Event arg0) {
//				boolean visited = ((Boolean) feedUrl.getData("visited")).booleanValue();
//				if(visited) return;
//				feedUrl.setData("visited",new Boolean(true));
//				feedUrl.setText("");
//			}
//		});

		feedUrl.addListener (SWT.DefaultSelection, saveListener);

		feedUrl.addListener(SWT.Modify, new Listener() {
			@Override
			public void handleEvent(Event event) {
				boolean valid_url = false;
				try {
					URL url = new URL(feedUrl.getText());
					String protocol = url.getProtocol().toLowerCase();
					valid_url = protocol.equals( "tor" ) || protocol.equals( "azplug" ) || protocol.equals( "file") || url.getHost().trim().length() > 0;
				} catch (Exception e) {}

				saveButton.setEnabled(valid_url);
			}
		});

		Label subTitle2 = new Label(composite,SWT.WRAP);
		//subTitle2.setFont(subTitleFont);
		subTitle2.setText(MessageText.getString("Wizard.Subscription.rss.subtitle2"));

		Label rssBullet = new Label(composite, SWT.NONE);
		imageLoader.setLabelImage(rssBullet, "rss");

		Label subsNameText = new Label(composite,SWT.WRAP);
		subsNameText.setText( MessageText.getString( "TableColumn.header.name" ));

		subsName = new Text(composite, SWT.BORDER);
		if ( subs_name_default != null ){
			subsName.setText( subs_name_default );
		}
		anonCheck = new Button(composite, SWT.CHECK );
		Messages.setLanguageText(anonCheck, "label.anon");

		anonCheck.setSelection( anon_default );

		Label subTitle3 = new Label(composite,SWT.WRAP);
		subTitle3.setFont(subTitleFont);
		subTitle3.setText(MessageText.getString("Wizard.Subscription.rss.subtitle3"));

		FormLayout layout = new FormLayout();
		layout.marginWidth = 50;
		layout.marginTop = 25;
		composite.setLayout(layout);

		FormData data;

		data = new FormData();
		data.top = new FormAttachment(0);
		data.left = new FormAttachment(0);
		data.right = new FormAttachment(100);
		subTitle1.setLayoutData(data);

		data = new FormData();
		data.top = new FormAttachment(subTitle1,5);
		data.left = new FormAttachment(50,-imageBounds.width/2);
		data.width = imageBounds.width;
		data.height = imageBounds.height;
		cSearchInput.setLayoutData(data);

			// feed url

		data = new FormData();
		data.top = new FormAttachment(0,5);
		data.left = new FormAttachment(0, 45);
		data.right = new FormAttachment(100, -8);
		data.bottom = new FormAttachment(100, -4);
		feedUrl.setLayoutData(data);

			// rss bullet and text

		data = new FormData();
		data.top = new FormAttachment(cSearchInput,15);
		data.left = new FormAttachment(0);
		rssBullet.setLayoutData(data);

		data = new FormData();
		data.top = new FormAttachment(rssBullet,-3,SWT.TOP);
		data.left = new FormAttachment(rssBullet,5);
		data.right = new FormAttachment(100);
		subTitle2.setLayoutData(data);

			// name + anon check and text

		data = new FormData();
		data.top = new FormAttachment(subsName,0, SWT.CENTER);
		data.left = new FormAttachment(subTitle2, 0, SWT.LEFT );
		subsNameText.setLayoutData(data);

		data = new FormData();
		data.top = new FormAttachment(rssBullet,20);
		data.left	= new FormAttachment(subsNameText, 5, SWT.RIGHT);
		data.right 	= new FormAttachment(50);
		subsName.setLayoutData(data);

		data = new FormData();
		data.top = new FormAttachment(subsName,0, SWT.CENTER);
		data.left = new FormAttachment(subsName, 5, SWT.RIGHT);
		anonCheck.setLayoutData(data);

			// bottom text

		data = new FormData();
		data.top = new FormAttachment(subsName,20);
		data.left = new FormAttachment(0);
		data.right = new FormAttachment(100);
		subTitle3.setLayoutData(data);


		return composite;
	}

	private Composite createCreateTemplateComposite(Composite parent) {
		Composite composite = new Composite(parent,SWT.NONE);


		Label subTitle = new Label(composite,SWT.WRAP);
		
		subTitle.setText(MessageText.getString("Wizard.Subscription.template.subtitle"));
		
		Label templateNameText = new Label(composite,SWT.WRAP);
		templateNameText.setText( MessageText.getString( "TableColumn.header.name" ));

		templateName = new Text(composite, SWT.BORDER);

		templateName.addListener(SWT.Modify, new Listener() {
			@Override
			public void handleEvent(Event event) {
				
				saveButton.setEnabled(!templateName.getText().isEmpty());
			}
		});
		
		FormLayout layout = new FormLayout();
		layout.marginWidth = 50;
		layout.marginTop = 25;
		composite.setLayout(layout);

		FormData data;
		
			// subtitle

		data = new FormData();
		data.top = new FormAttachment(0,0);
		data.left = new FormAttachment(0,0);
		data.right = new FormAttachment(100,0);
		subTitle.setLayoutData(data);

			// name

		data = new FormData();
		data.top = new FormAttachment(subTitle,10);
		data.left = new FormAttachment(0,0);
		templateNameText.setLayoutData(data);

		data = new FormData();
		data.top = new FormAttachment(templateNameText,0, SWT.CENTER );
		data.left	= new FormAttachment(templateNameText, 5, SWT.RIGHT );
		data.right 	= new FormAttachment(50);
		templateName.setLayoutData(data);

		return composite;
	}
	
	private Composite createCreateSearchComposite(Composite parent) {
		Composite composite = new Composite(parent,SWT.NONE);

		Label subTitle1 = new Label(composite,SWT.WRAP);
		subTitle1.setFont(subTitleFont);
		subTitle1.setText(MessageText.getString("Wizard.Subscription.search.subtitle1"));

		Composite cSearchInput = new Composite(composite, SWT.NONE);
		cSearchInput.setLayout(new FormLayout());
		imageLoader.setBackgroundImage(cSearchInput, "search_bg");
		Rectangle imageBounds = cSearchInput.getBackgroundImage().getBounds();

		searchInput = new Text(cSearchInput, SWT.SINGLE);
		FontUtils.fontToWidgetHeight(searchInput);
//		searchInput.setText(MessageText.getString("Wizard.Subscription.search.inputPrompt"));
//		searchInput.setData("visited",new Boolean(false));
//
//		searchInput.addListener(SWT.FocusIn, new Listener() {
//			public void handleEvent(Event arg0) {
//				boolean visited = ((Boolean) searchInput.getData("visited")).booleanValue();
//				if(visited) return;
//				searchInput.setData("visited",new Boolean(true));
//				searchInput.setText("");
//			}
//		});

		searchInput.addListener (SWT.DefaultSelection, searchListener);

		Label subTitle2 = new Label(composite,SWT.WRAP);
		subTitle2.setFont(subTitleFont);
		subTitle2.setText(MessageText.getString("Wizard.Subscription.search.subtitle2"));

		Label checkBullet1 = new Label(composite, SWT.NONE);
		imageLoader.setLabelImage(checkBullet1, "icon_check");
		Label checkBullet2 = new Label(composite, SWT.NONE);
		imageLoader.setLabelImage(checkBullet2, "icon_check");

		Label description1 = new Label(composite,SWT.NONE);
		description1.setText(MessageText.getString("Wizard.Subscription.search.subtitle2.sub1"));
		Label description2 = new Label(composite,SWT.NONE);
		description2.setText(MessageText.getString("Wizard.Subscription.search.subtitle2.sub2"));

		Label subTitle3 = new Label(composite,SWT.WRAP);
		subTitle3.setFont(subTitleFont);
		subTitle3.setText(MessageText.getString("Wizard.Subscription.search.subtitle3"));

		FormLayout layout = new FormLayout();
		layout.marginLeft = 50;
		layout.marginRight = 50;
		layout.marginTop = 25;
		//layout.spacing = 10;
		composite.setLayout(layout);

		FormData data;

		data = new FormData();
		data.top = new FormAttachment(0);
		data.left = new FormAttachment(0);
		data.right = new FormAttachment(100);
		subTitle1.setLayoutData(data);

		data = new FormData();
		data.top = new FormAttachment(subTitle1,5);
		data.left = new FormAttachment(50,-imageBounds.width/2);
		data.width = imageBounds.width;
		data.height = imageBounds.height;
		cSearchInput.setLayoutData(data);

		data = new FormData();
		data.top = new FormAttachment(0, 5);
		data.left = new FormAttachment(0, 45);
		data.right = new FormAttachment(100, -8);
		data.bottom = new FormAttachment(100, -4);
		searchInput.setLayoutData(data);

		data = new FormData();
		data.top = new FormAttachment(cSearchInput,15);
		data.left = new FormAttachment(0);
		data.right = new FormAttachment(100);
		subTitle2.setLayoutData(data);

		data = new FormData();
		data.top = new FormAttachment(subTitle2,5);
		data.left = new FormAttachment(0);
		checkBullet1.setLayoutData(data);

		data = new FormData();
		data.top = new FormAttachment(checkBullet1,5);
		data.left = new FormAttachment(0);
		checkBullet2.setLayoutData(data);

		data = new FormData();
		data.top = new FormAttachment(checkBullet1, 0, SWT.TOP);
		data.left = new FormAttachment(checkBullet1, 5);
		description1.setLayoutData(data);

		data = new FormData();
		data.top = new FormAttachment(checkBullet2, 0, SWT.TOP);
		data.left = new FormAttachment(checkBullet2, 5);
		description2.setLayoutData(data);

		data = new FormData();
		data.top = new FormAttachment(checkBullet2,15);
		data.left = new FormAttachment(0);
		data.right = new FormAttachment(100);
		subTitle3.setLayoutData(data);

		return composite;
	}

	private Composite createAvailableSubscriptionComposite(Composite parent) {
		Composite composite = new Composite(parent, SWT.NONE);

		Label hsep1 = new Label(composite, SWT.SEPARATOR | SWT.HORIZONTAL);
		Label hsep2 = new Label(composite, SWT.SEPARATOR | SWT.HORIZONTAL);

		Label vsep = new Label(composite, SWT.SEPARATOR | SWT.VERTICAL);

		Label subtitle1 = new Label(composite, SWT.NONE);
		Label subtitle2 = new Label(composite, SWT.NONE);
		subtitle1.setFont(subTitleFont);
		subtitle2.setFont(subTitleFont);
		subtitle1.setText(MessageText.getString("Wizard.Subscription.subscribe.library"));
		subtitle2.setText(MessageText.getString("Wizard.Subscription.subscribe.subscriptions"));

		libraryTable = new Table(composite, SWT.FULL_SELECTION | SWT.VIRTUAL | SWT.V_SCROLL | SWT.SINGLE);

		final TableColumn torrentColumn = new TableColumn(libraryTable, SWT.NONE);
		torrentColumn.setWidth(50);

		final Composite compEmpty = new Composite(composite,SWT.NONE);
		if ( !Utils.isDarkAppearanceNative()){
			compEmpty.setBackground(Colors.getSystemColor(display, SWT.COLOR_WHITE));
		}
		FillLayout fl = new FillLayout();
		fl.marginHeight = 15;
		fl.marginWidth = 15;
		compEmpty.setLayout(fl);
		compEmpty.setVisible(false);

		final Link labelEmpty = new Link(compEmpty,SWT.WRAP);
		labelEmpty.setText(MessageText.getString("Wizard.Subscription.subscribe.library.empty"));
		labelEmpty.setFont(subTitleFont);
		labelEmpty.setForeground(ColorCache.getColor(composite.getDisplay(), "#6D6F6E"));

		labelEmpty.addListener(SWT.Selection, new Listener() {
			@Override
			public void handleEvent(Event event) {
				if(event.text != null && (event.text.startsWith("http://") || event.text.startsWith("https://") ) ) {
					Utils.launch(event.text);
				}
			}
		});

		initColumns();

		final Composite cTV = new Composite(composite, SWT.NONE);
		cTV.setLayoutData(Utils.getFilledFormData());
		GridLayout layout = new GridLayout();
		layout.marginHeight = layout.marginWidth = layout.verticalSpacing = layout.horizontalSpacing = 0;
		cTV.setLayout(layout);

		tvSubscriptions = TableViewFactory.createTableViewSWT(Subscription.class,
				TABLE_SUB_WIZ, TABLE_SUB_WIZ, new TableColumnCore[0], "SubWizRank",
				SWT.FULL_SELECTION | SWT.VIRTUAL | SWT.V_SCROLL | SWT.SINGLE);
		tvSubscriptions.setMenuEnabled(false);
		tvSubscriptions.setHeaderVisible(false);
		tvSubscriptions.setRowDefaultHeightEM(1.4f);

		tvSubscriptions.initialize(cTV);

		tvSubscriptions.getComposite().addListener(SWT.Resize, new Listener() {
			@Override
			public void handleEvent(Event event) {
				com.biglybt.pif.ui.tables.TableColumn tcName = tvSubscriptions.getTableColumn("SubWizName");
				com.biglybt.pif.ui.tables.TableColumn tcRank = tvSubscriptions.getTableColumn("SubWizRank");
				Rectangle clientArea = ((Composite) event.widget).getClientArea();
				tcName.setWidthPX(clientArea.width - tcRank.getWidth() - 1);
			}
		});
		tvSubscriptions.addSelectionListener(new TableSelectionListener() {

			@Override
			public void selected(TableRowCore[] row) {
				Utils.execSWTThread(new AERunnable() {
					@Override
					public void runSupport() {
						if (tvSubscriptions.getSelectedRowsSize() == 0) {
							addButton.setEnabled(false);
						} else {
							addButton.setEnabled(true);
							TableRowCore[] rows = tvSubscriptions.getSelectedRows();
							Subscription subscription = (Subscription) rows[0].getDataSource();
							if (subscription.isSubscribed()) {
								addButton.setEnabled(false);
							} else {
								addButton.setEnabled(true);
							}
							addButton.setData("subscription", subscription);
						}
					}
				});
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
				Utils.execSWTThread(new AERunnable() {
					@Override
					public void runSupport() {
						if (tvSubscriptions.getSelectedRowsSize() == 0) {
							addButton.setEnabled(false);
						}
					}
				});
			}

			@Override
			public void defaultSelected(TableRowCore[] rows, int stateMask) {
			}
		}, false);


		UIUpdaterSWT.getInstance().addUpdater(new UIUpdatable() {

			@Override
			public void updateUI() {
				if (tvSubscriptions != null) {
					tvSubscriptions.refreshTable(false);
				}
			}

			@Override
			public String getUpdateUIName() {
				return "SubWiz";
			}
		});

		Listener resizeListener = new Listener() {

			int last_width;

			@Override
			public void handleEvent(Event event) {
				Table table = (Table)event.widget ;
				Rectangle rect = table.getClientArea();
				int width = rect.width - 3;

				if ( width == last_width ){
					return;
				}

				last_width = width;
				int nbColumns = table.getColumnCount();

				if(nbColumns == 1) {
					table.getColumns()[0].setWidth(width);
				}

				((Table)event.widget).update();
			}
		};

		//subscriptionTable.addListener(SWT.Resize , resizeListener);
		libraryTable.addListener(SWT.Resize , resizeListener);

		final Listener selectionListener = new Listener() {
			@Override
			public void handleEvent(Event event) {
				TableItem item = (TableItem) event.item;
				subscriptions = (Subscription[]) item.getData("subscriptions");

				tvSubscriptions.removeDataSources(tvSubscriptions.getDataSources().toArray(new Subscription[0]));
				if(subscriptions != null) {
					tvSubscriptions.addDataSources(subscriptions);
				}
				tvSubscriptions.processDataSourceQueueSync();

				addButton.setEnabled(false);
				addButton.setData("subscription",null);
				tvSubscriptions.setSelectedRows(new TableRowCore[0]);
				if (subscriptions != null && subscriptions.length > 0) {
					TableRowCore row = tvSubscriptions.getRow(subscriptions[0]);
					if (row != null) {
						row.setSelected(true);
					}
				}
			}
		};

		libraryTable.addListener(SWT.Selection, selectionListener);

		if(availableSubscriptions != null) {
			libraryTable.addListener(SWT.SetData, new Listener() {
				@Override
				public void handleEvent(Event event) {
					  TableItem item = (TableItem) event.item;
			          int index = libraryTable.indexOf (item);

			          SubscriptionDownloadDetails subInfo = availableSubscriptions[index];
			          item.setText (subInfo.getDownload().getDisplayName());
			          item.setData("subscriptions",subInfo.getSubscriptions());
			          boolean isSubscribed = false;
			          Subscription[] subs = subInfo.getSubscriptions();
			          for(int i = 0 ; i < subs.length ; i++) {
			        	  if(subs[i].isSubscribed()) isSubscribed = true;
			          }
			          if(isSubscribed) {
			        	  item.setForeground(Colors.getSystemColor(display, SWT.COLOR_GRAY));
			          }

			          if(subInfo.getDownload() == download) {
			        	  libraryTable.setSelection(item);
			        	  selectionListener.handleEvent(event);
			          }
			          if(index == 0 && download == null) {
			        	  libraryTable.setSelection(item);
			        	  selectionListener.handleEvent(event);
			          }
			          if(libraryTable.getSelectionIndex() == index) {
			        	  //If the item was already selected and we got the SetData afterwards, then let's populate the
			        	  //subscriptionsTable
			        	  selectionListener.handleEvent(event);
			          }
				}
			});

			libraryTable.setItemCount(availableSubscriptions.length);
			if(availableSubscriptions.length == 0) {
				libraryTable.setVisible(false);
				compEmpty.setVisible(true);
			}
		} else {
			//Test code
			libraryTable.addListener(SWT.SetData, new Listener() {
				@Override
				public void handleEvent(Event event) {
					  TableItem item = (TableItem) event.item;
			          int index = libraryTable.indexOf (item);
			          item.setText ("test " + index);
				}
			});

			libraryTable.setItemCount(20);
		}

		addButton.setEnabled(false);
		addButton.setData("subscription",null);


		//final Image rssIcon = imageLoader.getImage("icon_rss");

		libraryTable.addListener(SWT.MeasureItem, new Listener() {
			@Override
			public void handleEvent(Event event) {
				event.height = 20;
			}
		});

		FormLayout formLayout = new FormLayout();
		composite.setLayout(formLayout);

		FormData data;

		data = new FormData();
		data.top = new FormAttachment(0, 0);
		data.left = new FormAttachment(40, 0);
		data.bottom = new FormAttachment(100, 0);
		vsep.setLayoutData(data);

		data = new FormData();
		data.top = new FormAttachment(0, 5);
		data.right = new FormAttachment(vsep, 0);
		data.left = new FormAttachment(0, 5);
		subtitle1.setLayoutData(data);

		data = new FormData();
		data.top = new FormAttachment(0, 5);
		data.left = new FormAttachment(vsep, 5);
		data.right = new FormAttachment(100, 0);
		subtitle2.setLayoutData(data);

		data = new FormData();
		data.top = new FormAttachment(subtitle1, 5);
		data.right = new FormAttachment(vsep, 0);
		data.left = new FormAttachment(0, 0);
		hsep1.setLayoutData(data);

		data = new FormData();
		data.top = new FormAttachment(subtitle2, 5);
		data.left = new FormAttachment(vsep, -1);
		data.right = new FormAttachment(100, 0);
		hsep2.setLayoutData(data);

		data = new FormData();
		data.top = new FormAttachment(hsep1, 0);
		data.right = new FormAttachment(vsep, 0);
		data.left = new FormAttachment(0, 0);
		data.bottom = new FormAttachment(100, 0);

		if(availableSubscriptions != null && availableSubscriptions.length > 0) {
			libraryTable.setLayoutData(data);
		} else {
			// hack: dispose libraryTable as it's not needed and draws over controls
			//       (makes a white box covering text).  Would be smarter to not
			//       create the libraryTable at all..
			libraryTable.dispose();
			cancelButton.setFocus();
			shell.setDefaultButton(cancelButton);
			compEmpty.setLayoutData(data);
		}

		data = new FormData();
		data.top = new FormAttachment(hsep2, 0);
		data.left = new FormAttachment(vsep, 0);
		data.right = new FormAttachment(100, 0);
		data.bottom = new FormAttachment(100, 0);
		cTV.setLayoutData(data);


		return composite;
	}

	private static void initColumns() {
		if (columnsAdded) {
			return;
		}
		columnsAdded = true;
		UIManager uiManager = PluginInitializer.getDefaultInterface().getUIManager();
		TableManager tableManager = uiManager.getTableManager();
		tableManager.registerColumn(Subscription.class, "SubWizName",
				new TableColumnCreationListener() {
					private Image rssIcon;

					@Override
					public void tableColumnCreated(
							com.biglybt.pif.ui.tables.TableColumn column) {

							// this'll get triggered for the Subscriptions Overview table too - easiest fix is to default to hidden there

						column.setVisible( column.getTableID().equals( "SubscriptionWizard" ));
						ImageLoader imageLoader = ImageLoader.getInstance();
						rssIcon = imageLoader.getImage("icon_rss");

						column.addCellAddedListener(new TableCellAddedListener() {
							@Override
							public void cellAdded(TableCell cell) {
								Subscription sub = (Subscription) cell.getDataSource();
								if (sub.isSubscribed()) {
									cell.setForeground(0xa0, 0xa0, 0xa0);
								}
								cell.setText(sub.getName());
								((TableCellSWT) cell).setIcon(rssIcon);
								cell.setToolTip(sub.getNameEx());
							}
						});
					}
				});
		tableManager.registerColumn(Subscription.class, "SubWizRank",
				new TableColumnCreationListener() {
					@Override
					public void tableColumnCreated(
							com.biglybt.pif.ui.tables.TableColumn column) {
						column.setWidthLimits(RANK_COLUMN_WIDTH, RANK_COLUMN_WIDTH);
						column.setVisible(column.getTableID().equals( "SubscriptionWizard" ));	// as above
						column.addCellRefreshListener(new TableCellRefreshListener() {
							@Override
							public void refresh(TableCell cell) {
								Subscription sub = (Subscription) cell.getDataSource();
								cell.setSortValue(sub.getCachedPopularity());
							}
						});
						if (column instanceof TableColumnCore) {
							TableColumnCore columnCore = (TableColumnCore) column;
							columnCore.setSortAscending(false);
							columnCore.addCellOtherListener("SWTPaint",
									new TableCellSWTPaintListener() {
										@Override
										public void cellPaint(GC gc, TableCellSWT cell) {
											Subscription sub = (Subscription) cell.getDataSource();

											Rectangle bounds = cell.getBounds();
											bounds.width -= 5;
											bounds.height -= 7;
											bounds.x += 2;
											bounds.y += 3;
											gc.setBackground(ColorCache.getColor(gc.getDevice(), 255,
													255, 255));
											gc.fillRectangle(bounds);
											gc.setForeground(ColorCache.getColor(gc.getDevice(), 200,
													200, 200));
											gc.drawRectangle(bounds);
											bounds.width -= 2;
											bounds.height -= 2;
											bounds.x += 1;
											bounds.y += 1;

											long popularity = sub.getCachedPopularity();
											//Rank in pixels between 0 and 80
											//0 -> no subscriber
											//80 -> 1000 subscribers

											int rank = 80 * (int) popularity / 1000;
											if (rank > 80)
												rank = 80;
											if (rank < 5)
												rank = 5;

											Rectangle clipping = gc.getClipping();

											bounds.width = rank;
											bounds.height -= 1;
											bounds.x += 1;
											bounds.y += 1;
											Utils.setClipping(gc, bounds);

											ImageLoader imageLoader = ImageLoader.getInstance();
											Image rankingBars = imageLoader.getImage("ranking_bars");
											gc.drawImage(rankingBars, bounds.x, bounds.y);
											imageLoader.releaseImage("ranking_bars");

											Utils.setClipping(gc, clipping);
										}

									});
						}
					}
				});

		TableColumnManager tcm = TableColumnManager.getInstance();
		tcm.setDefaultColumnNames(TABLE_SUB_WIZ, new String[] {
			"SubWizName",
			"SubWizRank",
		});

	}

	private void createFonts() {

		Font baseFont = shell.getFont();

		boldFont = FontUtils.getFontWithStyle(baseFont, SWT.BOLD, 1.0f);
		subTitleFont = FontUtils.getFontWithStyle(baseFont, SWT.BOLD, 1.1f);
		titleFont = FontUtils.getFontWithStyle(baseFont, SWT.BOLD, 1.3f);
	}

	private void populateFooter(Composite footer) {
		yesButton = new Button(footer, SWT.PUSH);
		yesButton.setText(MessageText.getString("Button.yes"));
		yesButton.setFont(boldFont);

		addButton = new Button(footer, SWT.PUSH);
		addButton.setText(MessageText.getString("Button.add"));
		addButton.setFont(boldFont);

		saveButton = new Button(footer, SWT.PUSH);
		saveButton.setText(MessageText.getString("Button.save"));
		saveButton.setEnabled(false);
		saveButton.setFont(boldFont);

		searchButton = new Button(footer, SWT.PUSH);
		searchButton.setText(MessageText.getString("Button.search"));
		searchButton.setFont(boldFont);

		cancelButton = new Button(footer,SWT.PUSH);
		//cancelButton.setText(MessageText.getString("Button.cancel"));

		createButton = new Button(footer,SWT.PUSH);
		createButton.setText(MessageText.getString("Button.createNewSubscription"));

		availableButton = new Button(footer,SWT.PUSH);
		availableButton.setText(MessageText.getString("Button.availableSubscriptions"));

		FormLayout layout = new FormLayout();
		layout.marginHeight = 5;
		layout.marginWidth = 5;
		layout.spacing = 5;

		footer.setLayout(layout);
		FormData data;

		data = new FormData();
		data.right = new FormAttachment(100);
		data.width = 100;

		yesButton.setLayoutData(data);
		addButton.setLayoutData(data);
		searchButton.setLayoutData(data);

		data = new FormData();
		data.right = new FormAttachment(100);
		data.width = 100;
		saveButton.setLayoutData(data);

		data = new FormData();
		data.right = new FormAttachment(saveButton);
		data.width = 100;
		cancelButton.setLayoutData(data);

		data = new FormData();
		data.left = new FormAttachment(0);
		data.width = 175;
		createButton.setLayoutData(data);
		availableButton.setLayoutData(data);


		yesButton.addListener(SWT.Selection, new Listener() {
			@Override
			public void handleEvent(Event event) {
				COConfigurationManager.setParameter("subscriptions.opted_in",true);
				COConfigurationManager.save();
				setMode(MODE_SUBSCRIBE);
			}
		});

		createButton.addListener(SWT.Selection, new Listener() {
			@Override
			public void handleEvent(Event arg0) {
				setMode(MODE_CREATE_SEARCH);
			}
		});

		availableButton.addListener(SWT.Selection, new Listener() {
			@Override
			public void handleEvent(Event arg0) {
				setDefaultAvailableMode();
			}
		});

		cancelButton.addListener(SWT.Selection, new Listener() {
			@Override
			public void handleEvent(Event arg0) {
				shell.close();
			}
		});


		saveListener = new Listener() {
			@Override
			public void handleEvent(Event event) {
				try {
					if ( mode == MODE_CREATE_RSS ){
						String url_str = feedUrl.getText();
						URL	url = new URL(url_str);
	
						Map user_data = new HashMap();
	
						user_data.put( SubscriptionManagerUI.SUB_EDIT_MODE_KEY, Boolean.TRUE);
	
						boolean	anonymous = anonCheck.getSelection();
	
						String	subs_name = subsName.getText().trim();
	
						if ( subs_name.length() == 0 ){
	
							subs_name = url_str;
						}
	
						Subscription subRSS = SubscriptionManagerFactory.getSingleton().createRSS( subs_name, url, SubscriptionHistory.DEFAULT_CHECK_INTERVAL_MINS, anonymous, user_data );
	
						if ( anonymous ){
	
							subRSS.getHistory().setDownloadNetworks( new String[]{ AENetworkClassifier.AT_I2P });
						}
						
						if ( frequency != 0 ){
							
							subRSS.getHistory().setCheckFrequencyMins( frequency );
						}
	
						shell.close();
	
						final String key = "Subscription_" + ByteFormatter.encodeString(subRSS.getPublicKey());
	
						MultipleDocumentInterface mdi = UIFunctionsManager.getUIFunctions().getMDI();
						mdi.showEntryByID(key);
						
					}else{
						
						String	template_name = templateName.getText().trim();
						
						Subscription subTemplate = 
								SubscriptionManagerFactory.getSingleton().createSubscriptionTemplate( template_name );

						shell.close();
						
						final String key = "Subscription_" + ByteFormatter.encodeString(subTemplate.getPublicKey());
	
						MultipleDocumentInterface mdi = UIFunctionsManager.getUIFunctions().getMDI();
						mdi.showEntryByID(key);
					}

				} catch (Throwable e) {

					Debug.out( e );
					
					Utils.reportError( e );
				}
			}
		};

		saveButton.addListener(SWT.Selection, saveListener);

		addButton.addListener(SWT.Selection, new Listener() {
			@Override
			public void handleEvent(Event event) {
				Subscription subscription = (Subscription) addButton.getData("subscription");
				if(subscription != null) {
					subscription.setSubscribed(true);
					subscription.requestAttention();
					shell.close();
				}
			}
		});

		searchListener = new Listener() {
			@Override
			public void handleEvent(Event event) {
				UIFunctionsManager.getUIFunctions().doSearch(searchInput.getText(),true);
				shell.close();
			}
		};

		searchButton.addListener(SWT.Selection, searchListener);

	}

	private void setDefaultAvailableMode() {
		boolean opted_in = COConfigurationManager.getBooleanParameter("subscriptions.opted_in");
		if(!opted_in) {
			setMode(MODE_OPT_IN);
		} else {
			setMode(defaultMode);
		}
	}

	private void setMode(int _mode) {
		addButton.setVisible(false);
		searchButton.setVisible(false);
		saveButton.setVisible(false);
		yesButton.setVisible(false);
		createButton.setVisible(false);
		availableButton.setVisible(false);
		cancelButton.setText(MessageText.getString("Button.cancel"));

		String titleText = TITLE_OPT_IN;

		if ( _mode != MODE_OPT_IN ){
			if ( rss_feed_url != null ){
				_mode = MODE_CREATE_RSS;
				feedUrl.setText( rss_feed_url.toExternalForm());
				rss_feed_url = null;
			}
		}
		mode = _mode;
		
		switch (mode) {
		case MODE_SUBSCRIBE :
			mainLayout.topControl = availableSubscriptionComposite;
			titleText = TITLE_SUBSCRIBE;
			createButton.setVisible(true);
			addButton.setVisible(true);
			shell.setDefaultButton(addButton);
			break;

		case MODE_CREATE_RSS :
			mainLayout.topControl = createComposite;
			createTabFolder.setSelection(createRSSTabItem);
			titleText = TITLE_CREATE_RSS;
			availableButton.setVisible(true);
			saveButton.setVisible(true);
			shell.setDefaultButton(saveButton);
			break;

		case MODE_CREATE_SEARCH :
			mainLayout.topControl = createComposite;
			createTabFolder.setSelection(createSearchTabItem);
			titleText = TITLE_CREATE_RSS;
			availableButton.setVisible(true);
			searchButton.setVisible(true);
			shell.setDefaultButton(searchButton);
			break;

		case MODE_CREATE_TEMPLATE :
			mainLayout.topControl = createComposite;
			createTabFolder.setSelection(createTemplateTabItem);
			titleText = TITLE_CREATE_TEMPLATE;
			saveButton.setVisible(true);
			shell.setDefaultButton(saveButton);
			break;

		case MODE_OPT_IN:
		default:
			mainLayout.topControl = optinComposite;
			cancelButton.setText(MessageText.getString("Button.no"));
			createButton.setVisible(true);
			yesButton.setVisible(true);
			shell.setDefaultButton(yesButton);
			break;
		}

		main.layout(true,true);

		title.setText(titleText);
	}

	public static void main(String args[]) {
		final SubscriptionWizard sw = new SubscriptionWizard();

		while( ! sw.shell.isDisposed()) {
			if(! sw.display.readAndDispatch()) {
				sw.display.sleep();
			}
		}

		sw.display.dispose();
	}
}
