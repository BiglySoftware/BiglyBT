/*
 * File : ModePanel.java Created : 30 sept. 2003 01:51:05 By : Olivier
 *
 * This program is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by the Free
 * Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for
 * more details ( see the LICENSE file ).
 *
 * You should have received a copy of the GNU General Public License along with
 * this program; if not, write to the Free Software Foundation, Inc., 59 Temple
 * Place, Suite 330, Boston, MA 02111-1307 USA
 */

package com.biglybt.ui.swt.maketorrent;

import org.eclipse.swt.SWT;
import org.eclipse.swt.events.ModifyEvent;
import org.eclipse.swt.events.ModifyListener;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.*;
import com.biglybt.core.config.COConfigurationManager;
import com.biglybt.core.internat.MessageText;
import com.biglybt.core.tracker.host.TRHost;
import com.biglybt.core.tracker.util.TRTrackerUtils;
import com.biglybt.core.util.Constants;
import com.biglybt.core.util.TrackersUtil;
import com.biglybt.core.util.UrlUtils;
import com.biglybt.ui.swt.Messages;
import com.biglybt.ui.swt.Utils;
import com.biglybt.ui.swt.wizard.AbstractWizardPanel;
import com.biglybt.ui.swt.wizard.IWizardPanel;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.Iterator;
import java.util.List;

/**
 * @author Olivier
 *
 */
public class ModePanel extends AbstractWizardPanel<NewTorrentWizard> {

  private Combo tracker;

  public ModePanel(NewTorrentWizard wizard, AbstractWizardPanel previous) {
    super(wizard, previous);
  }

  /*
	 * (non-Javadoc)
	 *
	 * @see com.biglybt.ui.swt.maketorrent.IWizardPanel#show()
	 */
  @Override
  public void show() {
	final NewTorrentWizard wizard = (NewTorrentWizard)this.wizard;
    wizard.setTitle(MessageText.getString("wizard.maketorrent.mode"));
    wizard.setCurrentInfo(MessageText.getString("wizard.maketorrent.singlefile.help"));
    Composite rootPanel = wizard.getPanel();
    GridLayout layout = new GridLayout();
    layout.numColumns = 1;
    rootPanel.setLayout(layout);

    Composite panel = new Composite(rootPanel, SWT.NO_RADIO_GROUP);
    GridData gridData = new GridData(GridData.VERTICAL_ALIGN_CENTER | GridData.FILL_HORIZONTAL);
    panel.setLayoutData(gridData);
    layout = new GridLayout();
    layout.numColumns = 4;
    panel.setLayout(layout);

    //Line :
    // O use embedded tracker []Use SSL

    final Button btnLocalTracker = new Button(panel, SWT.RADIO);
    Messages.setLanguageText(btnLocalTracker, "wizard.tracker.local");
    gridData = new GridData();
    gridData.horizontalSpan = 2;
    btnLocalTracker.setLayoutData(gridData);

    final Button btnSSL = new Button(panel, SWT.CHECK);
    Messages.setLanguageText(btnSSL, "wizard.tracker.ssl");
    gridData = new GridData(GridData.HORIZONTAL_ALIGN_END);
    gridData.horizontalSpan = 2;
    btnSSL.setLayoutData(gridData);

    //Line :
    //Announce URL : <local announce>

    final String localTrackerHost = COConfigurationManager.getStringParameter("Tracker IP", "");
    final int localTrackerPort 	= COConfigurationManager.getIntParameter("Tracker Port", TRHost.DEFAULT_PORT );
    final int localTrackerPortSSL = COConfigurationManager.getIntParameter("Tracker Port SSL", TRHost.DEFAULT_PORT_SSL );
    final boolean SSLEnabled = COConfigurationManager.getBooleanParameter("Tracker Port SSL Enable", false );

    final String[] localTrackerUrl = new String[1];

    // there's a potential oversize issue with the howToLocal string, and attemtping to force wrap has no effect -
    // therefore, provide more room and remove extraneous labeling

    final boolean showLocal = TRTrackerUtils.isTrackerEnabled();

    final Label labelLocalAnnounce = (showLocal) ? new Label(panel, SWT.NULL) : null;

    final Label localTrackerValue = new Label(panel, SWT.NULL);

    if ( showLocal ){

      Messages.setLanguageText(labelLocalAnnounce, "wizard.announceUrl");

      localTrackerUrl[0] = "http://" + UrlUtils.convertIPV6Host(localTrackerHost) + ":" + localTrackerPort + "/announce";
      localTrackerValue.setText(localTrackerUrl[0]);
      btnSSL.setEnabled( SSLEnabled );

      gridData = new GridData();
      gridData.horizontalSpan = 3;

    } else {

      localTrackerUrl[0] = "";
      Messages.setLanguageText(localTrackerValue, "wizard.tracker.howToLocal");
      btnLocalTracker.setSelection(false);
      btnSSL.setEnabled(false);
      btnLocalTracker.setEnabled(false);
      localTrackerValue.setEnabled(true);

      if ( wizard.getTrackerType() == NewTorrentWizard.TT_LOCAL ){

      	wizard.setTrackerType( NewTorrentWizard.TT_EXTERNAL );
      }

      gridData = new GridData();
      gridData.horizontalSpan = 4;
    }

    localTrackerValue.setLayoutData(gridData);

    int	tracker_type = wizard.getTrackerType();

    if (tracker_type == NewTorrentWizard.TT_LOCAL) {

      setTrackerUrl(localTrackerUrl[0]);

    }else if ( tracker_type == NewTorrentWizard.TT_EXTERNAL ){

    	if ( !wizard.isValidTracker( wizard.getTrackerURL())){
    		setTrackerUrl( NewTorrentWizard.TT_EXTERNAL_DEFAULT );
    	}

    }else{

      setTrackerUrl( NewTorrentWizard.TT_DECENTRAL_DEFAULT );
    }

    //Line:
    // O use external Tracker

    final Button btnExternalTracker = new Button(panel, SWT.RADIO);
    Messages.setLanguageText(btnExternalTracker, "wizard.tracker.external");
    gridData = new GridData();
    gridData.horizontalSpan = 4;
    btnExternalTracker.setLayoutData(gridData);

    //Line:
    // [External Tracker Url ]V

    final Label labelExternalAnnounce = new Label(panel, SWT.NULL);
    Messages.setLanguageText(labelExternalAnnounce, "wizard.announceUrl");

    btnLocalTracker.setSelection(tracker_type==NewTorrentWizard.TT_LOCAL);
    if(showLocal) localTrackerValue.setEnabled(tracker_type==NewTorrentWizard.TT_LOCAL);
    btnSSL.setEnabled(SSLEnabled&&tracker_type==NewTorrentWizard.TT_LOCAL);

    btnExternalTracker.setSelection(tracker_type==NewTorrentWizard.TT_EXTERNAL);
    labelExternalAnnounce.setEnabled(tracker_type==NewTorrentWizard.TT_EXTERNAL);

    tracker = new Combo(panel, SWT.NULL);
    gridData = new GridData(GridData.FILL_HORIZONTAL);
    gridData.horizontalSpan = 3;
    tracker.setLayoutData(gridData);
    
    List<String> trackers = TrackersUtil.getInstance().getTrackersList();
    for ( String t: trackers ){
      tracker.add( t );
    }
 
    tracker.addModifyListener(new ModifyListener() {
      /*
			 * (non-Javadoc)
			 *
			 * @see org.eclipse.swt.events.ModifyListener#modifyText(org.eclipse.swt.events.ModifyEvent)
			 */
      @Override
      public void modifyText(ModifyEvent arg0) {
        String text = tracker.getText();
        setTrackerUrl(text);

        boolean valid = true;
        String errorMessage = "";
        try {
          new URL(text);
        } catch (MalformedURLException e) {
        	valid = false;
        	errorMessage = MessageText.getString("wizard.maketorrent.invalidurl");
        }
        wizard.setErrorMessage(errorMessage);
        wizard.setNextEnabled(valid);

      }
    });

    tracker.addListener(SWT.Selection,new Listener() {
      @Override
      public void handleEvent(Event e) {
        String text = tracker.getText();
        setTrackerUrl(text);

        boolean valid = true;
        String errorMessage = "";
        try {
          new URL(text);
        } catch (MalformedURLException ex) {
        	valid = false;
        	errorMessage = MessageText.getString("wizard.maketorrent.invalidurl");
        }
        wizard.setErrorMessage(errorMessage);
        wizard.setNextEnabled(valid);
      }
    });

    updateTrackerURL();

    tracker.setEnabled( tracker_type == NewTorrentWizard.TT_EXTERNAL );

    new Label(panel,SWT.NULL);

    // O decentral tracking
    // has to be on same no-radio-group panel otherwise weird things happen regarding selection of
    // "external tracker" button *even if* we set it up so that "dht tracker" should be selected....

    final Button btnDHTTracker = new Button(panel, SWT.RADIO);
    Messages.setLanguageText(btnDHTTracker, "label.decentralised");
    gridData = new GridData();
    gridData.horizontalSpan = 4;
    btnDHTTracker.setLayoutData(gridData);

    btnDHTTracker.setSelection(tracker_type==NewTorrentWizard.TT_DECENTRAL);


    // add another panel due to control oversize issues
    panel = new Composite(rootPanel, SWT.NO_RADIO_GROUP);
    gridData = new GridData(GridData.VERTICAL_ALIGN_CENTER | GridData.FILL_HORIZONTAL);
    panel.setLayoutData(gridData);
    layout = new GridLayout();
    layout.numColumns = 4;
    panel.setLayout(layout);

    //Line:
    // ------------------------------

    Control label = Utils.createSkinnedLabelSeparator(panel, SWT.HORIZONTAL);
    gridData = new GridData(GridData.FILL_HORIZONTAL);
    gridData.horizontalSpan = 4;
    label.setLayoutData(gridData);

    //Line:
    // [] add Multi-tracker information [] webseed

    final Button btnMultiTracker = new Button(panel,SWT.CHECK);
    Messages.setLanguageText(btnMultiTracker, "wizard.multitracker");
    gridData = new GridData();
    gridData.horizontalSpan = 4;
    btnMultiTracker.setLayoutData(gridData);
    btnMultiTracker.addListener(SWT.Selection, new Listener() {

	    @Override
	    public void handleEvent(Event arg0) {
	      ((NewTorrentWizard) wizard).useMultiTracker = btnMultiTracker.getSelection();
	    }
    });
    btnMultiTracker.setSelection(((NewTorrentWizard) wizard).useMultiTracker);

    btnMultiTracker.setEnabled( tracker_type != NewTorrentWizard.TT_DECENTRAL);

    final Button btnWebSeed = new Button(panel,SWT.CHECK);
    Messages.setLanguageText(btnWebSeed, "wizard.webseed");
    gridData = new GridData();
    gridData.horizontalSpan = 4;
    btnWebSeed.setLayoutData(gridData);
    btnWebSeed.addListener(SWT.Selection, new Listener() {

	    @Override
	    public void handleEvent(Event arg0) {
	      ((NewTorrentWizard) wizard).useWebSeed = btnWebSeed.getSelection();
	    }
    });
    btnWebSeed.setSelection(((NewTorrentWizard) wizard).useWebSeed);

    //Line:
    // include hashes for other networks (

    final Button btnExtraHashes = new Button(panel,SWT.CHECK);
    Messages.setLanguageText(btnExtraHashes, "wizard.createtorrent.extrahashes");
    gridData = new GridData();
    gridData.horizontalSpan = 4;
    btnExtraHashes.setLayoutData(gridData);
    btnExtraHashes.addListener(SWT.Selection, new Listener() {

    	@Override
	    public void handleEvent(Event arg0) {
    		((NewTorrentWizard) wizard).setAddOtherHashes( btnExtraHashes.getSelection());
    	}
    });
    btnExtraHashes.setSelection(((NewTorrentWizard) wizard).getAddOtherHashes());

    // add another panel due to control oversize issues
    // the "hack" is staying until a more satisfactory solution can be found
    panel = new Composite(rootPanel, SWT.NONE);
    gridData = new GridData(GridData.VERTICAL_ALIGN_CENTER | GridData.FILL_HORIZONTAL);
    panel.setLayoutData(gridData);
    layout = new GridLayout();
    layout.numColumns = 6;
    panel.setLayout(layout);

    //Line:
    // ------------------------------

    Control label1 = Utils.createSkinnedLabelSeparator(panel, SWT.HORIZONTAL);
    gridData = new GridData(GridData.FILL_HORIZONTAL);
    gridData.horizontalSpan = 6;
    label1.setLayoutData(gridData);

    activateMode(NewTorrentWizard.MODE_BYO);

    btnSSL.addListener(SWT.Selection, new Listener() {
		  @Override
		  public void handleEvent(Event arg0) {
		  	String	url;

			if ( btnSSL.getSelection()){
				url = "https://" + UrlUtils.convertIPV6Host(localTrackerHost) + ":" + localTrackerPortSSL + "/announce";
			}else{
				url = "http://" + UrlUtils.convertIPV6Host(localTrackerHost) + ":" + localTrackerPort + "/announce";
			}

			localTrackerValue.setText(url);

			localTrackerUrl[0] = url;

			setTrackerUrl(url);

		  }
		});

    btnLocalTracker.addListener(SWT.Selection, new Listener() {
      @Override
      public void handleEvent(Event arg0) {
        wizard.setTrackerType( NewTorrentWizard.TT_LOCAL );
        setTrackerUrl(localTrackerUrl[0]);
        updateTrackerURL();
        btnExternalTracker.setSelection(false);
        btnLocalTracker.setSelection(true);
        btnDHTTracker.setSelection(false);
        tracker.setEnabled(false);
        btnSSL.setEnabled(SSLEnabled);
        if(labelLocalAnnounce != null) {labelLocalAnnounce.setEnabled(true);}
        localTrackerValue.setEnabled(true);
        labelExternalAnnounce.setEnabled(false);
        btnMultiTracker.setEnabled(true);
      }
    });

    btnExternalTracker.addListener(SWT.Selection, new Listener() {
      @Override
      public void handleEvent(Event arg0) {
        wizard.setTrackerType( NewTorrentWizard.TT_EXTERNAL );
        setTrackerUrl( NewTorrentWizard.TT_EXTERNAL_DEFAULT );
        updateTrackerURL();
        btnLocalTracker.setSelection(false);
        btnExternalTracker.setSelection(true);
        btnDHTTracker.setSelection(false);
        tracker.setEnabled(true);
        btnSSL.setEnabled(false);
        if(labelLocalAnnounce != null) {labelLocalAnnounce.setEnabled(false);}
        localTrackerValue.setEnabled(false);
        labelExternalAnnounce.setEnabled(true);
        btnMultiTracker.setEnabled(true);
      }
    });

    btnDHTTracker.addListener(SWT.Selection, new Listener() {
        @Override
        public void handleEvent(Event arg0) {
          wizard.setTrackerType( NewTorrentWizard.TT_DECENTRAL );
          setTrackerUrl( NewTorrentWizard.TT_DECENTRAL_DEFAULT );
          updateTrackerURL();
          btnLocalTracker.setSelection(false);
          btnExternalTracker.setSelection(false);
          btnDHTTracker.setSelection(true);
          tracker.setEnabled(false);
          btnSSL.setEnabled(false);
          if(labelLocalAnnounce != null) {labelLocalAnnounce.setEnabled(false);}
          localTrackerValue.setEnabled(false);
          labelExternalAnnounce.setEnabled(false);
          btnMultiTracker.setEnabled(false);
        }
      });


    //Line:
    //Comment: [               ]
    label = new Label(panel, SWT.NULL);
    Messages.setLanguageText(label, "wizard.comment");

    final Text comment = new Text(panel, SWT.BORDER);
    gridData = new GridData(GridData.FILL_HORIZONTAL);
    gridData.horizontalSpan = 5;
    comment.setLayoutData(gridData);
    comment.setText(((NewTorrentWizard) wizard).getComment());

    comment.addListener(SWT.Modify, new Listener() {
      @Override
      public void handleEvent(Event event) {
        ((NewTorrentWizard) wizard).setComment(comment.getText());
      }
    });
    
    //Line:
    //Source: [               ]
    label = new Label(panel, SWT.NULL);
    Messages.setLanguageText(label, "wizard.source");

    final Text source = new Text(panel, SWT.BORDER);
    gridData = new GridData(GridData.FILL_HORIZONTAL);
    gridData.horizontalSpan = 5;
    source.setLayoutData(gridData);
    source.setText(((NewTorrentWizard) wizard).getSource());

    source.addListener(SWT.Modify, new Listener() {
      @Override
      public void handleEvent(Event event) {
        ((NewTorrentWizard) wizard).setSource(source.getText());
      }
    });
    
  }

  /*
	 * (non-Javadoc)
	 *
	 * @see com.biglybt.ui.swt.maketorrent.IWizardPanel#getNextPanel()
	 */
  @Override
  public IWizardPanel<NewTorrentWizard> getNextPanel() {

    //OSX work-arround to Fix SWT BUG #43396 :
    //Combo doesn't fire Selection Event
    if(Constants.isOSX) {
      //In case we're not using the localTracker, refresh the
      //Tracker URL from the Combo text
      if( wizard.getTrackerType() == NewTorrentWizard.TT_EXTERNAL ){
        setTrackerUrl(tracker.getText());
      }
    }

    if( wizard.useMultiTracker){
      return new MultiTrackerPanel( wizard, this);
    }

    if(  wizard.useWebSeed ){
        return new WebSeedPanel( wizard, this);
    }

    return( wizard.getNextPanelForMode( this ));
  }

  /*
	 * (non-Javadoc)
	 *
	 * @see com.biglybt.ui.swt.maketorrent.IWizardPanel#isNextEnabled()
	 */
  @Override
  public boolean isNextEnabled() {
    return true;
  }

  void activateMode(int mode) {
    wizard.setCurrentInfo(MessageText.getString(mode==NewTorrentWizard.MODE_SINGLE_FILE? "wizard.maketorrent.singlefile.help" :(mode==NewTorrentWizard.MODE_DIRECTORY? "wizard.maketorrent.directory.help" :"wizard.newtorrent.byo.help")));
    ((NewTorrentWizard) wizard).create_mode = mode;
  }

  void updateTrackerURL() {
    tracker.setText( wizard.getTrackerURL());
  }

  void setTrackerUrl(String url) {
    wizard.setTrackerURL(url);
    String config = ((NewTorrentWizard) wizard).multiTrackerConfig;
    if(config.equals("")) {
	    List list = (List) ((NewTorrentWizard) wizard).trackers.get(0);
	    if(list.size() > 0)
	      list.remove(0);
	    list.add(url);
    }
  }
}
