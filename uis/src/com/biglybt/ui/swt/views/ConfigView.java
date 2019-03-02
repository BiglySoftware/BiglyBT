/*
 * Created on 2 juil. 2003
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
 *
 */
package com.biglybt.ui.swt.views;

import java.util.*;

import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.SashForm;
import org.eclipse.swt.custom.ScrolledComposite;
import org.eclipse.swt.custom.StackLayout;
import org.eclipse.swt.events.*;
import org.eclipse.swt.graphics.*;
import org.eclipse.swt.layout.*;
import org.eclipse.swt.widgets.List;
import org.eclipse.swt.widgets.*;

import com.biglybt.core.CoreFactory;
import com.biglybt.core.config.COConfigurationManager;
import com.biglybt.core.internat.MessageText;
import com.biglybt.core.logging.LogEvent;
import com.biglybt.core.logging.LogIDs;
import com.biglybt.core.logging.Logger;
import com.biglybt.core.util.Timer;
import com.biglybt.core.util.*;
import com.biglybt.pifimpl.local.ui.config.ConfigSectionRepository;
import com.biglybt.ui.swt.Messages;
import com.biglybt.ui.swt.SimpleTextEntryWindow;
import com.biglybt.ui.swt.Utils;
import com.biglybt.ui.swt.components.BubbleTextBox;
import com.biglybt.ui.swt.components.LinkLabel;
import com.biglybt.ui.swt.mainwindow.Colors;
import com.biglybt.ui.swt.pif.UISWTConfigSection;
import com.biglybt.ui.swt.pif.UISWTInstance;
import com.biglybt.ui.swt.pif.UISWTView;
import com.biglybt.ui.swt.pif.UISWTViewEvent;
import com.biglybt.ui.swt.pifimpl.UISWTViewCoreEventListenerEx;
import com.biglybt.ui.swt.views.configsections.*;
import com.biglybt.util.JSONUtils;

import com.biglybt.pif.ui.UIInputReceiver;
import com.biglybt.pif.ui.UIInputReceiverListener;
import com.biglybt.pif.ui.config.ConfigSection;

public class ConfigView implements UISWTViewCoreEventListenerEx {
  public static final String VIEW_ID = UISWTInstance.VIEW_CONFIG;
  private static final LogIDs LOGID = LogIDs.GUI;
  public static final String sSectionPrefix = "ConfigView.section.";

  public static final String SELECT_KEY	= "ConfigView.select_key";
  
  final Map<TreeItem, ConfigSection> sections = new HashMap<>();
  // Only access on SWT Thread
  final java.util.List<ConfigSection> sectionsCreated = new ArrayList<>(1);
  Composite cConfig;
  Composite cConfigSection;
  StackLayout layoutConfigSection;
  Label lHeader;
  Label usermodeHint;
  Font headerFont;
  Font filterFoundFont;
  Tree tree;
  ArrayList<ConfigSection> pluginSections;

	private Timer filterDelayTimer;
	private String filterText = "";

	private String startSection;
	private UISWTView swtView;

	public ConfigView() {
  }
	
	public boolean
	isCloneable()
	{
		return( true );
	}

	public UISWTViewCoreEventListenerEx
	getClone()
	{
		return( new ConfigView());
	}
	
	@Override
	public CloneConstructor
	getCloneConstructor()
	{
		return( 
			new CloneConstructor()
			{
				public Class<? extends UISWTViewCoreEventListenerEx>
				getCloneClass()
				{
					return( ConfigView.class );
				}
				
				public java.util.List<Object>
				getParameters()
				{
					return( null );
				}
			});
	}

  private void initialize(final Composite composite) {
  	// need to initalize composite now, since getComposite can
  	// be called at any time
    cConfig = new Composite(composite, SWT.NONE);

    GridLayout configLayout = new GridLayout();
    configLayout.marginHeight = 0;
    configLayout.marginWidth = 0;
    cConfig.setLayout(configLayout);
    GridData gridData = new GridData(GridData.FILL_BOTH);
	  cConfig.setLayoutData(gridData);

    final Label label = new Label(cConfig, SWT.CENTER);
    Messages.setLanguageText(label, "view.waiting.core");
    gridData = new GridData(GridData.FILL_BOTH);
	  label.setLayoutData(gridData);

    // Need to delay initialation until core is done so we can guarantee
    // all config sections are loaded (ie. plugin ones).
    // TODO: Maybe add them on the fly?
    CoreFactory.addCoreRunningListener(core -> Utils.execSWTThread(() -> {
	    _initialize(composite);
	    label.dispose();
	    composite.layout(true, true);
    }));
  }

  private void _initialize(final Composite composite) {

    GridData gridData;
    /*
    /--cConfig-----------------------------------------------------------\
    | ###SashForm#form################################################## |
    | # /--cLeftSide-\ /--cRightSide---------------------------------\ # |
    | # | ##tree#### | | ***cHeader********************************* | # |
    | # | #        # | | * lHeader                    usermodeHint * | # |
    | # | #        # | | ******************************************* | # |
    | # | #        # | | ###Composite cConfigSection################ | # |
    | # | #        # | | #                                         # | # |
    | # | #        # | | #                                         # | # |
    | # | #        # | | #                                         # | # |
    | # | ########## | | #                                         # | # |
    | # |txtFilter X | | ########################################### | # |
    | # \------------/ \---------------------------------------------/ # |
    | ################################################################## |
    |                                                          [Buttons] |
    \--------------------------------------------------------------------/
    */
    try {
      Display d = composite.getDisplay();
      GridLayout configLayout;

      SashForm form = new SashForm(cConfig,SWT.HORIZONTAL);
      gridData = new GridData(GridData.FILL_BOTH);
	    form.setLayoutData(gridData);

      Composite cLeftSide = new Composite(form, SWT.BORDER);
      gridData = new GridData(GridData.FILL_BOTH);
	    cLeftSide.setLayoutData(gridData);

      FormLayout layout = new FormLayout();
      cLeftSide.setLayout(layout);

			BubbleTextBox bubbleTextBox = new BubbleTextBox(cLeftSide, SWT.BORDER
					| SWT.SEARCH | SWT.ICON_SEARCH | SWT.ICON_CANCEL | SWT.SINGLE);
			final Text txtFilter = bubbleTextBox.getTextWidget();
			Composite cFilterArea = bubbleTextBox.getParent();

      txtFilter.setMessage(MessageText.getString("ConfigView.filter"));
      txtFilter.addModifyListener(new ModifyListener() {
      	@Override
	      public void modifyText(ModifyEvent e) {
      		filterTree(txtFilter.getText());
      	}
      });

      tree = new Tree(cLeftSide, SWT.NONE);
      FontData[] fontData = tree.getFont().getFontData();
      fontData[0].setStyle(SWT.BOLD);
      filterFoundFont = new Font(d, fontData);

      FormData formData;

      formData = new FormData();
      formData.bottom = new FormAttachment(100, -5);
			formData.left = new FormAttachment(0, 2);
			formData.right = new FormAttachment(100, -2);
	    cFilterArea.setLayoutData(formData);

      formData = new FormData();
      formData.top = new FormAttachment(0, 0);
      formData.left = new FormAttachment(0,0);
      formData.right = new FormAttachment(100,0);
			formData.bottom = new FormAttachment(cFilterArea, -5);
	    tree.setLayoutData(formData);

      Composite cRightSide = new Composite(form, SWT.NULL);
      configLayout = new GridLayout();
      configLayout.marginHeight = 3;
      configLayout.marginWidth = 0;
      cRightSide.setLayout(configLayout);

      	// Header

      Composite cHeader = new Composite(cRightSide, SWT.BORDER);

      configLayout = new GridLayout();
      configLayout.marginHeight = 3;
      configLayout.marginWidth = 0;
      configLayout.numColumns = 2;
      configLayout.marginRight = 5;
      cHeader.setLayout(configLayout);
      gridData = new GridData(GridData.FILL_HORIZONTAL | GridData.VERTICAL_ALIGN_CENTER);
	    cHeader.setLayoutData(gridData);

      cHeader.setBackground(Colors.getSystemColor(d, SWT.COLOR_LIST_SELECTION));
      cHeader.setForeground(Colors.getSystemColor(d, SWT.COLOR_LIST_SELECTION_TEXT));

      lHeader = new Label(cHeader, SWT.NULL);
      lHeader.setBackground(Colors.getSystemColor(d, SWT.COLOR_LIST_SELECTION));
      lHeader.setForeground(Colors.getSystemColor(d, SWT.COLOR_LIST_SELECTION_TEXT));
      fontData = lHeader.getFont().getFontData();
      fontData[0].setStyle(SWT.BOLD);
      int fontHeight = (int)(fontData[0].getHeight() * 1.2);
      fontData[0].setHeight(fontHeight);
      headerFont = new Font(d, fontData);
      lHeader.setFont(headerFont);
      gridData = new GridData(GridData.VERTICAL_ALIGN_CENTER | GridData.HORIZONTAL_ALIGN_BEGINNING);
	    lHeader.setLayoutData(gridData);


      usermodeHint = new Label(cHeader, SWT.NULL);
      usermodeHint.setBackground(Colors.getSystemColor(d, SWT.COLOR_LIST_SELECTION));
      usermodeHint.setForeground(Colors.getSystemColor(d, SWT.COLOR_LIST_SELECTION_TEXT));
      gridData = new GridData(GridData.VERTICAL_ALIGN_CENTER | GridData.HORIZONTAL_ALIGN_END | GridData.GRAB_HORIZONTAL);
	    usermodeHint.setLayoutData(gridData);

	  Menu headerMenu = new Menu(cHeader.getShell(), SWT.POP_UP );

	  final MenuItem menuShortCut = new MenuItem(headerMenu, SWT.PUSH);
	  Messages.setLanguageText( menuShortCut, "label.set.shortcut" );

	  menuShortCut.addSelectionListener(new SelectionAdapter() {
		  @Override
		  public void widgetSelected(SelectionEvent e) {

			  final TreeItem tree_item = (TreeItem)lHeader.getData( "TreeItem" );

			  if ( tree_item != null ){

				  final String id = (String)tree_item.getData( "ID" );

				  if ( id != null ){

						SimpleTextEntryWindow entryWindow = new SimpleTextEntryWindow(
								"config.dialog.shortcut.title",
								"config.dialog.shortcut.text");
						entryWindow.setPreenteredText(COConfigurationManager.getStringParameter( "config.section.shortcut.key." + id, "" ), false);
						entryWindow.setTextLimit(1);
						entryWindow.prompt(new UIInputReceiverListener() {
							@Override
							public void UIInputReceiverClosed(UIInputReceiver entryWindow) {
								if (!entryWindow.hasSubmittedInput()) {
									return;
								}
								String sReturn = entryWindow.getSubmittedInput();
								if ( sReturn != null ){

									sReturn = sReturn.trim();

									if ( sReturn.length() > 1 ){

										sReturn = sReturn.substring(0,1);
									}

									COConfigurationManager.setParameter( "config.section.shortcut.key." + id, sReturn );

									updateHeader( tree_item );
								}
							}
						});
				  }
			  }
		  }
	  });

	  cHeader.setMenu( headerMenu );
	  lHeader.setMenu( headerMenu );
	  usermodeHint.setMenu( headerMenu );

      // Config Section
      cConfigSection = new Composite(cRightSide, SWT.NULL);
      layoutConfigSection = new StackLayout();
      cConfigSection.setLayout(layoutConfigSection);
      gridData = new GridData(GridData.FILL_BOTH);
      gridData.horizontalIndent = 2;
	    cConfigSection.setLayoutData(gridData);

      form.setWeights(new int[] {20,80});

			tree.addListener(SWT.Selection, e -> {
				if (!(e.widget instanceof Tree)) {
					return;
				}
				Tree tree = (Tree) e.widget;
				//Check that at least an item is selected
				//OSX lets you select nothing in the tree for example when a child is selected
				//and you close its parent.
				if (tree.getSelection().length > 0) {
					showSection(tree.getSelection()[0], false, null);
				}
			});
      // Double click = expand/contract branch
      tree.addListener(SWT.DefaultSelection, e -> {
          TreeItem item = (TreeItem)e.item;
          if (item != null) {
            item.setExpanded(!item.getExpanded());
          }
      });
    } catch (Exception e) {
    	Logger.log(new LogEvent(LOGID, "Error initializing ConfigView", e));
    	return;
    }

    // Add sections
    /** How to add a new section
     * 1) Create a new implementation of ConfigSectionSWT in a new file
     *    (Use the ConfigSectionTMP.java as a template if it's still around)
     * 2) import it into here
     * 3) add it to the internal sections list
     */
    pluginSections = ConfigSectionRepository.getInstance().getList();

    ConfigSection[] internalSections = {
                                         new ConfigSectionMode(),
                                         new ConfigSectionStartShutdown(),
                                         new ConfigSectionBackupRestore(),
                                         new ConfigSectionConnection(),
                                         new ConfigSectionConnectionProxy(),
                                         new ConfigSectionConnectionAdvanced(),
                                         new ConfigSectionConnectionEncryption(),
                                         new ConfigSectionConnectionDNS(),
                                         new ConfigSectionTransfer(),
                                         new ConfigSectionTransferAutoSpeedSelect(),
                                         new ConfigSectionTransferAutoSpeed(),
                                         new ConfigSectionTransferAutoSpeedBeta(),
                                         new ConfigSectionTransferLAN(),
                                         new ConfigSectionFile(),
                                         new ConfigSectionFileMove(),
                                         new ConfigSectionFileTorrents(),
                                         new ConfigSectionFileTorrentsDecoding(),
                                         new ConfigSectionFilePerformance(),
                                         new ConfigSectionInterface(),
                                         new ConfigSectionInterfaceLanguage(),
                                         new ConfigSectionInterfaceStart(),
                                         new ConfigSectionInterfaceDisplay(),
                                         new ConfigSectionInterfaceTables(),
                                         new ConfigSectionInterfaceColor(),
                                         new ConfigSectionInterfaceAlerts(),
                                         new ConfigSectionInterfacePassword(),
                                         new ConfigSectionInterfaceLegacy(),
                                         new ConfigSectionIPFilter(),
                                         new ConfigSectionPlugins(),
                                         new ConfigSectionStats(),
                                         new ConfigSectionTracker(),
                                         new ConfigSectionTrackerClient(),
                                         new ConfigSectionTrackerServer(),
                                         new ConfigSectionSecurity(),
                                         new ConfigSectionSharing(),
                                         new ConfigSectionLogging()
                                        };

    pluginSections.addAll(0, Arrays.asList(internalSections));

		for (ConfigSection section : pluginSections) {

			if (!(section instanceof UISWTConfigSection)) {
				continue;
			}

			String name;
			try {
				name = section.configSectionGetName();
			} catch (Exception e) {
				Logger.log(new LogEvent(LOGID, "A ConfigSection plugin caused an "
						+ "error while trying to call its "
						+ "configSectionGetName function", e));
				name = "Bad Plugin";
			}

			String section_key = sSectionPrefix + name;

			// Plugins don't use prefix by default (via UIManager.createBasicPluginConfigModel).
			// However, when a plugin overrides the name via BasicPluginConfigModel.setLocalizedName(..)
			// it creates a message bundle key with the prefix.  Therefore,
			// key with prefix overrides name key.
			if (!MessageText.keyExists(section_key) && MessageText.keyExists(name)) {
				section_key = name;
			}

			String section_name = MessageText.getString(section_key);

			try {
				TreeItem treeItem;
				String location = section.configSectionGetParentSection();

				if (location == null || location.length() == 0
						|| location.equalsIgnoreCase(ConfigSection.SECTION_ROOT)) {
					//int position = findInsertPointFor(section_name, tree);
					//if ( position == -1 ){
					treeItem = new TreeItem(tree, SWT.NULL);
					// }else{
					//	  treeItem = new TreeItem(tree, SWT.NULL, position);
					//}
				} else {
					TreeItem treeItemFound = findTreeItem(tree, location);
					if (treeItemFound != null) {
						if (location.equalsIgnoreCase(ConfigSection.SECTION_PLUGINS)) {
							// Force ordering by name here.
							int position = findInsertPointFor(section_name, treeItemFound);
							if (position == -1) {
								treeItem = new TreeItem(treeItemFound, SWT.NULL);
							} else {
								treeItem = new TreeItem(treeItemFound, SWT.NULL, position);
							}
						} else {
							treeItem = new TreeItem(treeItemFound, SWT.NULL);
						}
					} else {
						treeItem = new TreeItem(tree, SWT.NULL);
					}
				}

				ScrolledComposite sc = new ScrolledComposite(cConfigSection, SWT.H_SCROLL | SWT.V_SCROLL);
				sc.setExpandHorizontal(true);
				sc.setExpandVertical(true);
				sc.setLayoutData(new GridData(GridData.FILL_BOTH));
				ScrollBar verticalBar = sc.getVerticalBar();
				if (verticalBar != null) {
					verticalBar.setIncrement(16);
				}
				sc.addListener(SWT.Resize,
						(event) -> setupSC((ScrolledComposite) event.widget));

				Messages.setLanguageText(treeItem, section_key);
				treeItem.setData("Panel", sc);
				treeItem.setData("ID", name);
				treeItem.setData("ConfigSectionSWT", section);

				sections.put(treeItem, section);

			} catch (Exception e) {
				Logger.log(new LogEvent(LOGID, "ConfigSection plugin '" + name
						+ "' caused an error", e));
			}
	  }

    final Display d = composite.getDisplay();

    final Listener shortcut_listener =
    	new Listener()
		{
			@Override
			public void
			handleEvent(
				Event e)
			{
				  if ((e.stateMask & ( SWT.MOD1 | SWT.CONTROL )) != 0 || e.keyCode == SWT.COMMAND ){

				  char key = e.character;

				  if (key <= 26 && key > 0){
					  key += 'a' - 1;
				  }

				  if ((e.stateMask & SWT.SHIFT )!= 0 ){
					  key = Character.toUpperCase(key);
				  }
				  if ( !Character.isISOControl( key )){

					  for ( TreeItem ti: sections.keySet()){

						  if ( ti.isDisposed()){
							  continue;
						  }
						  
						  String id = (String)ti.getData( "ID" );

						  if ( id != null ){

							  String shortcut = COConfigurationManager.getStringParameter( "config.section.shortcut.key." + id, "" );

							  if ( shortcut.equals( String.valueOf( key ))){

								  //findFocus( cConfig );

								  selectSection( id, true );

								  e.doit = false;

								  break;
							  }
						  }
					  }
				  }
			  }
			}
		};

    d.addFilter( SWT.KeyDown, shortcut_listener );

		cConfigSection.addDisposeListener(
				e -> d.removeFilter(SWT.KeyDown, shortcut_listener));

    if (composite instanceof Shell) {
    	initApplyCloseButton();
    } else {
    	initSaveButton();
    }

    if (startSection != null) {
    	if (selectSection(startSection,false)) {
    		return;
    	}
    }

    TreeItem selection = getLatestSelection();

    TreeItem[] items = { selection };

    tree.setSelection( items );

    	// setSelection doesn't trigger a SelectionListener, so..

    showSection( selection, false, null );
  }


	private void setupSC(ScrolledComposite sc) {
		if (sc == null) {
			return;
		}
		Composite c = (Composite) sc.getContent();
		if (c != null) {
			Point size1 = c.computeSize(sc.getClientArea().width, SWT.DEFAULT);
			Point size = c.computeSize(SWT.DEFAULT, size1.y);
			sc.setMinSize(size);
		}
		ScrollBar verticalBar = sc.getVerticalBar();
		if (verticalBar != null) {
			verticalBar.setPageIncrement(sc.getSize().y);
		}
	}


  /**
	 * @param text
	 */
	protected void filterTree(String text) {
		filterText = text;
		if (filterDelayTimer != null) {
			filterDelayTimer.destroy();
		}

		filterDelayTimer = new Timer("Filter");
		filterDelayTimer.addEvent(SystemTime.getCurrentTime() + 300,
				new TimerEventPerformer() {
					@Override
					public void perform(TimerEvent event) {
						filterDelayTimer.destroy();
						filterDelayTimer = null;

						Utils.execSWTThread(new AERunnable() {
							@Override
							public void runSupport() {
								if (filterDelayTimer != null) {
									return;
								}
								if (tree == null || tree.isDisposed()){
									return;
								}

								Shell shell = tree.getShell();
								if (shell != null) {
									shell.setCursor(shell.getDisplay().getSystemCursor(
											SWT.CURSOR_WAIT));
								}
								try {
									ArrayList<TreeItem> foundItems = new ArrayList<>();
									TreeItem[] items = tree.getItems();
									try {
										tree.setRedraw(false);
										for (int i = 0; i < items.length; i++) {
											items[i].setExpanded(false);
										}

										filterTree(items, filterText, foundItems);
									} finally {
										tree.setRedraw(true);
									}
								} finally {
									if (shell != null) {
										shell.setCursor(null);
									}
									TreeItem[] selection = tree.getSelection();
									if (selection != null && selection.length > 0) {
										showSection(selection[0],false, null);
									}
								}
							}
						});
					}
				});
	}

	protected void filterTree(TreeItem[] items, String text,
			ArrayList<TreeItem> foundItems) {
		text = text.toLowerCase();
		for (int i = 0; i < items.length; i++) {
			ensureSectionBuilt(items[i], false);
			ScrolledComposite composite = (ScrolledComposite) items[i].getData("Panel");

			if (text.length() > 0
					&& (items[i].getText().toLowerCase().contains(text) || compositeHasText(
							composite, text))) {
				foundItems.add(items[i]);

				ensureExpandedTo(items[i]);
				items[i].setFont(filterFoundFont);
			} else {
				items[i].setFont(null);
			}
			filterTree(items[i].getItems(), text, foundItems);
		}
	}

	private void ensureExpandedTo(TreeItem item) {
    TreeItem itemParent = item.getParentItem();
  	if (itemParent != null) {
  		itemParent.setExpanded(true);
  		ensureExpandedTo(itemParent);
  	}
	}

	/**
	 * @param composite
	 * @param text
	 * @return
	 */
	private boolean compositeHasText(Composite composite, String text) {
		Control[] children = composite.getChildren();

		for (Control child : children) {
			if (child instanceof Label) {
				if (((Label) child).getText().toLowerCase().contains(text)) {
					return true;
				}
			} else if (child instanceof Group) {
				if (((Group) child).getText().toLowerCase().contains(text)) {
					return true;
				}
			} else if (child instanceof Button) {
				if (((Button) child).getText().toLowerCase().contains(text)) {
					return true;
				}
			} else if (child instanceof List) {
				String[] items = ((List) child).getItems();
				for (String item : items) {
					if (item.toLowerCase().contains(text)) {
						return true;
					}
				}
			} else if (child instanceof Combo) {
				String[] items = ((Combo) child).getItems();
				for (String item : items) {
					if (item.toLowerCase().contains(text)) {
						return true;
					}
				}
			}

			if (child instanceof Composite) {
				if (compositeHasText((Composite) child, text)) {
					return true;
				}
			}
		}

		return false;
	}

	private void
	saveLatestSelection(
		TreeItem	item )
	{
		String path = "";

		while( item != null ){

			path = item.getText() + (path.length()==0?"":("$" + path ));

			item = item.getParentItem();
		}

		COConfigurationManager.setParameter( "ConfigView.section.last.selection", path );
	}

	private TreeItem
	getLatestSelection()
	{
		String path = COConfigurationManager.getStringParameter( "ConfigView.section.last.selection", "" );

		String[] bits = path.split( "\\$" );

		TreeItem[]	items = tree.getItems();

		TreeItem current = null;

		boolean	located = false;

		for ( int i=0;i<bits.length;i++ ){

			String bit = bits[i];

			boolean found = false;

			for ( int j=0;j<items.length;j++ ){

				if ( items[j].getText().equals( bit )){

					current = items[j];

					items = current.getItems();

					found = true;

					if ( i == bits.length - 1 ){

						located = true;
					}

					break;
				}
			}

			if ( !found ){

				break;
			}
		}

		TreeItem result = located?current:tree.getItems()[0];

		return( result );
	}

	private void
	showSection(
		TreeItem 	section,
		boolean		focus,
		Map			options )
	{
		saveLatestSelection( section );

		ScrolledComposite item = (ScrolledComposite)section.getData("Panel");

		if (item != null) {

			ensureSectionBuilt(section, true);

			layoutConfigSection.topControl = item;

			setupSC(item);

			cConfigSection.layout();

			updateHeader(section);

			if ( options != null ){
				
				String select = (String)options.get( "select" );
				
				if ( select != null ){
					
					Control hit = hilightText2( item, select );
					
					if ( hit != null ){
						
						Utils.execSWTThreadLater(
							1,
							new Runnable()
							{
								public void
								run()
								{
									Rectangle itemRect = item.getDisplay().map( hit.getParent(), item, hit.getBounds());
																		
									Point origin = item.getOrigin();
																		
									origin.y = itemRect.y;
									
									item.setOrigin(origin);
								}
							});
					}
				}
			}
			if ( focus ){
				layoutConfigSection.topControl.traverse( SWT.TRAVERSE_TAB_NEXT);
			}
		}
	}

	private void hilightText(Composite c, String text) {
		Control[] children = c.getChildren();
		for (Control child : children) {
			if (child instanceof Composite) {
				hilightText((Composite) child, text);
			}

			if (child instanceof Label) {
				if (((Label) child).getText().toLowerCase().contains(text)) {
					hilightControl(child,text,true);
				}
			} else if (child instanceof Group) {
				if (((Group) child).getText().toLowerCase().contains(text)) {
					hilightControl(child,text,true);
				}
			} else if (child instanceof Button) {
				if (((Button) child).getText().toLowerCase().contains(text)) {
					hilightControl(child,text,true);
				}
			} else if (child instanceof List) {
				String[] items = ((List)child).getItems();
				for (String item : items) {
					if (item.toLowerCase().contains(text)) {
						hilightControl(child,text,true);
						break;
					}
				}
			} else if (child instanceof Combo) {
				String[] items = ((Combo)child).getItems();
				for (String item : items) {
					if (item.toLowerCase().contains(text)) {
						hilightControl(child,text,true);
						break;
					}
				}
			}

		}
	}
	
	private Control hilightText2(Composite c, String select) {
		
		Control first_control 	= null;
		
		Control[] children = c.getChildren();
		for (Control child : children) {
			if (child instanceof Composite) {
				Control x = hilightText2((Composite) child, select);
				if ( x != null ){
					if ( first_control == null ){
						first_control = x;
					}
				}
			}

			String select_key = (String)child.getData( SELECT_KEY );
			
			if ( select_key != null && select.equals( select_key )){
			
				if ( first_control == null ){
					
					first_control = child;
				}
								
				hilightControl( child, select, false);
			}
		}
		
		return( first_control );
	}

	/**
	 * @param child
	 *
	 * @since 4.5.1.1
	 */
	private void hilightControl(Control child, String text, boolean type1 ) {
		child.setFont(headerFont);
		
		if ( Utils.isGTK3 ){

				// problem with checkbox/radio controls not supporting setting foreground text color
				// so use alternative 
			
			Composite parent = child.getParent();
			
			parent.addPaintListener(
				new PaintListener(){
					
					@Override
					public void paintControl(PaintEvent e){
						GC gc = e.gc;
						
						gc.setAdvanced(true);
						gc.setAntialias(SWT.ON);
								
						Point pp = parent.toDisplay(0, 0);
						Point cp = child.toDisplay(0, 0 );
						
						Rectangle bounds = child.getBounds();
						
						
						int	width 	= bounds.width;
						int height	= bounds.height;
						
						gc.setForeground(Colors.fadedRed );
						
						gc.drawRectangle( cp.x-pp.x-1, cp.y-pp.y-1, width+2, height+2 );						
					}
				});
					
			Object ld = child.getLayoutData();
			
			if ( ld instanceof GridData || ld == null ){
				
				Point size = child.computeSize( SWT.DEFAULT,  SWT.DEFAULT );

				GridData gd = ld == null?new GridData():(GridData)ld;
				
				gd.minimumHeight = gd.heightHint = size.y + 2;
				gd.minimumWidth = gd.widthHint = size.x + 2;
				
				child.setLayoutData( gd );
			}
		}else{
			child.setBackground(Colors.getSystemColor(child.getDisplay(), SWT.COLOR_INFO_BACKGROUND));
			child.setForeground(Colors.getSystemColor(child.getDisplay(), SWT.COLOR_INFO_FOREGROUND));
		}
		
		if ( child instanceof Composite ){
			
			if ( type1 ){
				hilightText((Composite)child, text );
			}else{
				hilightText2((Composite)child, text );
			}
		}
	}

	private void ensureSectionBuilt(TreeItem treeSection, boolean recreateIfAlreadyThere) {
    ScrolledComposite item = (ScrolledComposite)treeSection.getData("Panel");

		if (item == null) {
			return;
		}

		ConfigSection configSection = (ConfigSection)treeSection.getData("ConfigSectionSWT");

		if (configSection != null) {

		  Control previous = item.getContent();
		  if (previous instanceof Composite) {
			  if (!recreateIfAlreadyThere) {
				  return;
			  }
			  configSection.configSectionDelete();
		    sectionsCreated.remove(configSection);
		    Utils.disposeComposite((Composite)previous,true);
		  }

		  Composite c = ((UISWTConfigSection)configSection).configSectionCreate(item);

			  // we need to do this here as, on GTK at least, leaving it until later causes check/radio-boxes not
			  // to layout correctly after their font is changed

		  if (filterText != null && filterText.length() > 0) {
			    hilightText(c, filterText);

		  }

		  sectionsCreated.add(configSection);

		  item.setContent(c);
		}
	}

  private void updateHeader(TreeItem section) {
		if (section == null)
			return;

		lHeader.setData( "TreeItem", section );

		int userMode = COConfigurationManager.getIntParameter("User Mode");
		int maxUsermode = 0;
		try
		{
			ConfigSection sect = sections.get(section);
			if (sect instanceof UISWTConfigSection)
			{
				maxUsermode = ((UISWTConfigSection) sect).maxUserMode();
			}
		} catch (Error e)
		{
			//Debug.printStackTrace(e);
		}

		String id = (String)section.getData( "ID" );

		String shortcut = COConfigurationManager.getStringParameter( "config.section.shortcut.key." + id, null );

		String sc_text;

		if ( shortcut != null && shortcut.length() > 0 ){

			sc_text = "      (Ctrl+" + shortcut.charAt(0) + ")";
		}else{

			sc_text = "";
		}

		if (userMode < maxUsermode)
			Messages.setLanguageText(usermodeHint, "ConfigView.higher.mode.available");
		else
			usermodeHint.setText("");

		String sHeader = section.getText();

		section = section.getParentItem();
		while (section != null)
		{
			sHeader = section.getText() + " : " + sHeader;
			section = section.getParentItem();
		}
		lHeader.setText(" " + sHeader.replaceAll("&", "&&") + sc_text );
		lHeader.getParent().layout(true, true);
	}


  private static Comparator<Object> insert_point_comparator = new Comparator<Object>() {

	  private String asString(Object o) {
		  if (o instanceof String) {
			  return (String)o;
		  }
		  else if (o instanceof TreeItem) {
			  return ((TreeItem)o).getText();
		  }
		  else {
				throw new ClassCastException("object is not String or TreeItem: "
						+ (o == null ? o : o.getClass().getName()));
		  }
	  }

	  @Override
	  public int compare(Object o1, Object o2) {
		  int result = String.CASE_INSENSITIVE_ORDER.compare(asString(o1), asString(o2));
		  return result;
	  }
  };

  private static int findInsertPointFor(String name, Object structure) {
		TreeItem[] children;
		if (structure instanceof Tree) {
			children = ((Tree) structure).getItems();
		} else if (structure instanceof TreeItem) {
			children = ((TreeItem) structure).getItems();
		} else {
			return -1;
		}
		if (children.length == 0) {
			return -1;
		}
	  int result =  Arrays.binarySearch(children, name, insert_point_comparator);
	  if (result > 0) {return result;}
	  result = -(result+1);
	  if (result == children.length) {
		  result = -1;
	  }
	  return result;
  }

  public TreeItem findTreeItem(String ID) {
  	return findTreeItem((Tree)null, ID);
  }

  private TreeItem findTreeItem(Tree tree, String ID) {
  	if (tree == null) {
  		tree = this.tree;
  	}
  	if (tree == null) {
  		return null;
  	}
    TreeItem[] items = tree.getItems();
	  for (TreeItem item : items) {
		  String itemID = (String) item.getData("ID");
		  if (itemID != null && itemID.equalsIgnoreCase(ID)) {
			  return item;
		  }
		  TreeItem itemFound = findTreeItem(item, ID);
		  if (itemFound != null)
			  return itemFound;
	  }
	 return null;
  }

  private TreeItem findTreeItem(TreeItem item, String ID) {
  	if (item == null || item.isDisposed()) {
  		return null;
	  }
    TreeItem[] subItems = item.getItems();
	  for (TreeItem subItem : subItems) {
		  String itemID = (String) subItem.getData("ID");
		  if (itemID != null && itemID.equalsIgnoreCase(ID)) {
			  return subItem;
		  }

		  TreeItem itemFound = findTreeItem(subItem, ID);
		  if (itemFound != null) {
			  return itemFound;
		  }
	  }
    return null;
  }

  private void initSaveButton() {
	  Composite cButtons = new Composite(cConfig, SWT.NONE);
	  GridLayout gridLayout = new GridLayout();
	  gridLayout.verticalSpacing = gridLayout.marginHeight = 0;
	  //gridLayout.horizontalSpacing = gridLayout.verticalSpacing = gridLayout.marginHeight = gridLayout.marginWidth = 0;
	  gridLayout.numColumns = 2;
	  cButtons.setLayout(gridLayout);
	  cButtons.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
	  GridData gridData;
	  
	  LinkLabel ll = new LinkLabel( cButtons, "label.help", Constants.URL_WIKI );
	    
	  gridData = new GridData(GridData.FILL_HORIZONTAL);
	  ll.getlabel().setLayoutData( gridData );
	  
	  final Button save = new Button(cButtons, SWT.PUSH);
	  Messages.setLanguageText(save, "ConfigView.button.save"); //$NON-NLS-1$
	  gridData = new GridData(GridData.HORIZONTAL_ALIGN_BEGINNING);
	  gridData.horizontalSpan = 1;
	  gridData.widthHint = 80;
	  save.setLayoutData(gridData);

	  save.addSelectionListener(new SelectionAdapter() {
		  @Override
		  public void widgetSelected(SelectionEvent event) {
			  // force focusout on osx
			  save.setFocus();
			  save();
		  }
	  });
  }

  private void initApplyCloseButton() {
  	Composite cButtons = new Composite(cConfig, SWT.NONE);
  	GridLayout gridLayout = new GridLayout();
		gridLayout.horizontalSpacing = gridLayout.verticalSpacing = gridLayout.marginHeight = gridLayout.marginWidth = 0;
		gridLayout.numColumns = 2;
		cButtons.setLayout(gridLayout);
		cButtons.setLayoutData(new GridData(GridData.HORIZONTAL_ALIGN_END));

    GridData gridData;
    final Button apply = new Button(cButtons, SWT.PUSH);
    Messages.setLanguageText(apply, "Button.apply");
    gridData = new GridData(GridData.HORIZONTAL_ALIGN_BEGINNING);
    gridData.widthHint = 80;
		apply.setLayoutData(gridData);

    apply.addSelectionListener(new SelectionAdapter() {
      @Override
      public void widgetSelected(SelectionEvent event) {
				// force focusout on osx
				apply.setFocus();
				save();
			}
		});

    final Button close = new Button(cButtons, SWT.PUSH);
    Messages.setLanguageText(close, "Button.close");
    gridData = new GridData(GridData.HORIZONTAL_ALIGN_BEGINNING);
    gridData.widthHint = 80;
		close.setLayoutData(gridData);

    close.addSelectionListener(new SelectionAdapter() {
      @Override
      public void widgetSelected(SelectionEvent event) {
				// force focusout on osx
				apply.setFocus();
				save();
				apply.getShell().dispose();
			}
		});
  }

  private Composite getComposite() {
    return cConfig;
  }

  private void updateLanguage() {
  	if (tree == null || tree.isDisposed()) {
  		return;
	  }
	  TreeItem[] selection = tree.getSelection();
	  if (selection != null && selection.length > 0) {
		  updateHeader(selection[0]);
	  }
	  if (swtView != null) {
    	swtView.setTitle(getFullTitle());
    }
  }

  private void delete() {
  	for (ConfigSection section : sectionsCreated) {
    	try {
    		section.configSectionDelete();
    	} catch (Exception e) {
    		Debug.out("Error while deleting config section", e);
    	}
    }
  	sectionsCreated.clear();
  	if ( pluginSections != null ){
  		pluginSections.clear();
  	}
	  if (tree != null && !tree.isDisposed()) {
		  TreeItem[] items = tree.getItems();
		  if (items != null) {
			  for (TreeItem item : items) {
				  Composite c = (Composite) item.getData("Panel");
				  Utils.disposeComposite(c);
				  item.setData("Panel", null);
				  item.setData("ConfigSectionSWT", null);
			  }
		  }
	  }
    Utils.disposeComposite(cConfig);

  	Utils.disposeSWTObjects(headerFont, filterFoundFont);
		headerFont = null;
		filterFoundFont = null;
  }

  private String getFullTitle() {
  	/*
  	 * Using resolveLocalizationKey because there are different version for Classic vs. Vuze
  	 */
    return MessageText.getString("ConfigView.title.full"); //$NON-NLS-1$
  }

  public boolean selectSection(String id, boolean focus) {
  	if (tree == null || tree.isDisposed()) {
  		return false;
	  }

	  Map args = null;

	  if ( id != null ){
		  int	pos = id.indexOf( '{' );
	
		  if ( pos != -1 ){
	
			  String json_args = id.substring( pos );
	
			  args = JSONUtils.decodeJSON( json_args );
	
			  id = id.substring( 0, pos );
		  }
	  }
	  
	  TreeItem ti = findTreeItem(id);
	  if (ti == null)
		  return false;
	  tree.setSelection(new TreeItem[] { ti });
	  showSection(ti, focus, args);
	  return true;
	}

	public void save() {
		COConfigurationManager.setParameter("updated", 1);
		COConfigurationManager.save();

		if (null != pluginSections) {
			for (ConfigSection section : pluginSections) {
				section.configSectionSave();
			}
		}
	}

  private void dataSourceChanged(Object newDataSource) {

  	if (newDataSource instanceof String) {
	  	startSection = (String) newDataSource;
			Utils.execSWTThread(() -> selectSection(startSection, false));
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
      	updateLanguage();
        break;

      case UISWTViewEvent.TYPE_DATASOURCE_CHANGED:
      	dataSourceChanged(event.getData());
        break;

      case UISWTViewEvent.TYPE_FOCUSGAINED:
      	break;

      case UISWTViewEvent.TYPE_REFRESH:
        break;
    }

    return true;
  }

}
