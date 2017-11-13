/*
 * File    : ConfigPanel*.java
 * Created : 11 mar. 2004
 * By      : TuxPaper
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

package com.biglybt.ui.swt.views.configsections;

import java.io.File;
import java.util.Arrays;
import java.util.Comparator;

import org.eclipse.swt.SWT;
import org.eclipse.swt.events.MouseAdapter;
import org.eclipse.swt.events.MouseEvent;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.layout.*;
import org.eclipse.swt.widgets.*;

import com.biglybt.core.config.COConfigurationManager;
import com.biglybt.core.internat.MessageText;
import com.biglybt.core.ipfilter.*;
import com.biglybt.core.ipfilter.impl.IpFilterAutoLoaderImpl;
import com.biglybt.core.logging.LogAlert;
import com.biglybt.core.logging.Logger;
import com.biglybt.core.util.AERunnable;
import com.biglybt.core.util.DisplayFormatters;
import com.biglybt.ui.swt.Messages;
import com.biglybt.ui.swt.Utils;
import com.biglybt.ui.swt.config.*;
import com.biglybt.ui.swt.pif.UISWTConfigSection;

import com.biglybt.core.CoreFactory;
import com.biglybt.ui.swt.imageloader.ImageLoader;

import com.biglybt.pif.ui.config.ConfigSection;

public class ConfigSectionIPFilter implements UISWTConfigSection {
  IpFilter filter;
  Table table;
  boolean noChange;

  FilterComparator comparator;

  private boolean bIsCachingDescriptions = false;

  static class FilterComparator implements Comparator {

    boolean ascending = true;

    static final int FIELD_NAME = 0;
    static final int FIELD_START_IP = 1;
    static final int FIELD_END_IP = 2;

    int field = FIELD_START_IP;


    @Override
    public int compare(Object arg0, Object arg1) {
      IpRange range0 = (IpRange) arg0;
      IpRange range1 = (IpRange) arg1;
      if(field == FIELD_NAME) {
        return (ascending ? 1 : -1) * ( range0.compareDescription( range1 ));
      }
      if(field == FIELD_START_IP) {
        return (ascending ? 1 : -1) * ( range0.compareStartIpTo( range1 ));
      }
      if(field == FIELD_END_IP) {
        return (ascending ? 1 : -1) * ( range0.compareEndIpTo( range1 ));
      }
      return 0;
    }

    public void setField(int newField) {
      if(field == newField) ascending = ! ascending;
      field = newField;
    }


  }

  IpRange 	ipRanges[];
  Label		percentage_blocked;

	private IPFilterListener filterListener;

  public
  ConfigSectionIPFilter()
  {
  	comparator = new FilterComparator();
  }

  @Override
  public String configSectionGetParentSection() {
    return ConfigSection.SECTION_ROOT;
  }

	@Override
	public String configSectionGetName() {
		return "ipfilter";
	}

  @Override
  public void configSectionSave() {
    try{
      if (filter != null)
      	filter.save();
    }catch( Exception e ){
    	Logger.log(new LogAlert(LogAlert.UNREPEATABLE,
					"Save of filter file fails", e));
    }
  }

	@Override
	public int maxUserMode() {
		return 1;
	}


  @Override
  public void configSectionDelete() {
  	if (bIsCachingDescriptions) {
	    IpFilterManager ipFilterManager = CoreFactory.getSingleton().getIpFilterManager();
	  	ipFilterManager.clearDescriptionCache();
	  	bIsCachingDescriptions = false;
  	}

  	if (filter != null) {
  		filter.removeListener(filterListener);
  	}
		ImageLoader imageLoader = ImageLoader.getInstance();
		imageLoader.releaseImage("openFolderButton");
		imageLoader.releaseImage("subitem");
  }

  @Override
  public Composite configSectionCreate(final Composite parent) {

  	if (!CoreFactory.isCoreRunning()) {
      Composite cSection = new Composite(parent, SWT.NULL);
    	cSection.setLayout(new FillLayout());
    	Label lblNotAvail = new Label(cSection, SWT.WRAP);
    	Messages.setLanguageText(lblNotAvail, "core.not.available");
    	return cSection;
    }

		ImageLoader imageLoader = ImageLoader.getInstance();
		Image imgOpenFolder = imageLoader.getImage("openFolderButton");

		String sCurConfigID;

    GridData gridData;

    int userMode = COConfigurationManager.getIntParameter("User Mode");

    final IpFilterManager ipFilterManager = CoreFactory.getSingleton().getIpFilterManager();
    filter = ipFilterManager.getIPFilter();

    Composite gFilter = new Composite(parent, SWT.NULL);
    GridLayout layout = new GridLayout();
		layout.marginHeight = 0;
		layout.marginWidth = 0;
    gFilter.setLayout(layout);
    gridData = new GridData(GridData.VERTICAL_ALIGN_FILL | GridData.HORIZONTAL_ALIGN_FILL);
    Utils.setLayoutData(gFilter, gridData);


    // start controls

    	// row: enable filter + allow/deny

	gridData = new GridData();

    BooleanParameter enabled = new BooleanParameter(gFilter, "Ip Filter Enabled");
	enabled.setLayoutData( gridData );
    Messages.setLanguageText(enabled.getControl(), "ConfigView.section.ipfilter.enable");

	gridData = new GridData();

    BooleanParameter deny = new BooleanParameter(gFilter, "Ip Filter Allow");
	deny.setLayoutData( gridData );
    Messages.setLanguageText(deny.getControl(), "ConfigView.section.ipfilter.allow");

    deny.addChangeListener(
    	new ParameterChangeAdapter()
		{
    		@Override
		    public void
    		parameterChanged(
    			Parameter	p,
    			boolean		caused_internally )
			{

    			setPercentageBlocked();
			}
		});

    	// row persist banning

	gridData = new GridData();

	BooleanParameter persist_bad_data_banning = new BooleanParameter(gFilter, "Ip Filter Banning Persistent");
	persist_bad_data_banning.setLayoutData( gridData );
	Messages.setLanguageText(persist_bad_data_banning.getControl(), "ConfigView.section.ipfilter.persistblocking");

	BooleanParameter disableForUpdates = new BooleanParameter(gFilter, "Ip Filter Disable For Updates");
	Messages.setLanguageText(disableForUpdates.getControl(), "ConfigView.section.ipfilter.disable.for.updates");

    Group gBlockBanning = new Group(gFilter, SWT.NULL);
    Messages.setLanguageText(gBlockBanning, "ConfigView.section.ipfilter.peerblocking.group");
    layout = new GridLayout();
    layout.numColumns = 2;
    gBlockBanning.setLayout(layout);


  	// row block bad + group ban

		BooleanParameter enable_bad_data_banning = new BooleanParameter(
				gBlockBanning, "Ip Filter Enable Banning",
				"ConfigView.section.ipfilter.enablebanning");
		gridData = new GridData();
		gridData.horizontalSpan = 2;
		enable_bad_data_banning.setLayoutData(gridData);

    Label discard_label = new Label(gBlockBanning, SWT.NULL);
    Messages.setLanguageText(discard_label,
				"ConfigView.section.ipfilter.discardbanning");

    FloatParameter discard_ratio = new FloatParameter(gBlockBanning, "Ip Filter Ban Discard Ratio");
    gridData = new GridData();
    discard_ratio.setLayoutData(gridData);


    Composite cIndent = new Composite(gBlockBanning, SWT.NONE);
    gridData = new GridData(GridData.FILL_BOTH);
    gridData.horizontalSpan = 2;
    gridData.horizontalIndent = 15;
    Utils.setLayoutData(cIndent, gridData);
    layout = new GridLayout(3, false);
    layout.marginHeight = 0;
    layout.marginWidth = 0;
    cIndent.setLayout(layout);

		Image img = imageLoader.getImage("subitem");
		Label label = new Label(cIndent, SWT.NULL);
		gridData = new GridData(GridData.VERTICAL_ALIGN_BEGINNING);
		Utils.setLayoutData(label, gridData);
		label.setImage(img);


		Label discard_min_label = new Label(cIndent, SWT.NULL);
    Messages.setLanguageText(discard_min_label,
    "ConfigView.section.ipfilter.discardminkb", new String[]{ DisplayFormatters.getUnit( DisplayFormatters.UNIT_KB)});

    IntParameter discard_min = new IntParameter(cIndent, "Ip Filter Ban Discard Min KB");
    gridData = new GridData();
    discard_min.setLayoutData(gridData);

   	// block banning

    Label block_label = new Label(gBlockBanning, SWT.NULL);
    Messages.setLanguageText(block_label,
    "ConfigView.section.ipfilter.blockbanning");

    IntParameter block_banning = new IntParameter(gBlockBanning,
    "Ip Filter Ban Block Limit", 0, 256);
    gridData = new GridData();
    block_banning.setLayoutData(gridData);

    // triggers

    enable_bad_data_banning.setAdditionalActionPerformer(new ChangeSelectionActionPerformer(
    		new Control[] {
    				block_banning.getControl(), block_label,
    				discard_ratio.getControl(), discard_label,
    				discard_min.getControl(), discard_min_label }));










    Group gAutoLoad = new Group(gFilter, SWT.NONE);
    Messages.setLanguageText(gAutoLoad, "ConfigView.section.ipfilter.autoload.group");
    FormLayout flayout = new FormLayout();
    flayout.marginHeight =flayout.marginWidth = 5;
    gAutoLoad.setLayout(flayout);
    gridData = new GridData(GridData.HORIZONTAL_ALIGN_FILL);
    gridData.widthHint = 500;
    Utils.setLayoutData(gAutoLoad, gridData);

    FormData fd;

    // Load from file
    sCurConfigID = "Ip Filter Autoload File";
    //allConfigIDs.add(sCurConfigID);
    Label lblDefaultDir = new Label(gAutoLoad, SWT.NONE);
    Messages.setLanguageText(lblDefaultDir, "ConfigView.section.ipfilter.autoload.file");
    fd = new FormData();
    Utils.setLayoutData(lblDefaultDir, fd);

    final StringParameter pathParameter = new StringParameter(gAutoLoad, sCurConfigID);

    Button browse = new Button(gAutoLoad, SWT.PUSH);
    browse.setImage(imgOpenFolder);
    imgOpenFolder.setBackground(browse.getBackground());

    browse.addListener(SWT.Selection, new Listener() {
      @Override
      public void handleEvent(Event event) {
        FileDialog dialog = new FileDialog(parent.getShell(), SWT.APPLICATION_MODAL);
        dialog.setFilterPath(pathParameter.getValue());
        dialog.setText(MessageText.getString("ConfigView.section.ipfilter.autoload.file"));
        dialog.setFilterExtensions(new String[] {
					"*.dat" + File.pathSeparator + "*.p2p" + File.pathSeparator + "*.p2b"
							+ File.pathSeparator + "*.txt",
					"*.*"
				});
        dialog.setFileName("ipfilter.dat");
        String file = dialog.open();
        if (file != null) {
          pathParameter.setValue(file);
        }
      }
    });

    final Button btnLoadNow = new Button(gAutoLoad, SWT.PUSH);
    Messages.setLanguageText(btnLoadNow, "ConfigView.section.ipfilter.autoload.loadnow");
    btnLoadNow.addListener(SWT.Selection, new Listener() {
			@Override
			public void handleEvent(Event event) {
				btnLoadNow.setEnabled(false);
				COConfigurationManager.setParameter(
						IpFilterAutoLoaderImpl.CFG_AUTOLOAD_LAST, 0);
				Utils.getOffOfSWTThread(new AERunnable() {
					@Override
					public void runSupport() {
						try {
							filter.reloadSync();
						} catch (Exception e) {
							e.printStackTrace();
						}
						Utils.execSWTThread(new AERunnable() {
							@Override
							public void runSupport() {
								if (!btnLoadNow.isDisposed()) {
									btnLoadNow.setEnabled(true);
								}
							}
						});
					}
				});
			}
		});

    fd = new FormData();
    fd.right = new FormAttachment(100, 0);
    Utils.setLayoutData(btnLoadNow, fd);

    fd = new FormData();
    fd.right = new FormAttachment(btnLoadNow, -5);
    Utils.setLayoutData(browse, fd);

    fd = new FormData();
    fd.left = new FormAttachment(lblDefaultDir, 5);
    fd.right = new FormAttachment(browse, -5);
    pathParameter.setLayoutData(fd);

    Label lblAutoLoadInfo = new Label(gAutoLoad, SWT.WRAP);
    Messages.setLanguageText(lblAutoLoadInfo, "ConfigView.section.ipfilter.autoload.info");
    fd = new FormData();
    fd.top = new FormAttachment(btnLoadNow, 3);
    fd.left = new FormAttachment(0, 0);
    fd.right = new FormAttachment(100, 0);
    Utils.setLayoutData(lblAutoLoadInfo, fd);

    BooleanParameter clear_on_reload = new BooleanParameter(gAutoLoad, "Ip Filter Clear On Reload" );
    fd = new FormData();
    fd.top = new FormAttachment(lblAutoLoadInfo, 3);
    fd.left = new FormAttachment(0, 0);
    fd.right = new FormAttachment(100, 0);
    clear_on_reload.setLayoutData(fd);
	Messages.setLanguageText(clear_on_reload.getControl(),
		"ConfigView.section.ipfilter.clear.on.reload");

    	// description scratch file

    if (userMode > 0) {
    	gridData = new GridData();
    	BooleanParameter enableDesc = new BooleanParameter(gFilter,
    	"Ip Filter Enable Description Cache");
    	enableDesc.setLayoutData(gridData);
    	Messages.setLanguageText(enableDesc.getControl(),
    	"ConfigView.section.ipfilter.enable.descriptionCache");
    }


		// table

    table = new Table(gFilter, SWT.SINGLE | SWT.BORDER | SWT.FULL_SELECTION | SWT.VIRTUAL);
    String[] headers = { "label.description", "ConfigView.section.ipfilter.start", "ConfigView.section.ipfilter.end" };
    int[] sizes = { 110, 110, 110 };
    int[] aligns = { SWT.LEFT, SWT.CENTER, SWT.CENTER };
    for (int i = 0; i < headers.length; i++) {
      TableColumn tc = new TableColumn(table, aligns[i]);
      tc.setText(headers[i]);
      tc.setWidth(Utils.adjustPXForDPI(sizes[i]));
      Messages.setLanguageText(tc, headers[i]); //$NON-NLS-1$
    }



    TableColumn[] columns = table.getColumns();
    columns[0].setData(new Integer(FilterComparator.FIELD_NAME));
    columns[1].setData(new Integer(FilterComparator.FIELD_START_IP));
    columns[2].setData(new Integer(FilterComparator.FIELD_END_IP));

    Listener listener = new Listener() {
      @Override
      public void handleEvent(Event e) {
        TableColumn tc = (TableColumn) e.widget;
        int field = ((Integer) tc.getData()).intValue();
        comparator.setField(field);

        if (field == FilterComparator.FIELD_NAME && !bIsCachingDescriptions) {
        	ipFilterManager.cacheAllDescriptions();
        	bIsCachingDescriptions = true;
        }
        ipRanges = getSortedRanges(filter.getRanges());
        table.setItemCount(ipRanges.length);
        table.clearAll();
    	// bug 69398 on Windows
    	table.redraw();
      }
    };

    columns[0].addListener(SWT.Selection,listener);
    columns[1].addListener(SWT.Selection,listener);
    columns[2].addListener(SWT.Selection,listener);

    table.setHeaderVisible(true);

    gridData = new GridData(GridData.FILL_BOTH);
    gridData.heightHint = table.getHeaderHeight() * 3;
		gridData.widthHint = 200;
    Utils.setLayoutData(table, gridData);

    Composite cArea = new Composite(gFilter, SWT.NULL);
    layout = new GridLayout();
    layout.marginHeight = 0;
    layout.marginWidth = 0;
    layout.numColumns = 4;
    cArea.setLayout(layout);
  	gridData = new GridData(GridData.FILL_HORIZONTAL);
    Utils.setLayoutData(cArea, gridData);

    Button add = new Button(cArea, SWT.PUSH);
    gridData = new GridData(GridData.CENTER);
    gridData.widthHint = 100;
    Utils.setLayoutData(add, gridData);
    Messages.setLanguageText(add, "Button.add");
    add.addListener(SWT.Selection, new Listener() {
      @Override
      public void handleEvent(Event arg0) {
        addRange();
      }
    });

    Button remove = new Button(cArea, SWT.PUSH);
    gridData = new GridData(GridData.CENTER);
    gridData.widthHint = 100;
    Utils.setLayoutData(remove, gridData);
    Messages.setLanguageText(remove, "ConfigView.section.ipfilter.remove");
    remove.addListener(SWT.Selection, new Listener() {
      @Override
      public void handleEvent(Event arg0) {
        TableItem[] selection = table.getSelection();
        if (selection.length == 0)
          return;
        removeRange((IpRange) selection[0].getData());
        ipRanges = getSortedRanges(filter.getRanges());
        table.setItemCount(ipRanges.length);
        table.clearAll();
        table.redraw();
      }
    });

    Button edit = new Button(cArea, SWT.PUSH);
    gridData = new GridData(GridData.CENTER);
    gridData.widthHint = 100;
    Utils.setLayoutData(edit, gridData);
    Messages.setLanguageText(edit, "Button.edit");
    edit.addListener(SWT.Selection, new Listener() {
      @Override
      public void handleEvent(Event arg0) {
        TableItem[] selection = table.getSelection();
        if (selection.length == 0)
          return;
        editRange((IpRange) selection[0].getData());
      }
    });

    percentage_blocked  = new Label(cArea, SWT.WRAP | SWT.RIGHT);
    gridData = new GridData(GridData.VERTICAL_ALIGN_FILL | GridData.FILL_HORIZONTAL);
    Utils.setLayoutData(percentage_blocked, gridData);
    Utils.setLayoutData(percentage_blocked, Utils.getWrappableLabelGridData(1, GridData.HORIZONTAL_ALIGN_FILL));
    setPercentageBlocked();



    table.addMouseListener(new MouseAdapter() {
      @Override
      public void mouseDoubleClick(MouseEvent arg0) {
        TableItem[] selection = table.getSelection();
        if (selection.length == 0)
          return;
        editRange((IpRange) selection[0].getData());
      }
    });

    Control[] controls = new Control[3];
    controls[0] = add;
    controls[1] = remove;
    controls[2] = edit;
    IAdditionalActionPerformer enabler = new ChangeSelectionActionPerformer(controls);
    enabled.setAdditionalActionPerformer(enabler);

    ipRanges = getSortedRanges(filter.getRanges());

    table.addListener(SWT.SetData, new Listener() {
			@Override
			public void handleEvent(Event event) {
				TableItem item = (TableItem) event.item;
				int index = table.indexOf(item);

				// seems we can get -1 here (see bug 1219314 )

				if (index < 0 || index >= ipRanges.length) {
					return;
				}
				IpRange range = ipRanges[index];
				item.setText(0, range.getDescription());
				item.setText(1, range.getStartIp());
				item.setText(2, range.getEndIp());
				item.setData(range);
			}
		});

    table.setItemCount(ipRanges.length);
    table.clearAll();
	// bug 69398 on Windows
	table.redraw();

		table.addListener(SWT.Resize, new Listener() {
			@Override
			public void handleEvent(Event e) {
        resizeTable();
			}
		});

		gFilter.addListener(SWT.Resize, new Listener() {
			@Override
			public void handleEvent(Event e) {
        resizeTable();
			}
		});


		filterListener = new IPFilterListener() {
			@Override
			public void
			IPFilterEnabledChanged(
				boolean			is_enabled )
			{
			}

			@Override
			public boolean canIPBeBanned(String ip) {
				return true;
			}

			@Override
			public void IPBanned(BannedIp ip) {
			}

			@Override
			public void IPBlockedListChanged(final IpFilter filter) {
				Utils.execSWTThread(new AERunnable() {
					@Override
					public void runSupport() {
						if (table.isDisposed()) {
					  	filter.removeListener(filterListener);
					  	return;
						}
		        ipRanges = getSortedRanges(filter.getRanges());
		        table.setItemCount(ipRanges.length);
		        table.clearAll();
		        table.redraw();
					}
				});

			}
			@Override
			public boolean
			canIPBeBlocked(
				String	ip,
				byte[]	torrent_hash )
			{
				return true;
			}
		};
    filter.addListener(filterListener);

    return gFilter;
  }

  private void resizeTable() {
	  int iNewWidth = table.getClientArea().width -
                    table.getColumn(1).getWidth() -
                    table.getColumn(2).getWidth() - 20;
    if (iNewWidth > 50)
      table.getColumn(0).setWidth(iNewWidth);
  }



  public void removeRange(IpRange range) {
  	filter.removeRange( range );
    //noChange = false;
    //refresh();
  }

  public void editRange(IpRange range) {
    new IpFilterEditor(CoreFactory.getSingleton(),table.getShell(), range);
    noChange = false;
    //refresh();
  }

  public void addRange() {
    new IpFilterEditor(CoreFactory.getSingleton(),table.getShell(), null);
    //noChange = false;
    //refresh();
  }

  public void refresh() {
    if (table == null || table.isDisposed() || noChange)
      return;
    noChange = true;
    TableItem[] items = table.getItems();
    for (int i = 0; i < items.length; i++) {
      if (items[i] == null || items[i].isDisposed())
        continue;
      String tmp = items[i].getText(0);
      IpRange range = (IpRange) items[i].getData();

      String	desc = range.getDescription();

      if (desc != null && !desc.equals(tmp))
        items[i].setText(0, desc);

      tmp = items[i].getText(1);
      if (range.getStartIp() != null && !range.getStartIp().equals(tmp))
        items[i].setText(1, range.getStartIp());

      tmp = items[i].getText(2);
      if (range.getEndIp() != null && !range.getEndIp().equals(tmp))
        items[i].setText(2, range.getEndIp());

    }
  }

  protected IpRange[]
  getSortedRanges(
  		IpRange[]	ranges )
  {
  	Arrays.sort(
  		ranges,
		comparator);

  	return( ranges );

  }

  protected void
  setPercentageBlocked()
  {
    long nbIPsBlocked = filter.getTotalAddressesInRange();

    if ( COConfigurationManager.getBooleanParameter( "Ip Filter Allow" )){

    	nbIPsBlocked = 0x100000000L - nbIPsBlocked;
    }

    int percentIPsBlocked =  (int) (nbIPsBlocked * 1000L / (256L * 256L * 256L * 256L));

    String nbIps = "" + nbIPsBlocked;
    String percentIps = DisplayFormatters.formatPercentFromThousands(percentIPsBlocked);

    Messages.setLanguageText(percentage_blocked,"ConfigView.section.ipfilter.totalIPs",new String[]{nbIps,percentIps});

  }
}
