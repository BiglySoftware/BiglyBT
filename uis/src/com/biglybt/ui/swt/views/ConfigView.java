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

import java.util.List;

import static com.biglybt.core.config.ConfigKeys.ICFG_USER_MODE;

import java.util.*;

import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.*;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.graphics.*;
import org.eclipse.swt.layout.*;
import org.eclipse.swt.widgets.*;

import com.biglybt.core.CoreFactory;
import com.biglybt.core.config.COConfigurationManager;
import com.biglybt.core.config.ConfigKeys;
import com.biglybt.core.internat.MessageText;
import com.biglybt.core.logging.LogEvent;
import com.biglybt.core.logging.LogIDs;
import com.biglybt.core.logging.Logger;
import com.biglybt.core.util.Constants;
import com.biglybt.core.util.Debug;
import com.biglybt.core.util.SystemTime;
import com.biglybt.core.util.Timer;
import com.biglybt.pifimpl.local.ui.config.*;
import com.biglybt.ui.config.*;
import com.biglybt.ui.swt.Messages;
import com.biglybt.ui.swt.SimpleTextEntryWindow;
import com.biglybt.ui.swt.Utils;
import com.biglybt.ui.swt.components.BubbleTextBox;
import com.biglybt.ui.swt.components.LinkLabel;
import com.biglybt.ui.swt.config.*;
import com.biglybt.ui.swt.config.actionperformer.DualChangeSelectionActionPerformer;
import com.biglybt.ui.swt.config.actionperformer.IAdditionalActionPerformer;
import com.biglybt.ui.swt.mainwindow.Colors;
import com.biglybt.ui.swt.pif.UISWTParameterContext;
import com.biglybt.ui.swt.pif.UISWTView;
import com.biglybt.ui.swt.pif.UISWTViewEvent;
import com.biglybt.ui.swt.pifimpl.MultiParameterImplListenerSWT;
import com.biglybt.ui.swt.pifimpl.UISWTViewCoreEventListener;
import com.biglybt.ui.swt.utils.FontUtils;
import com.biglybt.ui.swt.views.configsections.*;
import com.biglybt.util.JSONUtils;

import com.biglybt.pif.ui.UIInputReceiver;
import com.biglybt.pif.ui.config.ActionParameter;
import com.biglybt.pif.ui.config.ConfigSection;
import com.biglybt.pif.ui.config.IntListParameter;
import com.biglybt.pif.ui.config.Parameter;

public class ConfigView implements UISWTViewCoreEventListener, ConfigSectionRepository.ConfigSectionRepositoryListener {
	private static final LogIDs LOGID = LogIDs.GUI;

	// For highlighting via showSeection.  option map contains "select" key with "value".
	// (multiple) config widgets can setData(SELECT_KEY, "value"), which means they will be
	// highlighted
	public static final String SELECT_KEY	= "ConfigView.select_key";
	public static final String TREEITEMDATA_CONFIGSECTION = "ConfigSectionSWT";
	public static final String TREEITEMDATA_PANEL = "Panel";
	public static final String TREEITEMDATA_ITEM = "ConfigView.TreeItem";
	
	private static Font groupFont = null;

	
	public static String
	getSectionContext(
			Control	c )
	{
		String groups = "";
		
		while( c != null ){

			if ( c instanceof Group ){
				
				String text = ((Group)c).getText().trim();
				
				if ( !text.isEmpty()){
					
					groups = " [" + text + groups +  "]";
				}
			}
			TreeItem item = (TreeItem)c.getData( TREEITEMDATA_ITEM );

			if ( item != null ){

				String	str = groups;
				
				while( item != null ){
					
					str = "->" + item.getText() + str;
					
					item = item.getParentItem();
				}
				
				return( MessageText.getString( "ConfigView.title.full" ) + str );
			}

			c = c.getParent();
		}

		return( null );
	}
	  
	  
	final Map<TreeItem, BaseConfigSection> sections = new HashMap<>();
  // Only access on SWT Thread
	final List<BaseConfigSection> sectionsCreated = new ArrayList<>(1);
  Composite cConfig;
  Composite cConfigSection;
  StackLayout layoutConfigSection;
  Label lHeader;
  Label usermodeHint;
  Font headerFont;
  Font filterFoundFont;
  Tree tree;
	ArrayList<BaseConfigSection> pluginSections;

	private Timer filterDelayTimer;
	private String filterText = "";

	private String startSection;
	private UISWTView swtView;

	ConfigSectionRebuildRunner rebuildSectionRunnable = configSection -> Utils.execSWTThread(
			() -> ensureSectionBuilt(
					findTreeItem(configSection.getConfigSectionID()), true));

	
	public ConfigView() {
  }

  private void initialize(final Composite composite) {
  	// need to initialize composite now, since getComposite can
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
	    _initialize(composite instanceof Shell);
	    label.dispose();
	    composite.layout(true, true);
    }));
  }

  private void _initialize( boolean applyClose ) {

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
      Display d = cConfig.getDisplay();
      GridLayout configLayout;

	  SashForm form = new SashForm(cConfig,SWT.HORIZONTAL);
      gridData = new GridData(GridData.FILL_BOTH);
	    form.setLayoutData(gridData);

      Composite cLeftSide = Utils.createSkinnedComposite( form, SWT.BORDER, new GridData(GridData.FILL_BOTH));

      FormLayout layout = new FormLayout();
      cLeftSide.setLayout(layout);

      BubbleTextBox bubbleTextBox = new BubbleTextBox(cLeftSide, SWT.BORDER
    		  | SWT.SEARCH | SWT.ICON_SEARCH | SWT.ICON_CANCEL | SWT.SINGLE);
      final Text txtFilter = bubbleTextBox.getTextWidget();
      
      Composite cFilterArea = bubbleTextBox.getMainWidget();

      txtFilter.addModifyListener(e -> filterTree(txtFilter.getText()));

      tree = new Tree(cLeftSide, SWT.NONE);
      FontData[] fontData = tree.getFont().getFontData();
      fontData[0].setStyle(SWT.BOLD);
      filterFoundFont = new Font(d, fontData);

      FormData formData;

      formData = new FormData();
      formData.bottom = new FormAttachment(100, -5);
      formData.left = new FormAttachment(0, 2);
      formData.right = new FormAttachment(100, -2);
      
      bubbleTextBox.setMessageAndLayout( MessageText.getString("ConfigView.filter"), formData );
 
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

      Composite cHeader = Utils.createSkinnedComposite(cRightSide, SWT.BORDER, new GridData(GridData.FILL_HORIZONTAL | GridData.VERTICAL_ALIGN_CENTER));

      configLayout = new GridLayout();
      configLayout.marginHeight = 3;
      configLayout.marginWidth = 0;
      configLayout.numColumns = 2;
      configLayout.marginRight = 5;
      cHeader.setLayout(configLayout);

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

						UIInputReceiver entryWindow = new SimpleTextEntryWindow(
								"config.dialog.shortcut.title",
								"config.dialog.shortcut.text");
						entryWindow.setPreenteredText(COConfigurationManager.getStringParameter( "config.section.shortcut.key." + id, "" ), false);
						entryWindow.setTextLimit(1);
						entryWindow.prompt(ew -> {
							if (!ew.hasSubmittedInput()) {
								return;
							}
							String sReturn = ew.getSubmittedInput();
							if ( sReturn != null ){

								sReturn = sReturn.trim();

								if ( sReturn.length() > 1 ){

									sReturn = sReturn.substring(0,1);
								}

								COConfigurationManager.setParameter( "config.section.shortcut.key." + id, sReturn );

								updateHeader( tree_item );
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

    ConfigSectionRepository.getInstance().addListener( this );
    
		BaseConfigSection[] internalSections = {
                                         new ConfigSectionMode(),
                                         new ConfigSectionStartShutdown(),
                                         new ConfigSectionBackupRestoreSWT(),
                                         new ConfigSectionConnection(),
                                         new ConfigSectionConnectionProxySWT(),
                                         new ConfigSectionConnectionAdvanced(),
                                         new ConfigSectionConnectionEncryption(),
                                         new ConfigSectionConnectionDNS(),
                                         new ConfigSectionTransfer(),
                                         new ConfigSectionTransferAutoSpeedSelect(),
                                         new ConfigSectionTransferAutoSpeedClassic(),
                                         new ConfigSectionTransferAutoSpeedV2(),
                                         new ConfigSectionTransferLAN(),
                                         new ConfigSectionFile(),
                                         new ConfigSectionFileMove(),
                                         new ConfigSectionFileTorrentsSWT(),
                                         new ConfigSectionFileTorrentsDecoding(),
                                         new ConfigSectionFilePerformance(),
                                         new ConfigSectionInterfaceSWT(),
                                         new ConfigSectionInterfaceLanguageSWT(),
                                         new ConfigSectionInterfaceStartSWT(),
                                         new ConfigSectionInterfaceDisplaySWT(),
                                         new ConfigSectionInterfaceTagsSWT(),
                                         new ConfigSectionInterfaceTablesSWT(),
                                         new ConfigSectionInterfaceColorSWT(),
                                         new ConfigSectionInterfaceAlertsSWT(),
                                         new ConfigSectionInterfacePasswordSWT(),
                                         new ConfigSectionInterfaceLegacySWT(),
                                         new ConfigSectionIPFilterSWT(),
                                         new ConfigSectionPluginsSWT(),
                                         new ConfigSectionStats(),
                                         new ConfigSectionTracker(),
                                         new ConfigSectionTrackerClient(),
                                         new ConfigSectionTrackerServerSWT(),
                                         new ConfigSectionSecuritySWT(),
                                         new ConfigSectionSharing(),
                                         new ConfigSectionLogging()
                                        };

    pluginSections.addAll(0, Arrays.asList(internalSections));


    for (BaseConfigSection section : pluginSections){

    	buildSection( section );
    }

    final Display d = cConfig.getDisplay();

		final Listener shortcut_listener = e -> {
			if ((e.stateMask & (SWT.MOD1 | SWT.CONTROL)) == 0 && e.keyCode != SWT.COMMAND) {
				return;
			}

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
		};

    d.addFilter( SWT.KeyDown, shortcut_listener );

		cConfigSection.addDisposeListener(
				e -> d.removeFilter(SWT.KeyDown, shortcut_listener));

    if ( applyClose ) {
    	initApplyCloseButton();
    } else {
    	initSaveButton();
    }

    if (startSection != null) {
    	if (selectSection(startSection,false)) {
    		return;
    	}
    }

    Runnable r = ()->{
	    TreeItem selection = getLatestSelection();
	
	    TreeItem[] items = { selection };
	
	    tree.setSelection( items );
	
	    	// setSelection doesn't trigger a SelectionListener, so..
	
	    showSection( selection, false, null );
    };
    
    if ( Constants.isOSX ){
    	
    		// Catalina (public beta at least) bug whereby scrollbar is borked if we
    		// synchronously attempt to set the visible section
    	
    	Utils.execSWTThreadLater( 250, r );
    	
    }else{
    	
    	r.run();
    }
  }

  private void
  buildSection(
	BaseConfigSection	section )
  {
		section.setRebuildRunner(rebuildSectionRunnable);

		String section_key = section.getSectionNameKey();
		String section_name = MessageText.getString(section_key);

		try {
			TreeItem treeItem;
			String location = section.getParentSectionID();

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
			treeItem.setData(TREEITEMDATA_PANEL, sc);
			treeItem.setData("ID", section.getConfigSectionID());
			treeItem.setData(TREEITEMDATA_CONFIGSECTION, section);
			sc.setData(TREEITEMDATA_ITEM, treeItem);

			sections.put(treeItem, section);

		} catch (Exception e) {
			Logger.log(new LogEvent(LOGID, "ConfigSection plugin '"
					+ section.getConfigSectionID() + "' caused an error", e));
		}
  }
  public void
  sectionAdded(
		 BaseConfigSection section)
  {
	  Utils.execSWTThread(()->{
		  buildSection( section );
	  });
  }
  
  public void
  sectionRemoved(
		 BaseConfigSection section)
  {
	  Utils.execSWTThread(()->{
		  for (Map.Entry<TreeItem, BaseConfigSection> entry: sections.entrySet()){
			  
			  if ( entry.getValue() == section ){
				  
				  TreeItem treeItem = entry.getKey();
				  
				  sections.remove( treeItem );
				  
				  ScrolledComposite composite = (ScrolledComposite)treeItem.getData(TREEITEMDATA_PANEL);
				  
				  if ( composite != null && !composite.isDisposed()){
					
					  composite.dispose();
				  }
				  
				  if ( !treeItem.isDisposed()){
					  
					  treeItem.dispose();
				  }
				  
				  break;
			  }
		  }
	  }); 
  }

	private static void setupSC(ScrolledComposite sc) {
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


	protected void filterTree(String text) {
		filterText = text;
		if (filterDelayTimer != null) {
			filterDelayTimer.destroy();
		}

		filterDelayTimer = new Timer("Filter");
		filterDelayTimer.addEvent(SystemTime.getCurrentTime() + 300, event -> {
			filterDelayTimer.destroy();
			filterDelayTimer = null;

			Utils.execSWTThread(() -> {
				if (filterDelayTimer != null) {
					return;
				}
				if (tree == null || tree.isDisposed()) {
					return;
				}

				Shell shell = tree.getShell();
				if (shell != null) {
					shell.setCursor(shell.getDisplay().getSystemCursor(SWT.CURSOR_WAIT));
				}
				try {
					ArrayList<TreeItem> foundItems = new ArrayList<>();
					TreeItem[] items = tree.getItems();
					try {
						tree.setRedraw(false);
						for (TreeItem item : items) {
							item.setExpanded(false);
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
					if (selection.length > 0) {
						showSection(selection[0], false, null);
					}
				}
			});
		});
	}

	protected void filterTree(TreeItem[] items, String text,
			ArrayList<TreeItem> foundItems) {
		text = text.toLowerCase();
		for (TreeItem item : items) {
			ensureSectionBuilt(item, false);
			ScrolledComposite composite = (ScrolledComposite) item.getData(TREEITEMDATA_PANEL);
			if (composite == null || composite.isDisposed()) {
				continue;
			}

			if (text.length() > 0
					&& (item.getText().toLowerCase().contains(text) || compositeHasText(
					composite, text))) {
				foundItems.add(item);

				ensureExpandedTo(item);
				item.setFont(filterFoundFont);
			} else {
				item.setFont(null);
			}
			filterTree(item.getItems(), text, foundItems);
		}
	}

	private static void ensureExpandedTo(TreeItem item) {
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
	private static boolean compositeHasText(Composite composite, String text) {
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
			} else if (child instanceof org.eclipse.swt.widgets.List) {
				String[] items = ((org.eclipse.swt.widgets.List) child).getItems();
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
			}else if (child instanceof Text) {
				if (((Text) child).getText().toLowerCase().contains(text)) {
					return( true );
				}
			}else if (child instanceof Spinner) {
				if (((Spinner) child).getText().toLowerCase().contains(text)) {
					return( true );
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

	private static void saveLatestSelection(TreeItem item) {
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
		if (tree == null || tree.isDisposed()) {
			return null;
		}

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
		int userMode = COConfigurationManager.getIntParameter(ICFG_USER_MODE);
				
		if ( showSectionSupport( section, focus, options )){
				
			return;
		}
		
		if ( userMode < 2 ){
					
			userMode++;
			
			COConfigurationManager.setParameter(ICFG_USER_MODE, userMode );
			
			Utils.execSWTThreadLater( 250, ()->showSection( section, focus, options ));
		}
	}

	private boolean
	showSectionSupport(
		TreeItem 	section,
		boolean		focus,
		Map			options )
	{
		boolean result = true;
		
		saveLatestSelection( section );

		ScrolledComposite item = (ScrolledComposite)section.getData(TREEITEMDATA_PANEL);

		if (item != null && layoutConfigSection != null && cConfigSection != null) {

			ensureSectionBuilt(section, true);

			layoutConfigSection.topControl = item;

			setupSC(item);

			cConfigSection.layout();

			updateHeader(section);

			if ( options != null ){
				
				String select = (String)options.get( "select" );
				
				if ( select != null ){
					
					Control hit = highlightText2( item, select );
										
					if ( hit != null ){
					
						item.layout( true, true );

						Utils.execSWTThreadLater(
							1,
								() -> {
									Rectangle itemRect = item.getDisplay().map( hit.getParent(), item, hit.getBounds());

									Point origin = item.getOrigin();

									origin.y = itemRect.y;

									item.setOrigin(origin);
								});
						
					}else{
						
						result = false;
					}
				}
			}
			
			if ( focus ){
				
				layoutConfigSection.topControl.traverse( SWT.TRAVERSE_TAB_NEXT);
			}
		}
		
		return( result );
	}

	private void highlightText(Composite c, String text) {
		Control[] children = c.getChildren();
		for (Control child : children) {
			if (child instanceof Composite) {
				highlightText((Composite) child, text);
			}

			if (child instanceof Label) {
				if (((Label) child).getText().toLowerCase().contains(text)) {
					highlightControl(child,text,true);
				}
			} else if (child instanceof Group) {
				if (((Group) child).getText().toLowerCase().contains(text)) {
					highlightControl(child,text,true);
				}
			} else if (child instanceof Button) {
				if (((Button) child).getText().toLowerCase().contains(text)) {
					highlightControl(child,text,true);
				}
			} else if (child instanceof org.eclipse.swt.widgets.List) {
				String[] items = ((org.eclipse.swt.widgets.List)child).getItems();
				for (String item : items) {
					if (item.toLowerCase().contains(text)) {
						highlightControl(child,text,true);
						break;
					}
				}
			} else if (child instanceof Combo) {
				String[] items = ((Combo)child).getItems();
				for (String item : items) {
					if (item.toLowerCase().contains(text)) {
						highlightControl(child,text,true);
						break;
					}
				}
			}else if (child instanceof Text) {
				if (((Text) child).getText().toLowerCase().contains(text)) {
					highlightControl(child,text,true);
				}
			}else if (child instanceof Spinner) {
				if (((Spinner) child).getText().toLowerCase().contains(text)) {
					highlightControl(child,text,true);
				}
			}
		}
	}
	
	private Control highlightText2(Composite c, String select) {
		String cSelectKey = (String)c.getData( SELECT_KEY );
		if (select.equals(cSelectKey)){
			highlightControl(c, "", false);
			return null;
		}

		Control first_control 	= null;
		
		Control[] children = c.getChildren();
		for (Control child : children) {
			if (child instanceof Composite) {
				Control x = highlightText2((Composite) child, select);
				if ( x != null ){
					if ( first_control == null ){
						first_control = x;
					}
				}
			}

			String select_key = (String)child.getData( SELECT_KEY );
			
			if (select.equals(select_key)){
			
				if ( first_control == null ){
					
					first_control = child;
				}
								
				highlightControl( child, select, false);
			}
		}
		
		return( first_control );
	}

	/**
	 * @param child
	 *
	 * @since 4.5.1.1
	 */
	private void highlightControl(Control control, String text, boolean type1 ) {
		control.setFont(headerFont);
		
		if ( Constants.isWindows ){
			
			control.setBackground(Colors.getSystemColor(control.getDisplay(), SWT.COLOR_INFO_BACKGROUND));
			
			control.setForeground(Colors.getSystemColor(control.getDisplay(), SWT.COLOR_INFO_FOREGROUND));
			
		}else{
			
			control.setBackground(Utils.isDarkAppearanceNative()?Colors.dark_grey:Colors.fadedYellow );
		}
		
		if ( control instanceof Composite ){
			
			Composite comp = (Composite)control;
			
			for ( Control kid: comp.getChildren()) {
				
				highlightControl( kid, text, type1 );
			}
			
			if ( type1 ){
				highlightText( comp, text );
			}else{
				highlightText2( comp, text );
			}
		}
	}

	private void 
	ensureSectionBuilt(
		TreeItem treeSection, 
		boolean recreateIfAlreadyThere) 
	{
		if (treeSection == null) {
			return;
		}
    
		ScrolledComposite item = (ScrolledComposite)treeSection.getData(TREEITEMDATA_PANEL);

		if (item == null) {
			return;
		}

		BaseConfigSection configSection = (BaseConfigSection) treeSection.getData(TREEITEMDATA_CONFIGSECTION);

		if (configSection == null) {
			return;
		}

		Control previous = item.getContent();
		if (previous instanceof Composite) {
			if (!recreateIfAlreadyThere) {
				return;
			}
			configSection.deleteConfigSection();
			sectionsCreated.remove(configSection);
			// This is a must! For some reason, if we have a wrapped label in
			// the content, changing the content to another composite without first
			// nulling it will result in nothing being shown.
			item.setContent(null);
			Utils.disposeComposite((Composite)previous,true);
		}

		Composite c = new Composite(item, SWT.NULL);

		GridData gridData = new GridData(GridData.FILL_BOTH);
		c.setLayoutData(gridData);
		GridLayout layout = new GridLayout();
		layout.numColumns = 2;
		c.setLayout(layout);

		int minUserMode = configSection.getMinUserMode();
		if (minUserMode > Parameter.MODE_BEGINNER
				&& minUserMode > COConfigurationManager.getIntParameter(ConfigKeys.ICFG_USER_MODE)) {
			buildUnavailableSection(c,
					COConfigurationManager.getIntParameter(ConfigKeys.ICFG_USER_MODE), minUserMode);
		} else {
			if (!configSection.isBuilt()) {
				configSection.build();
				configSection.postBuild();
			}
			Parameter[] paramArray = configSection.getParamArray();
			Map<ParameterImpl, BaseSwtParameter> mapParamToSwtParam = new HashMap<>();
			if (paramArray.length > 0) {
				ParameterImplListener parameterImplListener = new MultiParameterImplListenerSWT(
						mapParamToSwtParam);
				buildScreen(c, paramArray, mapParamToSwtParam,
						parameterImplListener);
			}

			if (configSection instanceof BaseConfigSectionSWT) {
				((BaseConfigSectionSWT) configSection).configSectionCreate(c, mapParamToSwtParam);
			}
		}

			// we need to do this here as, on GTK at least, leaving it until later causes check/radio-boxes not
			// to layout correctly after their font is changed

		if (filterText.length() > 0) {
			highlightText(c, filterText);

		}

		sectionsCreated.add(configSection);

		item.setContent(c);
		
		c.addListener( SWT.Move, (ev)->{
			
				// for use in SWTThread when hacking scroll behaviour on Linux
			item.setData("LastScrollTime", SystemTime.getMonotonousTime());
		});
	}

	private static void buildUnavailableSection(Composite parent,
			int userMode, int requiredMode) {
		Label label = new Label(parent, SWT.WRAP);
		label.setLayoutData(
				Utils.getWrappableLabelGridData(1, GridData.FILL_HORIZONTAL));

		final String[] modeKeys = {
			"ConfigView.section.mode.beginner",
			"ConfigView.section.mode.intermediate",
			"ConfigView.section.mode.advanced"
		};

		String param1, param2;
		if (requiredMode < modeKeys.length)
			param1 = MessageText.getString(modeKeys[requiredMode]);
		else
			param1 = String.valueOf(requiredMode);

		if (userMode < modeKeys.length)
			param2 = MessageText.getString(modeKeys[userMode]);
		else
			param2 = String.valueOf(userMode);

		label.setText(MessageText.getString("ConfigView.notAvailableForMode",
				new String[] {
					param1,
					param2
				}));
	}

	private void updateHeader(TreeItem section) {
		if (section == null || lHeader == null) {
			return;
		}

		lHeader.setData( "TreeItem", section );

		int userMode = COConfigurationManager.getIntParameter(ConfigKeys.ICFG_USER_MODE);
		int maxUsermode = 0;
		try
		{
			BaseConfigSection sect = sections.get(section);
			if (sect != null) {
				maxUsermode = sect.getMaxUserMode();
			}
		} catch (Error e)
		{
			//Debug.printStackTrace(e);
		}

		String id = (String)section.getData( "ID" );

		String shortcut = COConfigurationManager.getStringParameter( "config.section.shortcut.key." + id, "" );

		String sc_text;

		if (shortcut.length() > 0){

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
			else if (o instanceof Item) {
				return ((Item) o).getText();
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

	private static TreeItem findTreeItem(TreeItem item, String ID) {
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
		if (cConfig == null) {
			return;
		}
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
			  
			  	// hmm, not working, let's try something else:
			  
			  Control fc = save.getDisplay().getFocusControl();
			  
			  if ( fc != null ){
			  
				  fc.traverse( SWT.TRAVERSE_TAB_NEXT );
			  }
			  
			  save();
		  }
	  });
  }

  private void initApplyCloseButton() {
		if (cConfig == null) {
			return;
		}
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
		if (selection.length > 0) {
		  updateHeader(selection[0]);
	  }
	  if (swtView != null) {
    	swtView.setTitle(getFullTitle());
    }
  }

  private void delete( boolean forRebuild ) {
	  save();
	  for (BaseConfigSection section : sectionsCreated) {
		  try {
			  section.deleteConfigSection();
		  } catch (Exception e) {
			  Debug.out("Error while deleting config section", e);
		  }
	  }
	  sectionsCreated.clear();
	  if ( pluginSections != null ){
		  pluginSections.clear();
	  }

	  ConfigSectionRepository.getInstance().removeListener( this );

	  if (tree != null && !tree.isDisposed()) {
		  TreeItem[] items = tree.getItems();
		  for (TreeItem item : items) {
			  Composite c = (Composite) item.getData(TREEITEMDATA_PANEL);
			  Utils.disposeComposite(c);
			  item.setData(TREEITEMDATA_PANEL, null);
			  item.setData(TREEITEMDATA_CONFIGSECTION, null);
		  }
	  }
	  Utils.disposeComposite(cConfig, !forRebuild);

	  Utils.disposeSWTObjects(headerFont, filterFoundFont);
	  headerFont = null;
	  filterFoundFont = null;
  }

	private static String getFullTitle() {
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
			for (BaseConfigSection section : pluginSections) {
				section.saveConfigSection();
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
  public boolean informOfDuplicates(int type){
	  	// need this so we are informed of multiple config section selection events for same section
	 return( type == UISWTViewEvent.TYPE_DATASOURCE_CHANGED );
  }
  
	@Override
	public boolean eventOccurred(UISWTViewEvent event) {
    switch (event.getType()) {
      case UISWTViewEvent.TYPE_CREATE:
      	swtView = (UISWTView)event.getData();
      	swtView.setTitle(getFullTitle());
        break;

      case UISWTViewEvent.TYPE_DESTROY:
        delete(false);
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

	private static void buildScreen(final Composite main_tab,
			Parameter[] parameters,
			Map<ParameterImpl, BaseSwtParameter> mapParamToSwtParam,
			ParameterImplListener parameterImplListener) {
		int userMode = Utils.getUserMode();

		// main tab set up

		Composite curComposite = main_tab;

		Map<ParameterGroupImpl, Composite> group_map = new HashMap<>();

		Map<ParameterTabFolderImpl, CTabFolder> tab_folder_map = new HashMap<>();
		Map<ParameterGroupImpl, Composite> tab_map = new HashMap<>();

		for (int i = 0; i < parameters.length; i++) {

			ParameterImpl param = (ParameterImpl) parameters[i];

			if (param.getMinimumRequiredUserMode() > userMode) {

				continue;
			}

			ParameterGroupImpl pg = param.getGroup();

			if (pg != null && pg.getGroup() != null
					&& group_map.get(pg.getGroup()) == null
					&& tab_folder_map.get(pg.getGroup()) == null) {

				// Parent group hasn't been created yet.
				// Hack in a label to parent group, so it gets created
				// doesn't solve deeper nesting
				i--;
				param = new ParameterImpl(null, null) {
				};
				pg = pg.getGroup();
				param.setGroup(pg);
			}

			if (pg == null) {

				curComposite = main_tab;

			} else {

				ParameterTabFolderImpl tab_folder = pg.getTabFolder();

				if (tab_folder != null) {
					curComposite = handleTabFolder(userMode, curComposite, group_map,
							tab_folder_map, tab_map, pg, tab_folder);
				}

				Composite comp = group_map.get(pg);

				if (comp == null) {

					ParameterGroupImpl pgParent = pg.getGroup();

					boolean nested = pgParent != null || tab_folder != null;

					if (tab_folder == null) {
						Composite composite = group_map.get(pgParent);
						if (composite != null) {
							curComposite = composite;
						}
					}

					Composite group_parent = nested ? curComposite : main_tab;

					String resource_name = pg.getGroupTitleKey();

					boolean use_composite = resource_name == null || tab_folder != null;

					curComposite = use_composite ? new Composite(group_parent, SWT.NONE)
							: Utils.createSkinnedGroup(group_parent, SWT.NULL);

					Control relatedControl = null;
					if (!use_composite) {
						Canvas gap = new Canvas(group_parent, SWT.NULL);
						relatedControl = gap;
						gap.setData("gap", true);
						GridData gridData = new GridData();
						gridData.widthHint = 1;
						gridData.heightHint = 2;
						gridData.horizontalSpan = ((GridLayout) group_parent.getLayout()).numColumns;
						gap.setLayoutData(gridData);

						Messages.setLanguageText(curComposite, resource_name);
						if (groupFont == null) {
							groupFont = FontUtils.getFontPercentOf(curComposite.getFont(),
									1.25f);
						}
						curComposite.setFont(groupFont);
					}

					if (group_parent.getLayout() instanceof GridLayout) {
						GridData grid_data = new GridData(GridData.FILL_HORIZONTAL);

						grid_data.horizontalSpan = 2;

						if (pg.getMinimumRequiredUserMode() > userMode) {

							curComposite.setVisible(false);

							grid_data.widthHint = 0;
							grid_data.heightHint = 0;
						}

						grid_data.horizontalIndent = pg.getIndent() * 20;

						if (!use_composite) {
							Control[] children = group_parent.getChildren();
							// last = gap widget
							// last - 1 = us
							// last - 2 = gap widget or previous control
							if (children.length > 3
									&& children[children.length - 3].getData("gap") == null) {
								grid_data.verticalIndent = 5;
							}
						}

						curComposite.setLayoutData(grid_data);
					}

					int numColumns = pg.getNumberColumns();

					if (numColumns > 0) {
						GridLayout layout = new GridLayout();

						layout.numColumns = numColumns * 2;

						if (use_composite) {
							layout.marginWidth = layout.marginHeight = 0;
						} else {
							layout.marginTop = 5;
						}

						curComposite.setLayout(layout);
					} else {
						RowLayout layout = new RowLayout();
						layout.marginLeft = pg.getIndent() * 20;
						layout.marginRight = 0;
						layout.spacing = 5;
						layout.center = true;
						curComposite.setLayout(layout);
					}

					group_map.put(pg, curComposite);

					UISWTParameter swt_param = new GroupSWTParameter(curComposite, pg,
							relatedControl);

					int indent = pg.getIndent();
					if (indent > 0) {
						swt_param.setIndent(indent, param.isIndentFancy());
					}

					String refID = pg.getReferenceID();
					if (refID != null) {
						// TODO: Migrate to a field
						swt_param.getMainControl().setData(SELECT_KEY, refID);
					}

					mapParamToSwtParam.put(pg, swt_param);

				} else {

					curComposite = comp;
				}
			}

			String label_key = param.getLabelKey();

			if (label_key == null) {
				String labelText = param.getLabelText();
				if (labelText != null) {
					label_key = "!" + labelText + "!";
				}
			}

			String key = param.getConfigKeyName();

			//System.out.println( "key = " + key );

			final BaseSwtParameter swt_param;

			if (param instanceof HyperlinkParameterImpl) {
				// check must be before LabelParameterImpl

				swt_param = new LinkSwtParameter(curComposite,
						(HyperlinkParameterImpl) param);

			} else if (param instanceof LabelParameterImpl) {
				// check must be after HyperlinkParameterImpl

				swt_param = new InfoSwtParameter(curComposite,
						(LabelParameterImpl) param);

			} else if (param instanceof BooleanParameterImpl) {

				swt_param = new BooleanSwtParameter(curComposite,
						(BooleanParameterImpl) param);

			} else if (param instanceof IntParameterImpl) {

				swt_param = new IntSwtParameter(curComposite, (IntParameterImpl) param);

			} else if (param instanceof FloatParameterImpl) {

				swt_param = new FloatSwtParameter(curComposite,
						(FloatParameterImpl) param);

			} else if (param instanceof ColorParameterImpl) {

				swt_param = new ColorSwtParameter(curComposite,
						(ColorParameterImpl) param);

			} else if (param instanceof StringParameterImpl) {

				StringParameterImpl s_param = (StringParameterImpl) param;

				int num_lines = s_param.getMultiLine();

				if (num_lines <= 1) {

					swt_param = new StringSwtParameter(curComposite, s_param);

				} else {

					swt_param = new StringAreaSwtParameter(curComposite, s_param);

				}
			} else if (param instanceof InfoParameterImpl) {

				swt_param = new InfoSwtParameter(curComposite,
						(InfoParameterImpl) param);

			} else if (param instanceof StringListParameterImpl) {

				swt_param = new StringListSwtParameter(curComposite,
						(StringListParameterImpl) param);

			} else if (param instanceof IntListParameterImpl) {

				IntListParameterImpl il_param = (IntListParameterImpl) param;

				int listType = il_param.getListType();
				if (listType == IntListParameter.TYPE_RADIO_LIST
						|| listType == IntListParameter.TYPE_RADIO_COMPACT) {
					swt_param = new IntRadioListSwtParameter(curComposite, il_param);
				} else {

					swt_param = new IntListSwtParameter(curComposite, il_param);
				}

			} else if (param instanceof PasswordParameterImpl) {

				swt_param = new PasswordSwtParameter(curComposite,
						(PasswordParameterImpl) param);

			} else if (param instanceof FileParameterImpl) {

				swt_param = new FileSwtParameter(curComposite,
						(FileParameterImpl) param);

			} else if (param instanceof DirectoryParameterImpl) {

				swt_param = new DirectorySwtParameter(curComposite,
						(DirectoryParameterImpl) param);

			} else if (param instanceof ActionParameterImpl) {

				ActionParameterImpl _param = (ActionParameterImpl) param;

				if (_param.getStyle() == ActionParameter.STYLE_BUTTON) {

					swt_param = new ButtonSwtParameter(curComposite,
							(ActionParameterImpl) param);

				} else {

					swt_param = new LinkSwtParameter(curComposite,
							(ActionParameterImpl) param);
				}

			} else if (param instanceof UIParameterImpl) {
				if (((UIParameterImpl) param).getContext() instanceof UISWTParameterContext) {
					UISWTParameterContext context = (UISWTParameterContext) ((UIParameterImpl) param).getContext();
					Composite internal_composite = new Composite(curComposite, SWT.NULL);
					GridData gridData = new GridData(GridData.FILL_HORIZONTAL);
					gridData.horizontalSpan = param.getLabelKey() == null
							|| param.getLabelText() == null ? 2 : 1;
					internal_composite.setLayoutData(gridData);
					boolean initialised_component = true;
					try {
						context.create(internal_composite);
					} catch (Exception e) {
						Debug.printStackTrace(e);
						initialised_component = false;
					}

					if (initialised_component) {
						swt_param = new UISWTParameter(internal_composite,
								param.getConfigKeyName());
					} else {
						swt_param = null;
					}
				} else {
					swt_param = null;
				}

			} else if (param instanceof UITextAreaImpl) {

				swt_param = new TextAreaSwtParameter(curComposite,
						((UITextAreaImpl) param));

			} else {
				swt_param = null;
			}

			if (swt_param != null) {

				int indent = param.getIndent();
				if (indent > 0) {
					swt_param.setIndent(indent, param.isIndentFancy());
				}

				String refID = param.getReferenceID();
				if (refID != null) {
					// TODO: Migrate to a field
					swt_param.getMainControl().setData(SELECT_KEY, refID);
				}

				mapParamToSwtParam.put(param, swt_param);
			}
		}

		for (Parameter parameter : parameters) {

			final ParameterImpl param = (ParameterImpl) parameter;

			param.addImplListener(parameterImplListener);

			if (!param.isEnabled()) {

				BaseSwtParameter swtParam = mapParamToSwtParam.get(param);

				if (swtParam != null) {
					swtParam.setEnabled(false);
				}
			}

			if (!param.isVisible()) {

				BaseSwtParameter swtParam = mapParamToSwtParam.get(param);

				if (swtParam != null) {
					swtParam.setVisible(false);
				}
			}

			List<BaseSwtParameter> swtParamsToEnable = new ArrayList<>();

			List<Parameter> listEnableOnSelection = param.getEnabledOnSelectionParameters();
			for (Parameter enable_param : listEnableOnSelection) {

				BaseSwtParameter stuff = mapParamToSwtParam.get(enable_param);

				if (stuff != null) {

					swtParamsToEnable.add(stuff);
				}
			}

			List<BaseSwtParameter> swtParamsToDisable = new ArrayList<>();

			List<Parameter> listDisableOnSelection = param.getDisabledOnSelectionParameters();
			for (Parameter disable_param : listDisableOnSelection) {

				BaseSwtParameter swtParameter = mapParamToSwtParam.get(disable_param);

				if (swtParameter != null) {

					swtParamsToDisable.add(swtParameter);
				}
			}

			if (swtParamsToEnable.size() + swtParamsToDisable.size() > 0) {

				BaseSwtParameter swtParameter = mapParamToSwtParam.get(param);

				// might not be visible (e.g. user mode too low) in which case it won't be in the map

				if (swtParameter instanceof BooleanSwtParameter) {
					IAdditionalActionPerformer<Boolean> ap = new DualChangeSelectionActionPerformer(
						swtParamsToEnable.toArray(new BaseSwtParameter[0]),
						swtParamsToDisable.toArray(new BaseSwtParameter[0]));

					((BooleanSwtParameter) swtParameter).setAdditionalActionPerformer(ap);
				}
			}
		}
	}

	private static Composite handleTabFolder(int userMode,
			Composite current_composite, Map<ParameterGroupImpl, Composite> group_map,
			Map<ParameterTabFolderImpl, CTabFolder> tab_folder_map,
			Map<ParameterGroupImpl, Composite> tab_map, ParameterGroupImpl pg,
			ParameterTabFolderImpl tab_folder) {
		GridLayout layout;
		ParameterGroupImpl tab_group = tab_folder.getGroup();

		CTabFolder	tf = tab_folder_map.get( tab_folder );

		if ( tf == null ){

			Composite tab_parent = current_composite;

			if ( tab_group != null ){

				String tg_resource = tab_group.getGroupTitleKey();

				if ( tg_resource != null ){

					tab_parent = group_map.get( tab_group );

					if ( tab_parent == null ){

						tab_parent = Utils.createSkinnedGroup( current_composite, SWT.NULL);

						Messages.setLanguageText(tab_parent, tg_resource );

						if (current_composite.getLayout() instanceof GridLayout) {

							GridData gridData = new GridData(GridData.FILL_HORIZONTAL );

							gridData.horizontalSpan = 2;

							if ( tab_group.getMinimumRequiredUserMode() > userMode ){

								tab_parent.setVisible( false );

								gridData.widthHint = 0;
								gridData.heightHint = 0;
							}

							tab_parent.setLayoutData(gridData);
						}

						layout = new GridLayout();

						layout.numColumns = tab_group.getNumberColumns() * 2;

						tab_parent.setLayout(layout);

						group_map.put( tab_group, tab_parent );
					}
				}
			}

			tf = new CTabFolder( tab_parent, SWT.LEFT );

			tf.setBorderVisible( tab_group == null );

			tf.setTabHeight(20);

			GridData grid_data = new GridData( GridData.FILL_HORIZONTAL );

			grid_data.horizontalSpan = 2;

			if ( tab_folder.getMinimumRequiredUserMode() > userMode ){

				tf.setVisible( false );

				grid_data.widthHint = 0;
				grid_data.heightHint = 0;
			}

			tf.setLayoutData(grid_data);

			tab_folder_map.put( tab_folder, tf );
		}

		Composite tab_composite = tab_map.get( pg );

		if ( tab_composite == null ){

			CTabItem tab_item = new CTabItem(tf, SWT.NULL);

			String tab_name = pg.getGroupTitleKey();

			if ( tab_name != null ){

				Messages.setLanguageText( tab_item, tab_name );
			}

			tab_composite = new Composite( tf, SWT.NONE );
			tab_item.setControl( tab_composite );

			layout = new GridLayout();
			layout.numColumns = 2;

			tab_composite.setLayout(layout);

			GridData grid_data = new GridData(GridData.FILL_BOTH);

			if ( pg.getMinimumRequiredUserMode() > userMode ){

				tab_composite.setVisible( false );

				grid_data.widthHint = 0;
				grid_data.heightHint = 0;
			}

			tab_composite.setLayoutData(grid_data);

			if ( tf.getItemCount() == 1 ){

				tf.setSelection( tab_item );
			}

			tab_map.put( pg, tab_composite );
		}

		current_composite = tab_composite;
		return current_composite;
	}

	private static class GroupSWTParameter
		extends UISWTParameter
	{
		public GroupSWTParameter(Composite current_composite, ParameterGroupImpl pg,
				Control relatedControl) {
			super(current_composite, pg);
			setRelatedControl(relatedControl);
		}
	}
}
