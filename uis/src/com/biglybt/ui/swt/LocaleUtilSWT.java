/*
 * LocaleUtilSWT.java
 *
 * Created on 29. August 2003, 17:32
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

package com.biglybt.ui.swt;

import java.util.ArrayList;
import java.util.Arrays;

import com.biglybt.core.Core;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.MouseAdapter;
import org.eclipse.swt.events.MouseEvent;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.*;
import com.biglybt.core.config.COConfigurationManager;
import com.biglybt.core.internat.*;
import com.biglybt.core.util.AERunnable;
import com.biglybt.core.util.Debug;

/**
 *
 * @author  Tobias Minich
 */

public class
LocaleUtilSWT
	implements LocaleUtilListener
{
  protected static boolean 				rememberEncodingDecision = true;
  protected static LocaleUtilDecoder 	rememberedDecoder 		 = null;
  protected static Object				remembered_on_behalf_of;


  public
  LocaleUtilSWT(
  	Core core )
  {
  	LocaleTorrentUtil.addListener( this );
  }


  @Override
  public LocaleUtilDecoderCandidate
  selectDecoder(
  	LocaleUtil						locale_util,
	Object							decision_owner,
  	LocaleUtilDecoderCandidate[]	candidates )

  	throws LocaleUtilEncodingException
  {
  	if ( decision_owner != remembered_on_behalf_of ){

  		remembered_on_behalf_of		= decision_owner;
  		rememberedDecoder			= null;
  	}

    if( rememberEncodingDecision && rememberedDecoder != null) {

      for (int i = 0; i < candidates.length; i++) {

        if(candidates[i].getValue() != null && rememberedDecoder == candidates[i].getDecoder()) {
          return( candidates[i] );
        }
      }
    }

    LocaleUtilDecoderCandidate	default_candidate	= candidates[0];

    String defaultString = candidates[0].getValue();

    Arrays.sort(candidates);

    boolean always_prompt = COConfigurationManager.getBooleanParameter("File.Decoder.Prompt", false );

    if ( !always_prompt ){

    	int minlength = candidates[0].getValue().length();

	    // If the default string length == minlength assumes that
	    // the array encoding is from default charset

	    if (defaultString != null && defaultString.length() == minlength) {
	      return( null );
	    }

	    	// see if we can try and apply a default encoding

	    String	default_name = COConfigurationManager.getStringParameter( "File.Decoder.Default", "" );

	    if ( default_name.length() > 0 ){
			for (int i = 0; i < candidates.length; i++) {
			  if(candidates[i].getValue() != null && candidates[i].getDecoder().getName().equals( default_name )) {

				return( candidates[i] );
			  }
			}
		}
    }

    ArrayList choosableCandidates = new ArrayList();

    	// Always stick the default candidate in position 0 if valid

    if ( defaultString != null ){

    	choosableCandidates.add( default_candidate );
    }

    LocaleUtilDecoder[]	general_decoders = locale_util.getGeneralDecoders();

    	// 	add all general candidates with names not already in the list

    for (int j = 0; j < general_decoders.length; j++) {

      for (int i = 0; i < candidates.length; i++) {

      	if (candidates[i].getValue()==null || candidates[i].getDecoder()==null) continue;

	        if(		general_decoders[j] != null &&
	        		general_decoders[j].getName().equals(candidates[i].getDecoder().getName())){

	        	if (!choosableCandidates.contains(candidates[i])) {

	        		choosableCandidates.add(candidates[i]);

	        		break;
	        	}
	        }
      	}
    }

    	// add the remaining possible locales

   	for (int i = 0; i < candidates.length; i++){

   		if (candidates[i].getValue()==null || candidates[i].getDecoder()==null) continue;

   		if (!choosableCandidates.contains(candidates[i])){

   			choosableCandidates.add(candidates[i]);
   		}
   	}


    final LocaleUtilDecoderCandidate[] candidatesToChoose = (LocaleUtilDecoderCandidate[]) choosableCandidates.toArray(new LocaleUtilDecoderCandidate[choosableCandidates.size()]);
    final LocaleUtilDecoderCandidate[] selected_candidate = {null};

    // Run Synchronously, since we want the results
    Utils.execSWTThread(new AERunnable() {
      @Override
      public void runSupport() {
      	try{
        	showChoosableEncodingWindow(Utils.findAnyShell(),
        			candidatesToChoose,selected_candidate);

      	}catch( Throwable e ){

      		Debug.printStackTrace( e );

        }
      }
    }, false);

    if ( selected_candidate[0] == null ){

    	throw( new LocaleUtilEncodingException( true ));
    }else{

    	return ( selected_candidate[0] );
    }
  }

  private void
  showChoosableEncodingWindow(
  		final 				Shell shell,
		final 				LocaleUtilDecoderCandidate[] 	candidates,
		final 				LocaleUtilDecoderCandidate[]	selected_candidate )
  {
    final Shell s = com.biglybt.ui.swt.components.shell.ShellFactory
				.createShell(shell, SWT.RESIZE | SWT.DIALOG_TRIM | SWT.PRIMARY_MODAL);
    Utils.setShellIcon(s);
    s.setText(MessageText.getString("LocaleUtil.title")); //$NON-NLS-1$
    GridData gridData;
    s.setLayout(new GridLayout(1, true));

    Label label = new Label(s, SWT.LEFT);
    Messages.setLanguageText(label, "LocaleUtil.label.chooseencoding"); //$NON-NLS-1$

    final Table table = new Table(s, SWT.SINGLE | SWT.FULL_SELECTION | SWT.BORDER | SWT.V_SCROLL);
    gridData = new GridData( GridData.FILL_BOTH );
    Utils.setLayoutData(table, gridData);

    table.setLinesVisible(true);
    table.setHeaderVisible(true);

    String[] titlesPieces = { "LocaleUtil.column.encoding", "LocaleUtil.column.text" };
    for (int i = 0; i < titlesPieces.length; i++) {
      TableColumn column = new TableColumn(table, SWT.LEFT);
      Messages.setLanguageText(column, titlesPieces[i]);
    }

    // add candidates to table
    for (int i = 0; i < candidates.length; i++) {
      TableItem item = new TableItem(table, SWT.NULL);
      String name = candidates[i].getDecoder().getName();
      item.setText(0, name);
      item.setText(1, candidates[i].getValue());
    }
    int lastSelectedIndex = 0;
    for (int i = 1; i < candidates.length; i++) {
      if(candidates[i].getValue() != null && candidates[i].getDecoder() == rememberedDecoder ) {
        lastSelectedIndex = i;
        break;
      }
    }
    table.select(lastSelectedIndex);

    // resize all columns to fit the widest entry
    table.getColumn(0).pack();
    table.getColumn(1).pack();

    label = new Label(s, SWT.LEFT);
    Messages.setLanguageText(label, "LocaleUtil.label.hint.doubleclick"); //$NON-NLS-1$

    Composite composite = new Composite(s,SWT.NULL);
    gridData = new GridData(GridData.FILL_HORIZONTAL);
    Utils.setLayoutData(composite, gridData);

    GridLayout subLayout  = new GridLayout();
    subLayout.numColumns = 2;

    composite.setLayout(subLayout);

    final Button checkBox = new Button(composite, SWT.CHECK);
    Utils.setLayoutData(checkBox, new GridData(GridData.HORIZONTAL_ALIGN_BEGINNING));
    checkBox.setSelection(rememberEncodingDecision);
    Messages.setLanguageText(checkBox, "LocaleUtil.label.checkbox.rememberdecision"); //$NON-NLS-1$, "LocaleUtil.label.checkbox.rememberdecision"); //$NON-NLS-1$

    final Button ok = new Button(composite, SWT.PUSH);
    ok.setText(" ".concat(MessageText.getString("Button.next")).concat(" ")); //$NON-NLS-1$ //$NON-NLS-3$ //$NON-NLS-2$
    gridData = new GridData(GridData.END);
    gridData.widthHint = 100;
    Utils.setLayoutData(ok, gridData);



    s.setSize(500,500);
    s.layout();

    Utils.centreWindow(s);

    ok.addSelectionListener(new SelectionAdapter() {
      @Override
      public void widgetSelected(SelectionEvent event) {
      	//abandonSelection(s);
        setSelectedIndex(s, table, checkBox, candidates,selected_candidate);
        s.dispose();
      }
    });

    table.addMouseListener(new MouseAdapter() {
      @Override
      public void mouseDoubleClick(MouseEvent mEvent) {
        setSelectedIndex(s, table, checkBox, candidates,selected_candidate);
        s.dispose();
      }
    });

    s.open();
    while (!s.isDisposed()) {
      if (!s.getDisplay().readAndDispatch()) {
      	s.getDisplay().sleep();
      }
    }
  }

  private void
  setSelectedIndex(
  		final Shell 					s,
		final Table 					table,
		final Button 					checkBox,
		LocaleUtilDecoderCandidate[] 	candidates,
		LocaleUtilDecoderCandidate[] 	selected_candidate )
  {
    int selectedIndex = table.getSelectionIndex();

    if(-1 == selectedIndex)
      return;

    rememberEncodingDecision = checkBox.getSelection();

    selected_candidate[0]	= candidates[selectedIndex];

	if ( rememberEncodingDecision ){

		rememberedDecoder = selected_candidate[0].getDecoder();
	}else{
		rememberedDecoder = null;
	}

    s.dispose();
  }

  private void
  abandonSelection(
  	final Shell s)
  {
    s.dispose();
  }
}
