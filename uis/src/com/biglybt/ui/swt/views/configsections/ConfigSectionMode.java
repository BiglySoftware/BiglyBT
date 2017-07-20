
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

import org.eclipse.swt.SWT;
import org.eclipse.swt.events.MouseAdapter;
import org.eclipse.swt.events.MouseEvent;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.layout.RowLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Group;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Listener;

import com.biglybt.pif.ui.config.ConfigSection;
import com.biglybt.ui.swt.mainwindow.ClipboardCopy;
import com.biglybt.ui.swt.mainwindow.Colors;
import com.biglybt.ui.swt.pif.UISWTConfigSection;
import com.biglybt.ui.swt.shells.MessageBoxShell;
import com.biglybt.ui.swt.Messages;
import com.biglybt.ui.swt.Utils;
import com.biglybt.core.config.COConfigurationManager;
import com.biglybt.core.internat.MessageText;
import com.biglybt.core.util.Constants;
import com.biglybt.ui.UserPrompterResultListener;
import com.biglybt.ui.common.RememberedDecisionsManager;



public class ConfigSectionMode implements UISWTConfigSection {
	  @Override
	  public String configSectionGetParentSection() {
		    return ConfigSection.SECTION_ROOT;
		  }

	@Override
	public String configSectionGetName() {
		return "mode";
	}

  @Override
  public void configSectionSave() {
  }

  @Override
  public void configSectionDelete() {
  }

	@Override
	public int maxUserMode() {
		return 0;
	}



  @Override
  public Composite configSectionCreate(final Composite parent) {
    GridData gridData;
    GridLayout layout;

    /*
    final String[] messTexts =
    	{	"ConfigView.section.mode.beginner.wiki.definitions",
    		"ConfigView.section.mode.intermediate.wiki.host",
    		"ConfigView.section.mode.advanced.wiki.main",
    };
    */

    final String[] links =
    	{	Constants.URL_WIKI + "w/Mode#Beginner",
    		Constants.URL_WIKI + "w/Mode#Intermediate",
    		Constants.URL_WIKI + "w/Mode#Advanced"
    };

    int userMode = COConfigurationManager.getIntParameter("User Mode");

    final Composite cMode = new Composite(parent, SWT.WRAP);
    gridData = new GridData(GridData.VERTICAL_ALIGN_FILL | GridData.HORIZONTAL_ALIGN_FILL);
    cMode.setLayoutData(gridData);
    layout = new GridLayout();
    layout.numColumns = 4;
    layout.marginHeight = 0;
    cMode.setLayout(layout);

    gridData = new GridData();
    gridData.horizontalSpan = 4;
    final Group gRadio = new Group(cMode, SWT.WRAP);
    Messages.setLanguageText(gRadio, "ConfigView.section.mode.title");
    gRadio.setLayoutData(gridData);
    Utils.setLayout(gRadio, new RowLayout(SWT.HORIZONTAL));

    Button button0 = new Button (gRadio, SWT.RADIO);
    Messages.setLanguageText(button0, "ConfigView.section.mode.beginner");
    button0.setData("iMode", "0");
    button0.setData("sMode", "beginner.text");

    Button button1 = new Button (gRadio, SWT.RADIO);
    Messages.setLanguageText(button1, "ConfigView.section.mode.intermediate");
    button1.setData("iMode", "1");
    button1.setData("sMode", "intermediate.text");

    Button button2 = new Button (gRadio, SWT.RADIO);
    Messages.setLanguageText(button2, "ConfigView.section.mode.advanced");
    button2.setData("iMode", "2");
    button2.setData("sMode", "advanced.text");

    final Button[] selected_button = { null };

    if ( userMode == 0) {
    	selected_button[0] = button0;
    	button0.setSelection(true);
    } else if ( userMode == 1) {
    	selected_button[0] = button1;
    	button1.setSelection(true);
    } else {
    	selected_button[0] = button2;
    	button2.setSelection(true);
    }

    gridData = new GridData(GridData.FILL_HORIZONTAL);
    final Label label = new Label(cMode, SWT.WRAP);
    gridData.horizontalSpan = 4;
    gridData.horizontalIndent=10;
    Utils.setLayoutData(label, gridData);


    final Label linkLabel = new Label(cMode, SWT.NULL);
    linkLabel.setText( MessageText.getString( "ConfigView.label.please.visit.here" ));
    linkLabel.setData( links[userMode] );
    linkLabel.setCursor(linkLabel.getDisplay().getSystemCursor(SWT.CURSOR_HAND));
    linkLabel.setForeground(Colors.blue);
    gridData = new GridData(GridData.FILL_HORIZONTAL);
    //gridData.horizontalIndent = 10;
    Utils.setLayoutData(linkLabel,  gridData );
    linkLabel.addMouseListener(new MouseAdapter() {
      @Override
      public void mouseDoubleClick(MouseEvent arg0) {
      	Utils.launch((String) ((Label) arg0.widget).getData());
      }
      @Override
      public void mouseUp(MouseEvent arg0) {
      	Utils.launch((String) ((Label) arg0.widget).getData());
      }
    });
    gridData = new GridData(GridData.FILL_HORIZONTAL);
    gridData.horizontalSpan = 4;
    Utils.setLayoutData(linkLabel, gridData);
    ClipboardCopy.addCopyToClipMenu( linkLabel );

    final Runnable setModeText =
    	new Runnable()
    	{
    		@Override
		    public void
    		run()
    		{
    			String key = "ConfigView.section.mode." + selected_button[0].getData("sMode");

    			if ( MessageText.keyExists( key + "1" )){
    				key = key + "1";
    			}

    			label.setText( "-> " +  MessageText.getString( key ) );
    		}
    	};


    setModeText.run();



	    /*
	    final Label linkLabel1 = new Label(gWiki, SWT.NULL);
	    linkLabel1.setText( (userMode == 1)?MessageText.getString(messTexts[3]):"");
	    linkLabel1.setData( links[3] );
	    linkLabel1.setCursor(linkLabel1.getDisplay().getSystemCursor(SWT.CURSOR_HAND));
	    linkLabel1.setForeground(Colors.blue);
	    gridData = new GridData(GridData.FILL_HORIZONTAL);
	    gridData.horizontalIndent = 10;
	    Utils.setLayoutData(linkLabel1,  gridData );
	    linkLabel1.addMouseListener(new MouseAdapter() {
	      public void mouseDoubleClick(MouseEvent arg0) {
	      	Utils.launch((String) ((Label) arg0.widget).getData());
	      }
	      public void mouseUp(MouseEvent arg0) {
	      	Utils.launch((String) ((Label) arg0.widget).getData());
	      }
	    });
		*/

    Listener radioGroup = new Listener () {
    	@Override
	    public void handleEvent (Event event) {

    		Control [] children = gRadio.getChildren ();

    		for (int j=0; j<children.length; j++) {
    			 Control child = children [j];
    			 if (child instanceof Button) {
    				 Button button = (Button) child;
    				 if ((button.getStyle () & SWT.RADIO) != 0) button.setSelection (false);
    			 }
    		}

		    Button button = (Button) event.widget;
		    button.setSelection (true);
		    int mode = Integer.parseInt((String)button.getData("iMode"));
		    selected_button[0] = button;
		    setModeText.run();

		    //linkLabel.setText( MessageText.getString(messTexts[mode]) );
		    linkLabel.setData( links[mode] );
		    /*
		    if(mode == 1){
			    linkLabel1.setText( MessageText.getString(messTexts[3]) );
			    linkLabel1.setData( links[3] );
		    } else{
			    linkLabel1.setText( "" );
			    linkLabel1.setData( "" );
		    }
		    */
		    COConfigurationManager.setParameter("User Mode", Integer.parseInt((String)button.getData("iMode")));
		    }
    };

    button0.addListener (SWT.Selection, radioGroup);
    button1.addListener (SWT.Selection, radioGroup);
    button2.addListener (SWT.Selection, radioGroup);

    Label padding = new Label(cMode, SWT.NULL );
    gridData = new GridData();
    gridData.horizontalSpan = 3;
    Utils.setLayoutData(padding,  gridData );

    	// reset to defaults

    Label blank = new Label(cMode, SWT.NULL);
    gridData = new GridData(GridData.FILL_HORIZONTAL);
    gridData.horizontalSpan = 4;
    Utils.setLayoutData(blank, gridData);


	Composite gReset = new Composite(cMode, SWT.WRAP);
    gridData = new GridData();
    gridData.horizontalSpan = 4;
    Utils.setLayoutData(gReset, gridData);
    layout = new GridLayout();
    layout.numColumns = 3;
    layout.marginWidth = 0;
    gReset.setLayout(layout);

    Label reset_label = new Label(gReset, SWT.NULL );
    Messages.setLanguageText(reset_label, "ConfigView.section.mode.resetdefaults" );

    Button reset_button = new Button(gReset, SWT.PUSH);

    Messages.setLanguageText(reset_button, "Button.reset" );

    reset_button.addListener(SWT.Selection,
		new Listener()
		{
	        @Override
	        public void
			handleEvent(Event event)
	        {
	        	MessageBoxShell mb = new MessageBoxShell(
	        			SWT.ICON_WARNING | SWT.OK | SWT.CANCEL,
	        			MessageText.getString("resetconfig.warn.title"),
	        			MessageText.getString("resetconfig.warn"));

	        	mb.setDefaultButtonUsingStyle(SWT.CANCEL);

	        	mb.setParent(parent.getShell());

	        	mb.open(
	        		new UserPrompterResultListener()
	        		{
	        			@Override
				        public void
	        			prompterClosed(
	        				int returnVal )
	        			{
							if (returnVal != SWT.OK) {
								return;
							}

							RememberedDecisionsManager.ensureLoaded();

							COConfigurationManager.resetToDefaults();
	        			}
	        		});
	        }
	    });

    padding = new Label(gReset, SWT.NULL );
    gridData = new GridData();
    Utils.setLayoutData(padding,  gridData );

    return cMode;
  }
}
