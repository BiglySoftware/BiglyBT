/*
 * Created on 27-Apr-2004
 * Created by Olivier Chalouhi
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


package com.biglybt.ui.swt.pifimpl;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.StyledText;
import org.eclipse.swt.events.*;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.*;

import com.biglybt.core.config.COConfigurationManager;
import com.biglybt.core.config.ParameterListener;
import com.biglybt.core.util.AERunnable;
import com.biglybt.pif.ui.components.UIButton;
import com.biglybt.pif.ui.components.UIComponent;
import com.biglybt.pif.ui.components.UIPropertyChangeEvent;
import com.biglybt.pif.ui.components.UIPropertyChangeListener;
import com.biglybt.pif.ui.model.BasicPluginViewModel;
import com.biglybt.ui.UIFunctions;
import com.biglybt.ui.UIFunctionsManager;
import com.biglybt.ui.mdi.MultipleDocumentInterface;
import com.biglybt.ui.swt.Messages;
import com.biglybt.ui.swt.Utils;
import com.biglybt.ui.swt.components.BufferedLabel;
import com.biglybt.ui.swt.mainwindow.ClipboardCopy;
import com.biglybt.ui.swt.mainwindow.Colors;
import com.biglybt.ui.swt.pif.UISWTView;
import com.biglybt.ui.swt.pif.UISWTViewEvent;


/**
 *
 */
public class
BasicPluginViewImpl
	implements UISWTViewCoreEventListener, UIPropertyChangeListener
{
	BasicPluginViewModel model;

	//GUI elements
	Display display;
	Composite panel;
	ProgressBar progress;
	BufferedLabel status;
	BufferedLabel task;
	StyledText log;
	Pattern inclusionFilter;
	Pattern exclusionFilter;
	boolean paused;

	boolean isCreated;

	@Override
	public boolean eventOccurred(UISWTViewEvent event) {
		switch (event.getType()) {
			case UISWTViewEvent.TYPE_CREATE: {
				if (isCreated)
					return false;
				isCreated = true;
				UISWTView swtView = event.getView();
				if (swtView != null) {
					model = (BasicPluginViewModel) swtView.getInitialDataSource();
					swtView.setTitle(model.getName());
				}
				break;
			}

			case UISWTViewEvent.TYPE_INITIALIZE: {
				initialize((Composite)event.getData());
				UISWTView swtView = event.getView();
				if (swtView != null) {
					swtView.setTitle(model.getName());
				}
				break;
			}

			case UISWTViewEvent.TYPE_REFRESH:
				refresh();
				break;

			case UISWTViewEvent.TYPE_DESTROY:
				delete();
				isCreated = false;
				break;

			case UISWTViewEvent.TYPE_FOCUSGAINED:
		   	String	text = model.getLogArea().getText().trim();

		   	if (log != null && !log.isDisposed()) {
  	    	log.setText( text);

  	    	log.setTopIndex(log.getLineCount());
		   	}
				break;

			case UISWTViewEvent.TYPE_FOCUSLOST:
		   	if (log != null && !log.isDisposed()) {
		   		log.setText("");
		   	}
				break;

		}
		return true;
	}

  private void initialize(Composite composite) {
    GridData gridData;
    GridLayout gridLayout;
    String sConfigSectionID = model.getConfigSectionID();

    this.display = composite.getDisplay();
    panel = new Composite(composite,SWT.NULL);
    gridLayout = new GridLayout();
    gridLayout.numColumns = 2;
    panel.setLayout(gridLayout);
	gridData = new GridData(GridData.FILL_BOTH);
		panel.setLayoutData(gridData);

    /*
     * Status       : [Status Text]
     * Current Task : [Task Text]
     * Progress     : [||||||||||----------]
     * Log :
     * [
     *
     *
     * ]
     */

	Composite topSection = new Composite(panel, SWT.NONE);
    gridLayout = new GridLayout();
    gridLayout.numColumns = 2;
    gridLayout.marginHeight = 0;
    gridLayout.marginWidth = 0;
    topSection.setLayout(gridLayout);
	gridData = new GridData(GridData.FILL_HORIZONTAL);
	if (sConfigSectionID == null){
		gridData.horizontalSpan = 2;
	}
		topSection.setLayoutData(gridData);

    if(model.getStatus().getVisible()) {
      Label statusTitle = new Label(topSection,SWT.NULL);
      Messages.setLanguageText(statusTitle,"plugins.basicview.status");

      status = new BufferedLabel(topSection,SWT.NULL);
      gridData = new GridData(GridData.FILL_HORIZONTAL);
			status.setLayoutData(gridData);
    }

    if(model.getActivity().getVisible()) {
      Label activityTitle = new Label(topSection,SWT.NULL);
      Messages.setLanguageText(activityTitle,"plugins.basicview.activity");

      task = new BufferedLabel(topSection,SWT.NULL);
      gridData = new GridData(GridData.FILL_HORIZONTAL);
			task.setLayoutData(gridData);
    }

    if(model.getProgress().getVisible()) {
      Label progressTitle = new Label(topSection,SWT.NULL);
      Messages.setLanguageText(progressTitle,"plugins.basicview.progress");

      progress = new ProgressBar(topSection,SWT.NULL);
      progress.setMaximum(100);
      progress.setMinimum(0);
      gridData = new GridData(GridData.FILL_HORIZONTAL);
			progress.setLayoutData(gridData);
    }

    List<Button>	all_buttons = new ArrayList<>();
    
    List<UIButton>	buttons = model.getButtons();
    
    if ( !buttons.isEmpty()){
    	for ( UIButton b: buttons ){
    		Label buttonTitle = new Label(topSection,SWT.NULL);
    		Messages.setLanguageText(buttonTitle, b.getLabel());
    		Button button = new Button( topSection, SWT.PUSH );
    		Messages.setLanguageText(button, b.getName());
    		
    		all_buttons.add( button );
    		
    		button.addSelectionListener(
    			new SelectionAdapter(){
    				@Override
    				public void widgetSelected(SelectionEvent e){
    					b.setProperty(UIComponent.PT_SELECTED, true );
    				}
				});
    	}
    }
    
    if (sConfigSectionID != null) {
    	Composite configSection = new Composite(panel, SWT.NONE);
        gridLayout = new GridLayout();
        gridLayout.numColumns = 1;
        gridLayout.marginHeight = 0;
        gridLayout.marginWidth = 2;
        configSection.setLayout(gridLayout);
        gridData = new GridData(GridData.END | GridData.VERTICAL_ALIGN_END );
			configSection.setLayoutData(gridData);
        //Label padding = new Label(configSection,SWT.NULL);
        //gridData = new GridData(GridData.FILL_HORIZONTAL);
        //Utils.setLayoutData(padding, gridData);
    	Button btnConfig = new Button(configSection, SWT.PUSH);
    	Messages.setLanguageText(btnConfig, "plugins.basicview.config");
    	btnConfig.addSelectionListener(new SelectionAdapter() {
    		@Override
		    public void widgetSelected(SelectionEvent e) {
       	 UIFunctions uiFunctions = UIFunctionsManager.getUIFunctions();
      	 if (uiFunctions != null) {
						uiFunctions.getMDI().showEntryByID(
								MultipleDocumentInterface.SIDEBAR_SECTION_CONFIG,
								model.getConfigSectionID());
	      	 }
    		}
    	});
			btnConfig.setLayoutData(new GridData());
    }

    if(model.getLogArea().getVisible()) {
    	Label logTitle = new Label(topSection,SWT.NULL);
    	Messages.setLanguageText(logTitle,"plugins.basicview.log");
    	//  gridData = new GridData(GridData.FILL_HORIZONTAL);
    	//  gridData.horizontalSpan = 1;
    	//  Utils.setLayoutData(logTitle, gridData);

    	Button button = new Button( topSection, SWT.PUSH );
    	Messages.setLanguageText(button,"plugins.basicview.clear");

    	all_buttons.add( button );
    	
    	button.addListener(SWT.Selection, new Listener() {
    		@Override
		    public void handleEvent(Event event)
    		{
    			model.getLogArea().setText("");
    		}});

    	log = new StyledText(panel,SWT.READ_ONLY | SWT.V_SCROLL | SWT.H_SCROLL | SWT.BORDER);
    	gridData = new GridData(GridData.FILL_BOTH);
    	gridData.horizontalSpan = 2;
			log.setLayoutData(gridData);

		ClipboardCopy.addCopyToClipMenu(
				log,
				new ClipboardCopy.copyToClipProvider()
				{
					@Override
					public String
					getText()
					{
						return( log.getText().trim());
					}
				});

	    log.addKeyListener(
				new KeyAdapter()
				{
					@Override
					public void
					keyPressed(
						KeyEvent event )
					{
						int key = event.character;

						if ( key <= 26 && key > 0 ){

							key += 'a' - 1;
						}

						if ( key == 'a' && event.stateMask == SWT.MOD1 ){

							event.doit = false;

							log.selectAll();
						}
					}
				});

    	//String	text = model.getLogArea().getText().trim();
    	//log.setText( text);
    	//log.setTopIndex(log.getLineCount());
    	model.getLogArea().addPropertyChangeListener(this);

    	Composite bottomSection = new Composite(panel, SWT.NONE);
        gridLayout = new GridLayout();
        gridLayout.numColumns = 3;
        gridLayout.marginHeight = 0;
        gridLayout.marginWidth = 0;
        bottomSection.setLayout(gridLayout);
    	gridData = new GridData(GridData.FILL_HORIZONTAL);
   		gridData.horizontalSpan = 2;
			bottomSection.setLayoutData(gridData);

			// include

    	Label label = new Label(bottomSection, SWT.NONE);
			label.setLayoutData(new GridData());
			Messages.setLanguageText(label, "LoggerView.includeOnly");

    	final Text inclText = new Text(bottomSection, SWT.BORDER);
    	gridData = new GridData();
    	gridData.widthHint = 200;
			inclText.setLayoutData(gridData);
    	inclText.addModifyListener(new ModifyListener()
    	{
    		@Override
		    public void modifyText(ModifyEvent e) {
    			String newExpression = inclText.getText();
    			if (newExpression.length() == 0)
    				inclusionFilter = null;
    			else
    			{
    				try
    				{
    					inclusionFilter = Pattern.compile(newExpression, Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE );
    					inclText.setBackground(null);
    				} catch (PatternSyntaxException e1)
    				{
    					inclText.setBackground(Colors.colorErrorBG);
    				}
    			}
    		}
    	});

    	label = new Label(bottomSection, SWT.NONE);

    		// exclude

    	label = new Label(bottomSection, SWT.NONE);
			label.setLayoutData(new GridData());
			Messages.setLanguageText(label, "LoggerView.excludeAll");

    	final Text exclText = new Text(bottomSection, SWT.BORDER);
    	gridData = new GridData();
    	gridData.widthHint = 200;
			exclText.setLayoutData(gridData);
    	exclText.addModifyListener(new ModifyListener()
    	{
    		@Override
		    public void modifyText(ModifyEvent e) {
    			String newExpression = exclText.getText();
    			if (newExpression.length() == 0)
    				exclusionFilter = null;
    			else
    			{
    				try
    				{
    					exclusionFilter = Pattern.compile(newExpression, Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    					exclText.setBackground(null);
    				} catch (PatternSyntaxException e1)
    				{
    					exclText.setBackground(Colors.colorErrorBG);
    				}
    			}
    		}
    	});

       	label = new Label(bottomSection, SWT.NONE);

    		// pause

		String config_key = model.getName() + ".LoggerView.pause";

		Button buttonPause = new Button(bottomSection, SWT.CHECK);
		Messages.setLanguageText(buttonPause, "LoggerView.pause");
		gridData = new GridData();
			buttonPause.setLayoutData(gridData);

		buttonPause.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				if (e.widget == null || !(e.widget instanceof Button))
					return;
				Button btn = (Button) e.widget;
				
				boolean sel = btn.getSelection();
				
				Object o = model.getProperty( BasicPluginViewModel.PR_EXTERNAL_LOG_PAUSE );
				
				boolean external_pause_control = false;
				
				if ( o instanceof Boolean ){
				
					external_pause_control = (Boolean)o;
				}
				
				if ( !external_pause_control){
					paused = sel;
				}
				
				COConfigurationManager.setParameter( config_key, sel );
			}
		});
		
		COConfigurationManager.addAndFireParameterListener(
			config_key,
			new ParameterListener(){
				
				@Override
				public void parameterChanged(String name){
					
					if ( buttonPause.isDisposed()){
						
						COConfigurationManager.removeParameterListener( name, this );
						
					}else{
						
						boolean	paused = COConfigurationManager.getBooleanParameter( name, false );
						
						buttonPause.setSelection( paused );
					}
				}
			});
    }
    
    if ( all_buttons.size() > 1 ){
    	
    	Utils.makeButtonsEqualWidth( all_buttons );
    }

  }

  private void refresh() {
    if(status != null) {
      status.setText(model.getStatus().getText());
    }
    if(task != null) {
      task.setText(model.getActivity().getText());
    }
    if(progress != null) {
      progress.setSelection(model.getProgress().getPercentageComplete());
    }
    if ( model != null ){
      model.getLogArea().refresh();
    }
  }

  @Override
  public void propertyChanged(final UIPropertyChangeEvent ev) {
    if(ev.getSource() != model.getLogArea())
      return;
    if(display == null || display.isDisposed() || log == null || paused)
      return;


    display.asyncExec(new AERunnable(){
      @Override
      public void runSupport() {
        if(log.isDisposed())
          return;
        if ( !log.isVisible()){
        	return;
        }
        String old_value = (String)ev.getOldPropertyValue();
        String new_value = (String) ev.getNewPropertyValue();

        ScrollBar bar = log.getVerticalBar();

        boolean max = bar.getSelection() == bar.getMaximum() - bar.getThumb();
        int lineOffset = log.getLineCount() - log.getTopIndex();

        if ( new_value.startsWith( old_value )){

        	String toAppend = new_value.substring(old_value.length());
        	if(toAppend.length() == 0)
        		return;

        	StringBuilder builder = new StringBuilder(toAppend.length());

        	String[] lines = toAppend.split("\n");


        	for( int i=0;i<lines.length;i++){
        		String line = lines[i];

        		if((inclusionFilter != null && !inclusionFilter.matcher(line).find()) || (exclusionFilter != null && exclusionFilter.matcher(line).find()))
					continue;
        		builder.append("\n");
        		builder.append(line);
        	}


        	log.append(builder.toString());

        }else{

        	StringBuilder builder = new StringBuilder(new_value.length());

        	String[] lines = new_value.split("\n");

        	for( int i=0;i<lines.length;i++){
        		String line = lines[i];
        		if((inclusionFilter != null && !inclusionFilter.matcher(line).find()) || (exclusionFilter != null && exclusionFilter.matcher(line).find()))
					continue;
        		if(line != lines[0]){
        			builder.append("\n");
        		}
        		builder.append(line);
        	}

        	log.setText(builder.toString());
        }

        if(max)
        {
        	bar.setSelection(bar.getMaximum()-bar.getThumb());
        	log.setTopIndex(log.getLineCount()-lineOffset);
        	log.redraw();
        }

      }
    });
  }

  private void
  delete()
  {
    model.getLogArea().removePropertyChangeListener( this );
  }
}
